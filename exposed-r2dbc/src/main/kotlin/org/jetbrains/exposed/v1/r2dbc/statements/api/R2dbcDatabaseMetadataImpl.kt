package org.jetbrains.exposed.v1.r2dbc.statements.api

import io.r2dbc.spi.Connection
import io.r2dbc.spi.ConnectionMetadata
import io.r2dbc.spi.IsolationLevel
import io.r2dbc.spi.Row
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.reactive.collect
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.core.statements.api.IdentifierManagerApi
import org.jetbrains.exposed.v1.core.vendors.*
import org.jetbrains.exposed.v1.r2dbc.R2dbcScope
import org.jetbrains.exposed.v1.r2dbc.SchemaUtils
import org.jetbrains.exposed.v1.r2dbc.statements.executeSQL
import org.jetbrains.exposed.v1.r2dbc.statements.getCurrentCatalog
import org.jetbrains.exposed.v1.r2dbc.statements.getCurrentSchema
import org.jetbrains.exposed.v1.r2dbc.transactions.TransactionManager
import org.jetbrains.exposed.v1.r2dbc.vendors.metadata.MetadataProvider
import java.math.BigDecimal
import java.sql.Types
import java.util.concurrent.ConcurrentHashMap

// TODO review why constructor parameters are not being used, e.g. 'scope'
/**
 * Base class responsible for retrieving and storing information about the R2DBC driver and underlying database.
 */
