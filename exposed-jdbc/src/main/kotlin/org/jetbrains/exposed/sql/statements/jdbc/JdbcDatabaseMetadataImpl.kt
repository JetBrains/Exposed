package org.jetbrains.exposed.sql.statements.jdbc

import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.statements.api.ExposedDatabaseMetadata
import org.jetbrains.exposed.sql.statements.api.IdentifierManagerApi
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.vendors.*
import java.math.BigDecimal
import java.sql.DatabaseMetaData
import java.sql.ResultSet
import kotlin.collections.HashMap

class JdbcDatabaseMetadataImpl(database: String, val metadata: DatabaseMetaData) : ExposedDatabaseMetadata(database) {
    override val url: String by lazyMetadata { url }
    override val version: BigDecimal by lazyMetadata { BigDecimal("$databaseMajorVersion.$databaseMinorVersion")}

    override val databaseDialectName: String by lazyMetadata {
        when (driverName) {
            "MySQL Connector/J",
            "MySQL Connector Java" -> MysqlDialect.dialectName
            "MariaDB Connector/J" -> MariaDBDialect.dialectName
            "SQLite JDBC" -> SQLiteDialect.dialectName
            "H2 JDBC Driver" -> H2Dialect.dialectName
            "pgjdbc-ng" -> PostgreSQLNGDialect.dialectName
            "PostgreSQL JDBC Driver" -> PostgreSQLDialect.dialectName
            "Oracle JDBC driver" -> OracleDialect.dialectName
            else -> {
                if (driverName.startsWith("Microsoft JDBC Driver "))
                    SQLServerDialect.dialectName
                else
                    error("Unsupported driver $driverName detected")
            }
        }
    }

    private val databaseName get() = when(databaseDialectName) {
         MysqlDialect.dialectName, MariaDBDialect.dialectName -> currentScheme
         OracleDialect.dialectName -> null
         else -> database
    }

    private val oracleSchema = database.takeIf { metadata.databaseProductName == "Oracle" }

    override val databaseProductVersion by lazyMetadata { databaseProductVersion!! }

    override val defaultIsolationLevel: Int by lazyMetadata { defaultTransactionIsolation }

    override val supportsAlterTableWithAddColumn by lazyMetadata { supportsAlterTableWithAddColumn() }
    override val supportsMultipleResultSets by lazyMetadata { supportsMultipleResultSets() }
    override val supportsSelectForUpdate: Boolean by lazyMetadata { supportsSelectForUpdate() }

    override val identifierManager: IdentifierManagerApi by lazyMetadata { JdbcIdentifierManager(this) }

    private var _currentScheme: String? = null
        get() {
            if (field == null) {
                field = try {
                    when (databaseDialectName) {
                        MysqlDialect.dialectName, MariaDBDialect.dialectName -> metadata.connection.catalog.orEmpty()
                        else -> metadata.connection.schema.orEmpty()
                    }
                } catch (e: Throwable) { "" }
            }
            return field!!
        }

    override val currentScheme: String get() = _currentScheme!!

    override fun resetCurrentScheme() {
        _currentScheme = null
    }

    private inner class CachableMapWithDefault<K, V>(private val map:MutableMap<K,V> = mutableMapOf(), val default: (K) -> V) : Map<K,V> by map {
        override fun get(key: K): V? = map.getOrPut(key, { default(key) })
        override fun containsKey(key: K): Boolean = true
        override fun isEmpty(): Boolean = false
    }

    override val tableNames: Map<String, List<String>> get() = CachableMapWithDefault(default = { schemeName ->
        tableNamesFor(schemeName)
    })

