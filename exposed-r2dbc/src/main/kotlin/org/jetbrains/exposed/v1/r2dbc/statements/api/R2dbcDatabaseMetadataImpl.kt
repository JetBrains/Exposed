package org.jetbrains.exposed.v1.r2dbc.statements.api

import io.r2dbc.spi.Connection
import io.r2dbc.spi.ConnectionMetadata
import io.r2dbc.spi.IsolationLevel
import io.r2dbc.spi.Row
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.reactive.collect
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.core.statements.api.ExposedMetadataUtils
import org.jetbrains.exposed.v1.core.statements.api.IdentifierManagerApi
import org.jetbrains.exposed.v1.core.utils.CachableMapWithSuspendableDefault
import org.jetbrains.exposed.v1.core.utils.CacheWithSuspendableDefault
import org.jetbrains.exposed.v1.core.vendors.*
import org.jetbrains.exposed.v1.r2dbc.SchemaUtils
import org.jetbrains.exposed.v1.r2dbc.mappers.R2dbcRegistryTypeMappingImpl
import org.jetbrains.exposed.v1.r2dbc.statements.executeSQL
import org.jetbrains.exposed.v1.r2dbc.statements.getCurrentCatalog
import org.jetbrains.exposed.v1.r2dbc.statements.getCurrentSchema
import org.jetbrains.exposed.v1.r2dbc.transactions.TransactionManager
import org.jetbrains.exposed.v1.r2dbc.vendors.metadata.MetadataProvider
import java.util.concurrent.ConcurrentHashMap

/**
 * Class responsible for retrieving and storing information about the R2DBC driver and underlying database.
 */
