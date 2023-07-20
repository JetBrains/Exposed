package org.jetbrains.exposed.sql.statements.jdbc

import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.statements.api.ExposedDatabaseMetadata
import org.jetbrains.exposed.sql.statements.api.IdentifierManagerApi
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.vendors.*
import org.jetbrains.exposed.sql.vendors.H2Dialect.H2CompatibilityMode
import java.math.BigDecimal
import java.sql.DatabaseMetaData
import java.sql.ResultSet
import java.util.concurrent.ConcurrentHashMap

class JdbcDatabaseMetadataImpl(database: String, val metadata: DatabaseMetaData) : ExposedDatabaseMetadata(database) {
    override val url: String by lazyMetadata { url }
    override val version: BigDecimal by lazyMetadata { BigDecimal("$databaseMajorVersion.$databaseMinorVersion") }

    override val databaseDialectName: String by lazyMetadata {
        when (driverName) {
            "MySQL-AB JDBC Driver",
            "MySQL Connector/J",
            "MySQL Connector Java" -> MysqlDialect.dialectName

            "MariaDB Connector/J" -> MariaDBDialect.dialectName
            "SQLite JDBC" -> SQLiteDialect.dialectName
            "H2 JDBC Driver" -> H2Dialect.dialectName
            "pgjdbc-ng" -> PostgreSQLNGDialect.dialectName
            "PostgreSQL JDBC - NG" -> PostgreSQLNGDialect.dialectName
            "PostgreSQL JDBC Driver" -> PostgreSQLDialect.dialectName
            "Oracle JDBC driver" -> OracleDialect.dialectName
            else -> {
                if (driverName.startsWith("Microsoft JDBC Driver ")) {
                    SQLServerDialect.dialectName
                } else {
                    Database.getDialectName(url) ?: error("Unsupported driver $driverName detected")
                }
            }
        }
    }

    private val databaseName
        get() = when (databaseDialectName) {
            MysqlDialect.dialectName, MariaDBDialect.dialectName -> currentScheme
            else -> database
        }

    override val databaseProductVersion by lazyMetadata { databaseProductVersion!! }

    override val defaultIsolationLevel: Int by lazyMetadata { defaultTransactionIsolation }

    override val supportsAlterTableWithAddColumn by lazyMetadata { supportsAlterTableWithAddColumn() }
    override val supportsMultipleResultSets by lazyMetadata { supportsMultipleResultSets() }
    override val supportsSelectForUpdate: Boolean by lazyMetadata { supportsSelectForUpdate() }

    override val identifierManager: IdentifierManagerApi by lazyMetadata {
        identityManagerCache.getOrPut(url) {
            JdbcIdentifierManager(this)
        }
    }

    private var _currentScheme: String? = null
        get() {
            if (field == null) {
                field = try {
                    when (databaseDialectName) {
                        MysqlDialect.dialectName, MariaDBDialect.dialectName -> metadata.connection.catalog.orEmpty()
                        OracleDialect.dialectName -> databaseName
                        else -> metadata.connection.schema.orEmpty()
                    }
                } catch (_: Throwable) {
                    ""
                }
            }
            return field!!
        }

    override val currentScheme: String get() = _currentScheme!!

    override fun resetCurrentScheme() {
        _currentScheme = null
    }

    private inner class CachableMapWithDefault<K, V>(
        private val map: MutableMap<K, V> = mutableMapOf(),
        val default: (K) -> V
    ) : Map<K, V> by map {
        override fun get(key: K): V? = map.getOrPut(key) { default(key) }
        override fun containsKey(key: K): Boolean = true
        override fun isEmpty(): Boolean = false
    }

    override val tableNames: Map<String, List<String>>
        get() = CachableMapWithDefault(
            default = { schemeName ->
                tableNamesFor(schemeName)
            }
        )

    private fun tableNamesFor(scheme: String): List<String> = with(metadata) {
        val useCatalogInsteadOfScheme = currentDialect is MysqlDialect
        val (catalogName, schemeName) = when {
            useCatalogInsteadOfScheme -> scheme to "%"
            currentDialect is OracleDialect -> databaseName to scheme.ifEmpty { databaseName }
            else -> databaseName to scheme.ifEmpty { "%" }
        }
        val resultSet = getTables(catalogName, schemeName, "%", arrayOf("TABLE"))
        return resultSet.iterate {
            val tableName = getString("TABLE_NAME")!!
            val fullTableName = when {
                useCatalogInsteadOfScheme -> getString("TABLE_CAT")?.let { "$it.$tableName" }
                else -> getString("TABLE_SCHEM")?.let { "$it.$tableName" }
            } ?: tableName
            identifierManager.inProperCase(fullTableName)
        }
    }

