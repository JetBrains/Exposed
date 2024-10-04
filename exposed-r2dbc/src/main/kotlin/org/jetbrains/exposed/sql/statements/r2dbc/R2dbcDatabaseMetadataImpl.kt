package org.jetbrains.exposed.sql.statements.r2dbc

import io.r2dbc.spi.Connection
import io.r2dbc.spi.Row
import io.r2dbc.spi.RowMetadata
import kotlinx.coroutines.reactive.awaitFirst
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.r2dbc.R2dbcDatabase
import org.jetbrains.exposed.sql.ForeignKeyConstraint
import org.jetbrains.exposed.sql.Index
import org.jetbrains.exposed.sql.Op
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.statements.api.ExposedDatabaseMetadata
import org.jetbrains.exposed.sql.statements.api.IdentifierManagerApi
import org.jetbrains.exposed.sql.stringLiteral
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.vendors.*
import java.math.BigDecimal
import java.util.concurrent.ConcurrentHashMap
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.set

// other than how metadata is retrieved, much of the business logic is identical to JDBC impl.
// could this be simplified?
@Suppress("UnusedParameter", "UnusedPrivateMember")
class R2dbcDatabaseMetadataImpl(
    database: String,
    val metadata: MetadataProvider,
    private val connection: Connection,
    private val scope: R2dbcScope
) : ExposedDatabaseMetadata(database) {
    private val connectionData = connection.metadata

    override val url: String by lazyMetadata { TransactionManager.current().db.url }

    override val version: BigDecimal by lazy {
        connectionData.databaseVersion
            .split('.', ' ')
            .let { BigDecimal("${it[0]}.${it[1]}") }
    }

    override val databaseDialectName: String by lazy {
        when (connectionData.databaseProductName) {
            "MySQL Community Server - GPL", "MySQL Community Server (GPL)" -> MysqlDialect.dialectName
            "MariaDB" -> MariaDBDialect.dialectName
            "H2" -> H2Dialect.dialectName
            "PostgreSQL" -> PostgreSQLDialect.dialectName
            "Oracle" -> OracleDialect.dialectName
            else -> {
                if (connectionData.databaseProductName.startsWith("Microsoft SQL Server ")) {
                    SQLServerDialect.dialectName
                } else {
                    R2dbcDatabase.getR2dbcDialectName(url)
                        ?: error("Unsupported driver ${connectionData.databaseProductName} detected")
                }
            }
        }
    }

    private val databaseName
        get() = when (databaseDialectName) {
            MysqlDialect.dialectName, MariaDBDialect.dialectName -> currentSchema!!
            else -> database
        }

    override val databaseProductVersion: String by lazy { connectionData.databaseVersion }

    override val defaultIsolationLevel: Int by lazyMetadata { defaultIsolationLevel }

    override val supportsAlterTableWithAddColumn: Boolean by lazyMetadata { propertyProvider.supportsAlterTableWithAddColumn }

    override val supportsAlterTableWithDropColumn: Boolean by lazyMetadata { propertyProvider.supportsAlterTableWithDropColumn }

    override val supportsMultipleResultSets: Boolean by lazyMetadata { propertyProvider.supportsMultipleResultSets }

    override val supportsSelectForUpdate: Boolean by lazyMetadata { propertyProvider.supportsSelectForUpdate }

    override val identifierManager: IdentifierManagerApi by lazyMetadata {
        // db URL as KEY causes issues with multi-tenancy!
        identityManagerCache.getOrPut(url) { R2dbcIdentifierManager(this, connectionData) }
    }

    private var currentSchema: String? = null
        get() {
            if (field == null) {
                field = try {
                    when (databaseDialectName) {
                        MysqlDialect.dialectName, MariaDBDialect.dialectName -> metadata.getCatalog().orEmpty()
                        OracleDialect.dialectName -> metadata.getSchema() ?: databaseName
                        else -> metadata.getSchema().orEmpty()
                    }
                } catch (_: Throwable) {
                    ""
                }
            }
            return field!!
        }

    override fun resetCurrentScheme() {
        currentSchema = null
    }

    override val tableNames: Map<String, List<String>>
        get() = CachableMapWithDefault(default = { schemeName ->
            tableNamesFor(schemeName)
        })

    private fun tableNamesFor(scheme: String): List<String> = with(metadata) {
        val useCatalogInsteadOfScheme = currentDialect is MysqlDialect
        val (catalogName, schemeName) = when {
            useCatalogInsteadOfScheme -> scheme to "%"
            currentDialect is OracleDialect -> databaseName to scheme.ifEmpty { databaseName }
            else -> databaseName to scheme.ifEmpty { "%" }
        }
        return fetchMetadata(
            sqlQuery = getTables(catalogName, schemeName, "%")
        ) { row, _ ->
            val tableName = row.getString("TABLE_NAME")!!
            val fullTableName = when {
                useCatalogInsteadOfScheme -> row.getString("TABLE_CAT")?.let { "$it.$tableName" }
                else -> row.getString("TABLE_SCHEM")?.let { "$it.$tableName" }
            } ?: tableName
            identifierManager.inProperCase(fullTableName)
        }
    }

    override val schemaNames: List<String>
        get() = schemaNames()

    private fun schemaNames(): List<String> = with(metadata) {
        val useCatalogInsteadOfScheme = currentDialect is MysqlDialect

        val schemas = when {
            useCatalogInsteadOfScheme -> fetchMetadata(getCatalogs()) { row, _ -> row.getString("TABLE_CAT")!! }
            else -> fetchMetadata(getSchemas()) { row, _ -> row.getString("TABLE_SCHEM")!! }
        }

        return schemas.map { identifierManager.inProperCase(it) }
    }

    override fun tableNamesByCurrentSchema(tableNamesCache: Map<String, List<String>>?): SchemaMetadata {
        val tablesInSchema = (tableNamesCache ?: tableNames).getValue(currentSchema!!)
        return SchemaMetadata(currentSchema!!, tablesInSchema)
    }

    override fun columns(vararg tables: Table): Map<Table, List<ColumnMetadata>> {
        val result = mutableMapOf<Table, List<ColumnMetadata>>()
        val useSchemaInsteadOfDatabase = currentDialect is MysqlDialect
        val tablesBySchema = tables.groupBy {
            identifierManager.inProperCase(it.schemaName ?: currentSchema!!)
        }

        for ((schema, schemaTables) in tablesBySchema.entries) {
            for (table in schemaTables) {
                val catalog = if (!useSchemaInsteadOfDatabase || schema == currentSchema!!) databaseName else schema
                val columns = fetchMetadata(
                    metadata.getColumns(catalog, schema, table.nameInDatabaseCaseUnquoted())
                ) { row, _ ->
                    // could this use RowMetadata instead of query?
                    val defaultDbValue = row.getString("COLUMN_DEF")?.let { sanitizedDefault(it) }
                    val autoIncrement = row.getString("IS_AUTOINCREMENT") == "YES"
                    val type = row.getInt("DATA_TYPE")
                    val name = row.getString("COLUMN_NAME")!!
                    val nullable = row.getBoolean("NULLABLE")
                    val size = row.getInt("COLUMN_SIZE").takeIf { it != 0 }
                    val scale = row.getInt("DECIMAL_DIGITS").takeIf { it != 0 }

                    ColumnMetadata(name, type, nullable, size, scale, autoIncrement, defaultDbValue?.takeIf { !autoIncrement })
                }
                check(columns.isNotEmpty())
                result[table] = columns
            }
        }

        return result
    }

    private val existingIndicesCache = HashMap<Table, List<Index>>()

    override fun existingIndices(vararg tables: Table): Map<Table, List<Index>> {
        for (table in tables) {
            val transaction = TransactionManager.current()
            val (catalog, tableSchema) = tableCatalogAndSchema(table)

            existingIndicesCache.getOrPut(table) {
                val pkNames = fetchMetadata(
                    metadata.getPrimaryKeys(catalog, tableSchema, table.nameInDatabaseCaseUnquoted())
                ) { row, _ ->
                    row.getString("PK_NAME")
                }.filterNotNull()

                val storedIndexTable = if (tableSchema == currentSchema!! && currentDialect is OracleDialect) {
                    table.nameInDatabaseCase()
                } else {
                    table.nameInDatabaseCaseUnquoted()
                }
                val tmpIndices = hashMapOf<Triple<String, Boolean, Op.TRUE?>, MutableList<String>>()
                fetchMetadata(
                    metadata.getIndexInfo(catalog, tableSchema, storedIndexTable)
                ) { row, _ ->
                    row.getString("INDEX_NAME")?.let { indexName ->
                        // if index is function-based, MySQL returns null column_name metadata
                        val columnNameMetadata = row.getString("COLUMN_NAME") ?: when (currentDialect) {
                            is MysqlDialect -> "\"\""
                            else -> null
                        }
                        columnNameMetadata?.let { columnName ->
                            val column = transaction.db.identifierManager.quoteIdentifierWhenWrongCaseOrNecessary(
                                columnName
                            )
                            val isUnique = !row.getBoolean("NON_UNIQUE")
                            val isPartial = if (row.getString("FILTER_CONDITION").isNullOrEmpty()) null else Op.TRUE
                            tmpIndices.getOrPut(Triple(indexName, isUnique, isPartial)) { arrayListOf() }.add(column)
                        }
                    }
                }

                val tColumns = table.columns.associateBy { transaction.identity(it) }
                tmpIndices.filterNot { it.key.first in pkNames }.mapNotNull { (index, columns) ->
                    val (functionBased, columnBased) = columns.distinct().partition { cn -> tColumns[cn] == null }
                    columnBased.map { cn -> tColumns[cn]!! }.takeIf { c -> c.size + functionBased.size == columns.size }?.let { c ->
                        Index(
                            c,
                            index.second,
                            index.first,
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
            val (catalog, tableSchema) = tableCatalogAndSchema(table)
            var pkName = ""
            val columnNames = fetchMetadata(
                metadata.getPrimaryKeys(catalog, tableSchema, table.nameInDatabaseCaseUnquoted())
            ) { row, _ ->
                row.getString("PK_NAME")?.let { pkName = it }
                row.getString("COLUMN_NAME")!!
            }
            if (pkName.isEmpty()) null else PrimaryKeyMetadata(pkName, columnNames)
        }
    }

    override fun sequences(): List<String> = fetchMetadata(
        metadata.getSequences()
    ) { row, _ ->
        row.getString("SEQUENCE_NAME")!!
    }

    override fun tableConstraints(tables: List<Table>): Map<String, List<ForeignKeyConstraint>> {
        val allTables = SchemaUtils.sortTablesByReferences(tables).associateBy { it.nameInDatabaseCaseUnquoted() }
        return allTables.keys.associateWith { table ->
            val (catalog, tableSchema) = tableCatalogAndSchema(allTables[table]!!)
            fetchMetadata(
                metadata.getImportedKeys(catalog, identifierManager.inProperCase(tableSchema), table)
            ) { row, _ ->
                val fromTableName = row.getString("FKTABLE_NAME")!!
                val fromColumnName = identifierManager.quoteIdentifierWhenWrongCaseOrNecessary(
                    row.getString("FKCOLUMN_NAME")!!
                )
                val fromColumn = allTables[fromTableName]?.columns?.firstOrNull {
                    identifierManager.quoteIdentifierWhenWrongCaseOrNecessary(it.name) == fromColumnName
                } ?: return@fetchMetadata null // Do not crash if there are missing fields in Exposed's tables
                val constraintName = row.getString("FK_NAME")!!
                val targetTableName = row.getString("PKTABLE_NAME")!!
                val targetColumnName = identifierManager.quoteIdentifierWhenWrongCaseOrNecessary(
                    identifierManager.inProperCase(row.getString("PKCOLUMN_NAME")!!)
                )
                val targetColumn = allTables[targetTableName]?.columns?.firstOrNull {
                    identifierManager.quoteIdentifierWhenWrongCaseOrNecessary(it.nameInDatabaseCase()) == targetColumnName
                } ?: return@fetchMetadata null // Do not crash if there are missing fields in Exposed's tables
                val constraintUpdateRule = row.get("UPDATE_RULE")?.toString()?.toIntOrNull()?.let {
                    currentDialect.resolveRefOptionFromJdbc(it)
                }
                val constraintDeleteRule = currentDialect.resolveRefOptionFromJdbc(row.getInt("DELETE_RULE"))
                ForeignKeyConstraint(
                    target = targetColumn,
                    from = fromColumn,
                    onUpdate = constraintUpdateRule,
                    onDelete = constraintDeleteRule,
                    name = constraintName
                )
            }.filterNotNull().groupBy { it.fkName }.values.map { it.reduce(ForeignKeyConstraint::plus) }
        }
    }

    private fun tableCatalogAndSchema(table: Table): Pair<String, String> {
        val tableSchema = identifierManager.inProperCase(table.schemaName ?: currentSchema!!)
        return if (currentDialect is MysqlDialect && tableSchema != currentSchema!!) {
            tableSchema to tableSchema
        } else {
            databaseName to tableSchema
        }
    }

    override fun cleanCache() {
        existingIndicesCache.clear()
    }

    private fun <T> lazyMetadata(body: MetadataProvider.() -> T) = lazy { metadata.body() }

    companion object {
        private val identityManagerCache = ConcurrentHashMap<String, R2dbcIdentifierManager>()
    }

    private fun <T> fetchMetadata(
        sqlQuery: String,
        body: (Row, RowMetadata) -> T
    ): List<T> = runBlocking {
        withContext(scope.coroutineContext) {
            val result = mutableListOf<T>()
            connection
                .createStatement(sqlQuery)
                .execute()
                .awaitFirst()
                .map { row, metadata ->
                    result.add(body(row, metadata))
                }
            result
        }
    }

    private fun Row.getString(name: String): String? = get(name, java.lang.String::class.java)?.toString()

    private fun Row.getInt(name: String): Int = get(name, java.lang.Number::class.java)?.intValue() ?: 0

    private fun Row.getBoolean(name: String): Boolean = get(name, java.lang.Boolean::class.java)?.booleanValue() ?: false
}