@Suppress("UnusedPrivateMember", "UnusedParameter")
class R2dbcDatabaseMetadataImpl(
    database: String,
    private val connection: Connection,
    private val vendorDialect: String,
) : R2dbcLocalMetadataImpl(database, vendorDialect) {
    private val connectionData: ConnectionMetadata = connection.metadata
    private val metadataProvider: MetadataProvider = MetadataProvider.getProvider(vendorDialect)

    override fun getVersion() = Version.from(connectionData.databaseVersion)

    override fun getMajorVersion(): Int = connectionData.databaseVersion.split('.', ' ')[0].toInt()

    override fun getMinorVersion(): Int = connectionData.databaseVersion.split('.', ' ')[1].toInt()

    override fun getDatabaseDialectName(): String {
        return when (val dbProductName = connectionData.databaseProductName) {
            "MySQL Community Server - GPL", "MySQL Community Server (GPL)" -> MysqlDialect.dialectName
            "MariaDB" -> MariaDBDialect.dialectName
            "H2" -> H2Dialect.dialectName
            "PostgreSQL" -> PostgreSQLDialect.dialectName
            "Oracle" -> OracleDialect.dialectName
            else -> {
                if (
                    dbProductName.startsWith("Microsoft Azure SQL ") ||
                    dbProductName.startsWith("Microsoft SQL Server ")
                ) {
                    SQLServerDialect.dialectName
                } else {
                    vendorDialect
                }
            }
        }
    }

    private suspend fun getDatabaseName(): String = when (getDatabaseDialectName()) {
        MysqlDialect.dialectName, MariaDBDialect.dialectName -> getCurrentSchema()
        else -> database
    }

    override suspend fun getDatabaseDialectMode(): String? {
        return connection.executeSQL(metadataProvider.getDatabaseMode()) { row, _ ->
            row.getString("DB_MODE")
        }?.firstOrNull()
    }

    override fun getDatabaseProductVersion(): String = connectionData.databaseVersion

    override fun getDefaultIsolationLevel(): IsolationLevel = connection.transactionIsolationLevel

    override val identifierManager: IdentifierManagerApi by lazy {
        // db URL as KEY causes issues with multi-tenancy!
        // 'database' string may certainly be less complex a key than JDBC 'connection.url' value.
        // To use an identical url string, we would need to save/parse it to/from ConnectionFactoryOptions & pass to this class.
        // This is what is done for R2dbcDatabase.url, for example.
        // So far this is the only use for us storing ConnectionFactoryOptions details in this class, but perhaps if other cases arise?
        identityManagerCache.getOrPut(database) { R2dbcIdentifierManager(metadataProvider, connectionData) }
    }

    private var currentSchema: String? = null

    private suspend fun getCurrentSchema(): String {
        if (currentSchema == null) {
            currentSchema = try {
                when (getDatabaseDialectName()) {
                    MysqlDialect.dialectName, MariaDBDialect.dialectName -> {
                        connection.getCurrentCatalog(metadataProvider).orEmpty()
                    }
                    OracleDialect.dialectName -> connection.getCurrentSchema(metadataProvider) ?: getDatabaseName()
                    else -> connection.getCurrentSchema(metadataProvider).orEmpty()
                }
            } catch (_: Throwable) { // REVIEW if this is necessary anymore
                ""
            }
        }
        return currentSchema ?: error("A non-null value could not be found for the current database schema.")
    }

    override fun resetCurrentScheme() {
        currentSchema = null
    }

    override suspend fun tableNames(): CacheWithSuspendableDefault<String, List<String>> {
        @OptIn(InternalApi::class)
        return CachableMapWithSuspendableDefault( // should this internal cache model be refactored?
            default = { schemaName ->
                tableNamesFor(schemaName)
            }
        )
    }

    private suspend fun tableNamesFor(schema: String): List<String> {
        val useCatalogInsteadOfScheme = currentDialect is MysqlDialect
        val (catalogName, schemaName) = when {
            useCatalogInsteadOfScheme -> schema to "%"
            currentDialect is OracleDialect -> getDatabaseName() to schema.ifEmpty { getDatabaseName() }
            else -> getDatabaseName() to schema.ifEmpty { "%" }
        }
        return connection.executeSQL(metadataProvider.getTables(catalogName, schemaName)) { row, _ ->
            val tableName = row.getString("TABLE_NAME")!!
            val fullTableName = when {
                useCatalogInsteadOfScheme -> row.getString("TABLE_CAT")?.let { "$it.$tableName" }
                else -> row.getString("TABLE_SCHEM")?.let { "$it.$tableName" }
            } ?: tableName
            identifierManager.inProperCase(fullTableName)
        }.orEmpty()
    }

    override suspend fun schemaNames(): List<String> {
        val useCatalogInsteadOfScheme = currentDialect is MysqlDialect

        val schemas = when {
            useCatalogInsteadOfScheme -> {
                connection.executeSQL(metadataProvider.getCatalogs()) { row, _ ->
                    row.getString("TABLE_CAT")!!
                }
            }
            else -> {
                connection.executeSQL(metadataProvider.getSchemas()) { row, _ ->
                    row.getString("TABLE_SCHEM")!!
                }
            }
        }.orEmpty()

        return schemas.map { identifierManager.inProperCase(it) }
    }

    override suspend fun tableNamesByCurrentSchema(tableNamesCache: CacheWithSuspendableDefault<String, List<String>>?): SchemaMetadata {
        // since properties are not used, should this be cached
        val schema = getCurrentSchema()
        val tablesInSchema = tableNamesCache?.get(schema) ?: tableNames().get(schema)
        return SchemaMetadata(getCurrentSchema(), tablesInSchema)
    }

    override suspend fun columns(vararg tables: Table): Map<Table, List<ColumnMetadata>> {
        val result = mutableMapOf<Table, List<ColumnMetadata>>()
        val useSchemaInsteadOfDatabase = currentDialect is MysqlDialect
        val tablesBySchema = tables.groupBy { identifierManager.inProperCase(it.schemaName ?: getCurrentSchema()) }

        for ((schema, schemaTables) in tablesBySchema.entries) {
            for (table in schemaTables) {
                val catalog = if (!useSchemaInsteadOfDatabase || schema == getCurrentSchema()) getDatabaseName() else schema
                // TODO is this necessary with R2DBC? Answer is no, because all data is returned by getColumns() query below
                // But it is temporarily left in as executeSQL block below needs to be refactored to process & emit 2 different results
                val prefetchedColumnTypes = fetchAllColumnTypes(table.nameInDatabaseCase())
                val query = metadataProvider.getColumns(catalog, schema, table.nameInDatabaseCaseUnquoted())

                @OptIn(InternalApi::class)
                val columns = connection.executeSQL(query) { row, _ ->
                    // Unlike JdbcResult, R2dbcResult is split apart for ResultApi vs RowApi, so a 2nd arg placeholder has to be used
                    with(ExposedMetadataUtils) {
                        R2dbcRow(row, R2dbcRegistryTypeMappingImpl()).asColumnMetadata(prefetchedColumnTypes)
                    }
                }.orEmpty()
                check(columns.isNotEmpty())
                result[table] = columns
            }
        }

        return result
    }

    /**
     * Returns a map of all the columns' names mapped to their type.
     *
     * Currently, only H2Dialect will actually return a result.
     */
    private suspend fun fetchAllColumnTypes(tableName: String): Map<String, String> {
        if (currentDialect !is H2Dialect) return emptyMap()

        return connection.executeSQL("SHOW COLUMNS FROM $tableName") { row, _ ->
            val field = row.getString("FIELD")
            val type = row.getString("TYPE")?.uppercase() ?: ""
            field?.let { it to type }
        }?.filterNotNull()?.toList().orEmpty().toMap()
    }

    private val existingIndicesCache = HashMap<Table, List<Index>>()

    override suspend fun existingIndices(vararg tables: Table): Map<Table, List<Index>> {
        for (table in tables) {
            val transaction = TransactionManager.current()
            val (catalog, tableSchema) = tableCatalogAndSchema(table)

            existingIndicesCache.getOrPut(table) {
                val pkQuery = metadataProvider.getPrimaryKeys(catalog, tableSchema, table.nameInDatabaseCaseUnquoted())
                val pkNames = connection.executeSQL(pkQuery) { row, _ ->
                    row.getString("PK_NAME")
                }?.filterNotNull().orEmpty()

                val storedIndexTable = if (
                    (tableSchema == getCurrentSchema() && currentDialect is OracleDialect) ||
                    currentDialect is SQLServerDialect
                ) {
                    table.nameInDatabaseCase()
                } else {
                    table.nameInDatabaseCaseUnquoted()
                }
                val indexQuery = metadataProvider.getIndexInfo(catalog, tableSchema, storedIndexTable)

                val tmpIndices = hashMapOf<Triple<String, Boolean, Op.TRUE?>, MutableList<String>>()

                connection.executeSQL(indexQuery) { row, _ ->
                    row.getString("INDEX_NAME")?.let { indexName ->
                        val columnNameMetadata = row.getString("COLUMN_NAME") ?: when (currentDialect) {
                            is MysqlDialect -> "\"\""
                            else -> null
                        }
                        columnNameMetadata?.let { columnName ->
                            val column = transaction.db.identifierManager
                                .quoteIdentifierWhenWrongCaseOrNecessary(columnName)
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

    override suspend fun existingCheckConstraints(vararg tables: Table): Map<Table, List<CheckConstraint>> {
        val tx = TransactionManager.current()
        return tables.associateWith { table ->
            val (catalog, tableSchema) = tableCatalogAndSchema(table)
            val query = metadataProvider.getCheckConstraints(catalog, tableSchema, table.nameInDatabaseCaseUnquoted())
            connection.executeSQL(query) { row, _ ->
                row.getString("CONSTRAINT_NAME")?.let {
                    CheckConstraint(
                        tx.identity(table),
                        it,
                        row.getString("CHECK_CLAUSE") ?: ""
                    )
                }
            }?.filterNotNull().orEmpty()
        }
    }

    override suspend fun existingPrimaryKeys(vararg tables: Table): Map<Table, PrimaryKeyMetadata?> {
        return tables.associateWith { table ->
            val (catalog, tableSchema) = tableCatalogAndSchema(table)
            val pkQuery = metadataProvider.getPrimaryKeys(catalog, tableSchema, table.nameInDatabaseCaseUnquoted())
            val results = flow {
                connection.createStatement(pkQuery)
                    .execute()
                    .collect { r ->
                        r.map { row, _ ->
                            row.getString("PK_NAME")?.let {
                                it to row.getString("COLUMN_NAME")!!
                            }
                        }
                            .collect { emit(it) }
                    }
            }.toList()
            val (names, columns) = results.unzip()
            names.distinct().singleOrNull()?.let { PrimaryKeyMetadata(it, columns) }
        }
    }

    override suspend fun existingSequences(vararg tables: Table): Map<Table, List<Sequence>> {
        return tables.associateWith { table ->
            val (catalog, tableSchema) = tableCatalogAndSchema(table)
            val query = metadataProvider.getSequences(catalog, tableSchema, table.nameInDatabaseCaseUnquoted())
            connection.executeSQL(query) { row, _ ->
                row.getString("SEQUENCE_NAME")?.let {
                    Sequence(
                        it,
                        row.getString("SEQUENCE_START")?.toLong(),
                        row.getString("SEQUENCE_INCREMENT")?.toLong(),
                        row.getString("SEQUENCE_MIN")?.toLong(),
                        row.getString("SEQUENCE_MAX")?.toLong(),
                        row.getBoolean("SEQUENCE_CYCLE"),
                        row.getString("SEQUENCE_CACHE")?.toLong()
                    )
                } ?: Sequence("")
            }?.filterNot { it.name.isEmpty() }.orEmpty()
        }
    }

    override suspend fun sequences(): List<String> {
        val results = connection.executeSQL(metadataProvider.getAllSequences()) { row, _ ->
            row.getString("SEQUENCE_NAME")!!
        }.orEmpty()

        val dialect = currentDialect
        return if (dialect is OracleDialect || dialect.h2Mode == H2Dialect.H2CompatibilityMode.Oracle) {
            results.map { if (identifierManager.isDotPrefixedAndUnquoted(it)) "\"$it\"" else it }
        } else {
            results
        }
    }

    override suspend fun tableConstraints(tables: List<Table>): Map<String, List<ForeignKeyConstraint>> {
        val allTables = SchemaUtils.sortTablesByReferences(tables).associateBy { it.nameInDatabaseCaseUnquoted() }

        return allTables.keys.associateWith { table ->
            val (catalog, tableSchema) = tableCatalogAndSchema(allTables[table]!!)
            val query = metadataProvider.getImportedKeys(catalog, identifierManager.inProperCase(tableSchema), table)
            connection.executeSQL(query) { row, _ ->
                row.extractForeignKeys(allTables, false)
            }.orEmpty()
                .filterNotNull()
                .unzip().second
                .groupBy { it.fkName }.values
                .map { it.reduce(ForeignKeyConstraint::plus) }
        }
    }

    private fun Row.extractForeignKeys(
        allTables: Map<String, Table>,
        isMysqlDialect: Boolean
    ): Pair<String, ForeignKeyConstraint>? {
        val fromTableName = getString("FKTABLE_NAME")!!
        if (isMysqlDialect && fromTableName !in allTables.keys) return null
        val fromColumnName = identifierManager.quoteIdentifierWhenWrongCaseOrNecessary(
            getString("FKCOLUMN_NAME")!!
        )
        val fromColumn = allTables[fromTableName]?.columns?.firstOrNull {
            val identifier = if (isMysqlDialect) it.nameInDatabaseCase() else it.name
            identifierManager.quoteIdentifierWhenWrongCaseOrNecessary(identifier) == fromColumnName
        } ?: return null // Do not crash if there are missing fields in Exposed's tables
        val constraintName = getString("FK_NAME")!!
        val targetTableName = getString("PKTABLE_NAME")!!
        val targetColumnName = identifierManager.quoteIdentifierWhenWrongCaseOrNecessary(
            if (isMysqlDialect) {
                getString("PKCOLUMN_NAME")!!
            } else {
                identifierManager.inProperCase(getString("PKCOLUMN_NAME")!!)
            }
        )
        val targetColumn = allTables[targetTableName]?.columns?.firstOrNull {
            identifierManager.quoteIdentifierWhenWrongCaseOrNecessary(it.nameInDatabaseCase()) == targetColumnName
        } ?: return null // Do not crash if there are missing fields in Exposed's tables
        val constraintUpdateRule = get("UPDATE_RULE")?.toString()?.let { resolveReferenceOption(it) }
        val constraintDeleteRule = get("DELETE_RULE")?.toString()?.let { resolveReferenceOption(it) }
        return fromTableName to ForeignKeyConstraint(
            target = targetColumn,
            from = fromColumn,
            onUpdate = constraintUpdateRule,
            onDelete = constraintDeleteRule,
            name = constraintName
        )
    }

    override fun cleanCache() {
        existingIndicesCache.clear()
    }

    private suspend fun tableCatalogAndSchema(table: Table): Pair<String, String> {
        val tableSchema = identifierManager.inProperCase(table.schemaName ?: getCurrentSchema())
        return if (currentDialect is MysqlDialect && tableSchema != getCurrentSchema()) {
            tableSchema to tableSchema
        } else {
            getDatabaseName() to tableSchema
        }
    }

    companion object {
        private val identityManagerCache = ConcurrentHashMap<String, R2dbcIdentifierManager>()
    }
}

// Core RowApi and R2dbcRow only provide getObject() variants, as per the only R2dbc SPI methods offered.
internal fun Row.getString(name: String): String? = get(name, java.lang.String::class.java)?.toString()

internal fun Row.getBoolean(name: String): Boolean = get(name)?.toString()?.toBoolean() == true