    /** Returns a list of existing schema names. */
    override val schemaNames: List<String> get() = schemaNames()

    /** Returns a list of existing schema names. */
    private fun schemaNames(): List<String> = with(metadata) {
        val useCatalogInsteadOfScheme = currentDialect is MysqlDialect

        val schemas = when {
            useCatalogInsteadOfScheme -> catalogs.iterate { getString("TABLE_CAT") }
            else -> schemas.iterate { getString("TABLE_SCHEM") }
        }

        return schemas.map { identifierManager.inProperCase(it) }
    }

    private fun ResultSet.extractColumns(
        tables: Array<out Table>,
        extract: (ResultSet) -> Pair<String, ColumnMetadata>
    ): Map<Table, List<ColumnMetadata>> {
        val mapping = tables.associateBy { it.nameInDatabaseCase() }
        val result = HashMap<Table, MutableList<ColumnMetadata>>()

        while (next()) {
            val (tableName, columnMetadata) = extract(this)
            mapping[tableName]?.let { t ->
                result.getOrPut(t) { arrayListOf() } += columnMetadata
            }
        }
        return result
    }

    override fun columns(vararg tables: Table): Map<Table, List<ColumnMetadata>> {
        val rs = metadata.getColumns(databaseName, currentScheme, "%", "%")
        val result = rs.extractColumns(tables) {
            // @see java.sql.DatabaseMetaData.getColumns
            // That read should go first as Oracle driver closes connection after that
            val defaultDbValue = it.getString("COLUMN_DEF")?.let { sanitizedDefault(it) }
            val autoIncrement = it.getString("IS_AUTOINCREMENT") == "YES"
            val type = it.getInt("DATA_TYPE")
            val columnMetadata = ColumnMetadata(
                it.getString("COLUMN_NAME"),
                type,
                it.getBoolean("NULLABLE"),
                it.getInt("COLUMN_SIZE").takeIf { it != 0 },
                autoIncrement,
                // Not sure this filters enough but I dont think we ever want to have sequences here
                defaultDbValue?.takeIf { !autoIncrement },
            )
            it.getString("TABLE_NAME") to columnMetadata
        }
        rs.close()
        return result
    }

    private fun sanitizedDefault(defaultValue: String): String {
        val dialect = currentDialect
        val h2Mode = dialect.h2Mode
        return when {
            dialect is SQLServerDialect -> defaultValue.trim('(', ')', '\'')
            dialect is OracleDialect || h2Mode == H2CompatibilityMode.Oracle -> defaultValue.trim().trim('\'')
            dialect is MysqlDialect || h2Mode == H2CompatibilityMode.MySQL || h2Mode == H2CompatibilityMode.MariaDB ->
                defaultValue.substringAfter("b'").trim('\'')

            dialect is PostgreSQLDialect || h2Mode == H2CompatibilityMode.PostgreSQL -> when {
                defaultValue.startsWith('\'') && defaultValue.endsWith('\'') -> defaultValue.trim('\'')
                else -> defaultValue
            }

            else -> defaultValue.trim('\'')
        }
    }

    private val existingIndicesCache = HashMap<Table, List<Index>>()