@Suppress("UnusedPrivateMember", "UnusedParameter")
class R2dbcDatabaseMetadataImpl(
    database: String,
    private val connection: Connection,
    private val vendorDialect: String,
    private val scope: R2dbcScope
) : R2dbcExposedDatabaseMetadata(database) {
    private val connectionData: ConnectionMetadata = connection.metadata
    private val metadataProvider: MetadataProvider = MetadataProvider.getProvider(vendorDialect)

    // TODO REVIEW db with major/minor/patch
    override fun getVersion(): BigDecimal = connectionData.databaseVersion
        .split('.', ' ')
        .let {
            BigDecimal("${it[0]}.${it[1]}")
        }

    override fun getMajorVersion(): Int = connectionData.databaseVersion.split('.', ' ')[0].toInt()

    override fun getMinorVersion(): Int = connectionData.databaseVersion.split('.', ' ')[1].toInt()

    override fun getDatabaseDialectName(): String {
        val dbProductName = connectionData.databaseProductName
        return when (dbProductName) {
            "MySQL Community Server - GPL", "MySQL Community Server (GPL)" -> MysqlDialect.dialectName
            "MariaDB" -> MariaDBDialect.dialectName
            "H2" -> H2Dialect.dialectName
            "PostgreSQL" -> PostgreSQLDialect.dialectName
            "Oracle" -> OracleDialect.dialectName
            else -> {
                if (dbProductName.startsWith("Microsoft Azure SQL ")) {
                    SQLServerDialect.dialectName
                } else {
                    error("Unsupported driver $dbProductName detected")
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

    override suspend fun getDefaultIsolationLevel(): IsolationLevel = connection.transactionIsolationLevel

    override val supportsAlterTableWithAddColumn: Boolean by lazy {
        metadataProvider.propertyProvider.supportsAlterTableWithAddColumn
    }

    override val supportsAlterTableWithDropColumn: Boolean by lazy {
        metadataProvider.propertyProvider.supportsAlterTableWithDropColumn
    }

    override val supportsMultipleResultSets: Boolean by lazy {
        metadataProvider.propertyProvider.supportsMultipleResultSets
    }

    override val supportsSelectForUpdate: Boolean by lazy {
        metadataProvider.propertyProvider.supportsSelectForUpdate
    }

    override val supportsLimitWithUpdateOrDelete: Boolean by lazy {
        metadataProvider.propertyProvider.supportsLimitWithUpdateOrDelete
    }

    override val identifierManager: IdentifierManagerApi by lazy {
        // db URL as KEY causes issues with multi-tenancy!
        // TODO REVIEW use of JDBC url versus database here
        identityManagerCache.getOrPut(database) { R2dbcIdentifierManager(metadataProvider, connectionData) }
    }

    private var currentSchema: String? = null

    // TODO compare side-by-side JDBC VS R2DBC (all API files in this package) to make sure nothing was left out
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

    override suspend fun tableNames(): Map<String, List<String>> {
        @OptIn(InternalApi::class)
        return CachableMapWithDefault( // should this internal cache model be refactored?
            default = { schemaName ->
                // TODO replace runBlocking with SuspendCachableMapWithDefault
                runBlocking { tableNamesFor(schemaName) }
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

    override suspend fun tableNamesByCurrentSchema(tableNamesCache: Map<String, List<String>>?): SchemaMetadata {
        // since properties are not used, should this be cached
        val tablesInSchema = (tableNamesCache ?: tableNames()).getValue(getCurrentSchema())
        return SchemaMetadata(getCurrentSchema(), tablesInSchema)
    }

    override suspend fun columns(vararg tables: Table): Map<Table, List<ColumnMetadata>> {
        val result = mutableMapOf<Table, List<ColumnMetadata>>()
        val useSchemaInsteadOfDatabase = currentDialect is MysqlDialect
        val tablesBySchema = tables.groupBy { identifierManager.inProperCase(it.schemaName ?: getCurrentSchema()) }

        for ((schema, schemaTables) in tablesBySchema.entries) {
            for (table in schemaTables) {
                val catalog = if (!useSchemaInsteadOfDatabase || schema == getCurrentSchema()) getDatabaseName() else schema
                // TODO is this necessary with R2DBC?
                val prefetchedColumnTypes = fetchAllColumnTypes(table.nameInDatabaseCase())
                val query = metadataProvider.getColumns(catalog, schema, table.nameInDatabaseCaseUnquoted())
                val columns = connection.executeSQL(query) { row, _ ->
                    row.asColumnMetadata(prefetchedColumnTypes)
                }.orEmpty()
                check(columns.isNotEmpty())
                result[table] = columns
            }
        }

        return result
    }

    // TODO is this necessary with R2DBC?
    /** Returns a map of all the columns' names mapped to their type. */
    private suspend fun fetchAllColumnTypes(tableName: String): Map<String, String> {
        if (currentDialect !is H2Dialect) return emptyMap()

        // TODO extract if needed to metadataProvider for future extension
        return TransactionManager.current().exec("SHOW COLUMNS FROM $tableName") { row ->
            val field = row.getString("FIELD")
            val type = row.getString("TYPE")?.uppercase() ?: ""
            field?.let { it to type }
        }?.filterNotNull()?.toList().orEmpty().toMap()
    }

    // All H2 V1 databases are excluded because Exposed will be dropping support for it soon
    @OptIn(InternalApi::class)
    private fun getColumnType(resultSet: Row, prefetchedColumnTypes: Map<String, String>): String {
        if (currentDialect !is H2Dialect) return ""

        val columnName = resultSet.getString("COLUMN_NAME")!!
        val columnType = prefetchedColumnTypes[columnName] ?: resultSet.getString("DATA_TYPE_OG")?.uppercase()!!
        val dataType = resultSet.getInt("DATA_TYPE")!!
        // TODO check if it works with R2DBC. It's possible that R2DBC does not use java.sql types.
        return if (dataType == Types.ARRAY) {
            val baseType = columnType.substringBefore(" ARRAY")
            normalizedColumnType(baseType) + columnType.replaceBefore(" ARRAY", "")
        } else {
            normalizedColumnType(columnType)
        }
    }

    // TODO Could Row be replaced with RowApi to share this between jdbc/r2dbc
    @OptIn(InternalApi::class)
    private fun Row.asColumnMetadata(prefetchedColumnTypes: Map<String, String> = emptyMap()): ColumnMetadata {
        val defaultDbValue = getString("COLUMN_DEF")?.let { sanitizedDefault(it) }
        val autoIncrement = getString("IS_AUTOINCREMENT") == "YES"
        val type = getInt("DATA_TYPE")!!
        val name = getString("COLUMN_NAME")!!
        val nullable = getBoolean("NULLABLE")
        val size = getInt("COLUMN_SIZE").takeIf { it != 0 }
        val scale = getInt("DECIMAL_DIGITS").takeIf { it != 0 }
        val sqlType = getColumnType(this, prefetchedColumnTypes)

        return ColumnMetadata(name, type, sqlType, nullable, size, scale, autoIncrement, defaultDbValue?.takeIf { !autoIncrement })
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

    @OptIn(InternalApi::class)
    override fun resolveReferenceOption(refOption: String): ReferenceOption? {
        val refOptionInt = refOption.toIntOrNull() ?: return null

        val dialectMapping = metadataProvider.typeProvider.referenceOptions
        return dialectMapping.keys.first { dialectMapping[it] == refOptionInt }
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

// TODO are these not covered as part of RowApi or something public? Should they be?
internal fun Row.getString(name: String): String? = get(name, java.lang.String::class.java)?.toString()

internal fun Row.getBoolean(name: String): Boolean = get(name)?.toString()?.toBoolean() == true

internal fun Row.getInt(name: String): Int? = get(name)?.toString()?.toInt()