    private fun tableNamesFor(scheme: String): List<String> = with(metadata) {
        val useCatalogInsteadOfScheme = currentDialect is MysqlDialect
        val (catalogName, schemeName) = when {
            useCatalogInsteadOfScheme -> scheme to ""
            else -> databaseName to scheme
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

    private fun ResultSet.extractColumns(tables: Array<out Table>, extract: (ResultSet) -> Pair<String, ColumnMetadata>): Map<Table, List<ColumnMetadata>> {
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
        val rs =  metadata.getColumns(databaseName, oracleSchema, "%", "%")
        val result = rs.extractColumns(tables) {
            //@see java.sql.DatabaseMetaData.getColumns
            val columnMetadata = ColumnMetadata(it.getString("COLUMN_NAME")/*.quoteIdentifierWhenWrongCaseOrNecessary(tr)*/, it.getInt("DATA_TYPE"), it.getBoolean("NULLABLE"), it.getInt("COLUMN_SIZE").takeIf { it != 0 })
            it.getString("TABLE_NAME") to columnMetadata
        }
        rs.close()
        return result
    }

    private val existingIndicesCache = HashMap<Table, List<Index>>()

    override fun existingIndices(vararg tables: Table): Map<Table, List<Index>> {
        for(table in tables) {
            val tableName = table.nameInDatabaseCase()
            val transaction = TransactionManager.current()

            existingIndicesCache.getOrPut(table) {
                val pkNames = metadata.getPrimaryKeys(databaseName, oracleSchema, tableName).let { rs ->
                    val names = arrayListOf<String>()
                    while(rs.next()) {
                        rs.getString("PK_NAME")?.let { names += it }
                    }
                    rs.close()
                    names
                }
                val rs = metadata.getIndexInfo(databaseName, oracleSchema, tableName, false, false)

                val tmpIndices = hashMapOf<Pair<String, Boolean>, MutableList<String>>()

                while (rs.next()) {
                    rs.getString("INDEX_NAME")?.let {
                        val column = transaction.db.identifierManager.quoteIdentifierWhenWrongCaseOrNecessary(rs.getString("COLUMN_NAME")!!)
                        val isUnique = !rs.getBoolean("NON_UNIQUE")
                        tmpIndices.getOrPut(it to isUnique) { arrayListOf() }.add(column)
                    }
                }
                rs.close()
                val tColumns = table.columns.associateBy { transaction.identity(it) }
                tmpIndices.filterNot { it.key.first in pkNames }
                    .mapNotNull { (index, columns) ->
                        columns.mapNotNull { cn -> tColumns[cn] }.takeIf { c -> c.size == columns.size }?.let { c -> Index(c, index.second, index.first) }
                    }
            }
        }
        return HashMap(existingIndicesCache)
    }

    @Synchronized
    override fun tableConstraints(tables: List<Table>): Map<String, List<ForeignKeyConstraint>> {
        val allTables = SchemaUtils.sortTablesByReferences(tables).associateBy { it.nameInDatabaseCase() }
        return allTables.keys.associateWith { table ->
            metadata.getImportedKeys(databaseName, oracleSchema, table).iterate {
                val fromTableName = getString("FKTABLE_NAME")!!
                val fromColumnName = identifierManager.quoteIdentifierWhenWrongCaseOrNecessary(getString("FKCOLUMN_NAME")!!)
                val fromColumn = allTables.getValue(fromTableName).columns.firstOrNull {
                    identifierManager.quoteIdentifierWhenWrongCaseOrNecessary(it.name) == fromColumnName
                } ?: return@iterate null // Do not crash if there are missing fields in Exposed's tables
                val constraintName = getString("FK_NAME")!!
                val targetTableName = getString("PKTABLE_NAME")!!
                val targetColumnName = identifierManager.quoteIdentifierWhenWrongCaseOrNecessary(getString("PKCOLUMN_NAME")!!)
                val targetColumn = allTables.getValue(targetTableName).columns.first {
                    identifierManager.quoteIdentifierWhenWrongCaseOrNecessary(it.nameInDatabaseCase()) == targetColumnName
                }
                val constraintUpdateRule = ReferenceOption.resolveRefOptionFromJdbc(getInt("UPDATE_RULE"))
                val constraintDeleteRule = ReferenceOption.resolveRefOptionFromJdbc(getInt("DELETE_RULE"))
                ForeignKeyConstraint(
                        target = targetColumn,
                        from = fromColumn,
                        onUpdate = constraintUpdateRule,
                        onDelete = constraintDeleteRule,
                        name = constraintName
                )
            }.filterNotNull()
        }
    }

    @Synchronized
    override fun cleanCache() {
        existingIndicesCache.clear()
    }

    private fun <T> lazyMetadata(body: DatabaseMetaData.() -> T) = lazy { metadata.body() }
}

fun <T> ResultSet.iterate(body: ResultSet.() -> T) : List<T> {
    val result = arrayListOf<T>()
    while(next()) {
        result.add(body())
    }
    close()
    return result
}