    override fun existingIndices(vararg tables: Table): Map<Table, List<Index>> {
        for (table in tables) {
            val tableName = table.nameInDatabaseCase()
            val transaction = TransactionManager.current()

            existingIndicesCache.getOrPut(table) {
                val pkNames = metadata.getPrimaryKeys(databaseName, currentScheme, tableName).let { rs ->
                    val names = arrayListOf<String>()
                    while (rs.next()) {
                        rs.getString("PK_NAME")?.let { names += it }
                    }
                    rs.close()
                    names
                }
                val rs = metadata.getIndexInfo(databaseName, currentScheme, tableName, false, false)

                val tmpIndices = hashMapOf<Triple<String, Boolean, Op.TRUE?>, MutableList<String>>()

                while (rs.next()) {
                    rs.getString("INDEX_NAME")?.let { indexName ->
                        // if index is function-based, SQLite & MySQL return null column_name metadata
                        val columnNameMetadata = rs.getString("COLUMN_NAME") ?: when (currentDialect) {
                            is MysqlDialect, is SQLiteDialect -> "\"\""
                            else -> null
                        }
                        columnNameMetadata?.let { columnName ->
                            val column = transaction.db.identifierManager.quoteIdentifierWhenWrongCaseOrNecessary(columnName)
                            val isUnique = !rs.getBoolean("NON_UNIQUE")
                            val isPartial = if (rs.getString("FILTER_CONDITION").isNullOrEmpty()) null else Op.TRUE
                            tmpIndices.getOrPut(Triple(indexName, isUnique, isPartial)) { arrayListOf() }.add(column)
                        }
                    }
                }
                rs.close()
                val tColumns = table.columns.associateBy { transaction.identity(it) }
                tmpIndices.filterNot { it.key.first in pkNames }
                    .mapNotNull { (index, columns) ->
                        val (functionBased, columnBased) = columns.distinct().partition { cn -> tColumns[cn] == null }
                        columnBased.map { cn -> tColumns[cn]!! }
                            .takeIf { c -> c.size + functionBased.size == columns.size }
                            ?.let { c ->
                                Index(
                                    c, index.second, index.first,
                                    filterCondition = index.third,
                                    functions = functionBased.map { stringLiteral(it) }.ifEmpty { null },
                                    functionsTable = if (functionBased.isNotEmpty()) table else null
                                )
                            }
                    }
            }
        }
        return HashMap(existingIndicesCache)
    }

    override fun existingPrimaryKeys(vararg tables: Table): Map<Table, PrimaryKeyMetadata?> {
        return tables.associateWith { table ->
            metadata.getPrimaryKeys(databaseName, currentScheme, table.nameInDatabaseCase()).let { rs ->
                val columnNames = mutableListOf<String>()
                var pkName = ""
                while (rs.next()) {
                    rs.getString("PK_NAME")?.let { pkName = it }
                    columnNames += rs.getString("COLUMN_NAME")
                }
                rs.close()
                if (pkName.isEmpty()) null else PrimaryKeyMetadata(pkName, columnNames)
            }
        }
    }

    @Synchronized
    override fun tableConstraints(tables: List<Table>): Map<String, List<ForeignKeyConstraint>> {
        val allTables = SchemaUtils.sortTablesByReferences(tables).associateBy { it.nameInDatabaseCase() }
        return allTables.keys.associateWith { table ->
            metadata.getImportedKeys(databaseName, currentScheme, table).iterate {
                val fromTableName = getString("FKTABLE_NAME")!!
                val fromColumnName = identifierManager.quoteIdentifierWhenWrongCaseOrNecessary(getString("FKCOLUMN_NAME")!!)
                val fromColumn = allTables[fromTableName]?.columns?.firstOrNull {
                    identifierManager.quoteIdentifierWhenWrongCaseOrNecessary(it.name) == fromColumnName
                } ?: return@iterate null // Do not crash if there are missing fields in Exposed's tables
                val constraintName = getString("FK_NAME")!!
                val targetTableName = getString("PKTABLE_NAME")!!
                val targetColumnName = identifierManager.quoteIdentifierWhenWrongCaseOrNecessary(
                    identifierManager.inProperCase(getString("PKCOLUMN_NAME")!!)
                )
                val targetColumn = allTables[targetTableName]?.columns?.firstOrNull {
                    identifierManager.quoteIdentifierWhenWrongCaseOrNecessary(it.nameInDatabaseCase()) == targetColumnName
                } ?: return@iterate null // Do not crash if there are missing fields in Exposed's tables
                val constraintUpdateRule = ReferenceOption.resolveRefOptionFromJdbc(getInt("UPDATE_RULE"))
                val constraintDeleteRule = ReferenceOption.resolveRefOptionFromJdbc(getInt("DELETE_RULE"))
                ForeignKeyConstraint(
                    target = targetColumn,
                    from = fromColumn,
                    onUpdate = constraintUpdateRule,
                    onDelete = constraintDeleteRule,
                    name = constraintName
                )
            }
                .filterNotNull()
                .groupBy { it.fkName }
                .values
                .map { it.reduce(ForeignKeyConstraint::plus) }
        }
    }

    @Synchronized
    override fun cleanCache() {
        existingIndicesCache.clear()
    }

    private fun <T> lazyMetadata(body: DatabaseMetaData.() -> T) = lazy { metadata.body() }

    companion object {
        private val identityManagerCache = ConcurrentHashMap<String, JdbcIdentifierManager>()
    }
}

private fun <T> ResultSet.iterate(body: ResultSet.() -> T): List<T> {
    val result = arrayListOf<T>()
    while (next()) {
        result.add(body())
    }
    close()
    return result
}
