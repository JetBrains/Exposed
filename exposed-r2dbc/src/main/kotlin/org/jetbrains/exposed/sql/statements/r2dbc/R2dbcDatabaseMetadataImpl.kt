package org.jetbrains.exposed.sql.statements.r2dbc

import io.r2dbc.spi.Connection
import io.r2dbc.spi.Row
import io.r2dbc.spi.RowMetadata
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.reactive.collect
import kotlinx.coroutines.withContext
import org.intellij.lang.annotations.Language
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.statements.api.IdentifierManagerApi
import org.jetbrains.exposed.sql.statements.api.R2dbcExposedDatabaseMetadata
import org.jetbrains.exposed.sql.vendors.*
import org.jetbrains.exposed.sql.vendors.H2Dialect.H2MajorVersion
import org.jetbrains.exposed.sql.vendors.metadata.MetadataProvider
import java.math.BigDecimal
import java.sql.SQLException
import java.util.concurrent.ConcurrentHashMap

/**
 * Base class responsible for retrieving and storing information about the R2DBC driver and underlying database.
 */
@Suppress("UnusedPrivateMember", "UnusedParameter")
class R2dbcDatabaseMetadataImpl(
    database: String,
    val metadata: MetadataProvider,
    private val connection: Connection,
    private val scope: R2dbcScope
) : R2dbcExposedDatabaseMetadata(database) {
    private val connectionData = connection.metadata

    override suspend fun getUrl(): String = fetchMetadata(metadata.getUsername()) { r, _ -> r.getString(1)!! }.single()

    override suspend fun getVersion(): BigDecimal = connectionData.databaseVersion
        .split('.', ' ')
        .let {
            BigDecimal("${it[0]}.${it[1]}")
        }

    override suspend fun getMajorVersion(): Int {
        TODO("Not yet implemented")
    }

    override suspend fun getMinorVersion(): Int {
        TODO("Not yet implemented")
    }

    override fun getDatabaseDialectName(): String {
        return when (connectionData.databaseProductName) {
            "MySQL Community Server - GPL", "MySQL Community Server (GPL)" -> MysqlDialect.dialectName
            "MariaDB" -> MariaDBDialect.dialectName
            "H2" -> H2Dialect.dialectName
            "PostgreSQL" -> PostgreSQLDialect.dialectName
            "Oracle" -> OracleDialect.dialectName
            else -> {
                if (connectionData.databaseProductName.startsWith("Microsoft SQL Server ")) {
                    SQLServerDialect.dialectName
                } else {
                    R2dbcDatabase.getR2dbcDialectName(database)
                        ?: error("Unsupported driver ${connectionData.databaseProductName} detected")
                }
            }
        }
    }

    private val databaseName
        get() = when (getDatabaseDialectName()) {
            MysqlDialect.dialectName, MariaDBDialect.dialectName -> currentSchema!!
            else -> database
        }

    @InternalApi
    override suspend fun getDatabaseDialectMode(): String? {
        val dialect = currentDialect
        if (dialect !is H2Dialect) null

        return when (val dialect = currentDialect) {
            is H2Dialect -> {
                val (settingNameField, settingValueField) = when (dialect.majorVersion) {
                    H2MajorVersion.One -> "NAME" to "VALUE"
                    H2MajorVersion.Two -> "SETTING_NAME" to "SETTING_VALUE"
                }

                @Language("H2")
                val modeQuery = "SELECT $settingValueField FROM INFORMATION_SCHEMA.SETTINGS WHERE $settingNameField = 'MODE'"
                fetchMetadata(modeQuery) { row, _ ->
                    row.getString(settingValueField)
                }.first()
            }
            else -> null
        }
    }

    override suspend fun getDatabaseProductVersion(): String = connectionData.databaseVersion

    override suspend fun getDefaultIsolationLevel(): Int = connection.transactionIsolationLevel.asInt()

    override val supportsAlterTableWithAddColumn: Boolean by lazy { metadata.propertyProvider.supportsAlterTableWithAddColumn }

    override val supportsAlterTableWithDropColumn: Boolean by lazy { metadata.propertyProvider.supportsAlterTableWithDropColumn }

    override val supportsMultipleResultSets: Boolean by lazy { metadata.propertyProvider.supportsMultipleResultSets }

    override val supportsSelectForUpdate: Boolean by lazy { metadata.propertyProvider.supportsSelectForUpdate }

    override suspend fun supportsLimitWithUpdateOrDelete(): Boolean {
        return when (currentDialect) {
            is SQLiteDialect -> with(metadata) {
                try {
                    fetchMetadata(
                        """SELECT sqlite_compileoption_used("ENABLE_UPDATE_DELETE_LIMIT");"""
                    ) { r, _ ->
                        r.getBoolean(1)
                    }.single() == true
                } catch (_: SQLException) {
                    false
                }
            }
            is PostgreSQLDialect -> false
            else -> true
        }
    }

    override val identifierManager: IdentifierManagerApi by lazy {
        // db URL as KEY causes issues with multi-tenancy!
        identityManagerCache.getOrPut(database) { R2dbcIdentifierManager(metadata, connectionData) }
    }

    private var currentSchema: String? = null
        get() {
            TODO()
//            if (field == null) {
//                field = try {
//                    when (getDatabaseDialectName()) {
//                        MysqlDialect.dialectName, MariaDBDialect.dialectName -> getCatalog()
//                        OracleDialect.dialectName -> connection.schema ?: databaseName
//                        else -> metadata.connection.schema.orEmpty()
//                    }
//                } catch (_: Throwable) {
//                    ""
//                }
//            }
//            return field!!
        }

    override fun resetCurrentScheme() {
        @OptIn(InternalApi::class)
        currentSchema = null
    }

    override suspend fun tableNames(): Map<String, List<String>> {
//        @OptIn(InternalApi::class)
//        return CachableMapWithDefault(
//            default = { schemeName -> tableNamesFor(schemeName) }
//        )
        TODO()
    }

    @OptIn(InternalApi::class)
    private suspend fun tableNamesFor(schema: String): List<String> = with(metadata) {
        TODO()
    }

    suspend fun getCatalog(): String? = with(metadata) {
        fetchMetadata(getCatalog()) { row, _ ->
            row.getString("TABLE_CAT")
        }
    }.single()

    suspend fun getSchema(): String? = with(metadata) {
        fetchMetadata(getSchema()) { row, _ ->
            row.getString("TABLE_SCHEM")
        }
    }.single()

    suspend fun catalogs(): List<String> = with(metadata) {
        fetchMetadata(getCatalogs()) { row, _ ->
            row.getString("TABLE_CAT")
        }
    }.mapNotNull { name -> name?.let { identifierManager.inProperCase(it) } }

    override suspend fun schemaNames(): List<String> = with(metadata) {
        fetchMetadata(getSchemas()) { row, _ ->
            row.getString("TABLE_SCHEM")
        }
    }.mapNotNull { name -> name?.let { identifierManager.inProperCase(it) } }

    override suspend fun tableNamesByCurrentSchema(tableNamesCache: Map<String, List<String>>?): SchemaMetadata {
        // since properties are not used, should this be cached
        val tablesInSchema = (tableNamesCache ?: tableNames()).getValue(currentSchema!!)
        return SchemaMetadata(currentSchema!!, tablesInSchema)
    }

    override suspend fun columns(vararg tables: Table): Map<Table, List<ColumnMetadata>> {
        TODO("Not yet implemented")
    }

//    private fun Row.asColumnMetadata(): ColumnMetadata {
//        @OptIn(InternalApi::class)
//        val defaultDbValue = getString("COLUMN_DEF")?.let { sanitizedDefault(it) }
//        val autoIncrement = getString("IS_AUTOINCREMENT") == "YES"
//        val type = getInt("DATA_TYPE")!!
//        val name = getString("COLUMN_NAME")!!
//        val nullable = getBoolean("NULLABLE")
//        val size = getInt("COLUMN_SIZE").takeIf { it != 0 }
//        val scale = getInt("DECIMAL_DIGITS").takeIf { it != 0 }
//
//        return ColumnMetadata(name, type, nullable, size, scale, autoIncrement, defaultDbValue?.takeIf { !autoIncrement })
//    }

    private val existingIndicesCache = HashMap<Table, List<Index>>()

    override suspend fun existingIndices(vararg tables: Table): Map<Table, List<Index>> {
        TODO("Not yet implemented")
    }

    override suspend fun existingPrimaryKeys(vararg tables: Table): Map<Table, PrimaryKeyMetadata?> {
        TODO("Not yet implemented")
    }

    override suspend fun existingSequences(vararg tables: Table): Map<Table, List<Sequence>> {
        TODO("Not yet implemented")
    }

    override suspend fun sequences(): List<String> {
        TODO("Not yet implemented")
    }

    override suspend fun tableConstraints(tables: List<Table>): Map<String, List<ForeignKeyConstraint>> {
        TODO("Not yet implemented")
    }

//    private fun Row.parseConstraint(
//        allTables: Map<String, Table>,
//        isMysqlDialect: Boolean
//    ): Pair<String, ForeignKeyConstraint>? {
//        val fromTableName = getString("FKTABLE_NAME")!!
//        if (isMysqlDialect && fromTableName !in allTables.keys) return null
//        val fromColumnName = identifierManager.quoteIdentifierWhenWrongCaseOrNecessary(
//            getString("FKCOLUMN_NAME")!!
//        )
//        val fromColumn = allTables[fromTableName]?.columns?.firstOrNull {
//            val identifier = if (isMysqlDialect) it.nameInDatabaseCase() else it.name
//            identifierManager.quoteIdentifierWhenWrongCaseOrNecessary(identifier) == fromColumnName
//        } ?: return null // Do not crash if there are missing fields in the Exposed tables
//        val constraintName = getString("FK_NAME")!!
//        val targetTableName = getString("PKTABLE_NAME")!!
//        val targetColumnName = identifierManager.quoteIdentifierWhenWrongCaseOrNecessary(
//            if (isMysqlDialect) {
//                getString("PKCOLUMN_NAME")!!
//            } else {
//                identifierManager.inProperCase(getString("PKCOLUMN_NAME")!!)
//            }
//        )
//        val targetColumn = allTables[targetTableName]?.columns?.firstOrNull {
//            identifierManager.quoteIdentifierWhenWrongCaseOrNecessary(it.nameInDatabaseCase()) == targetColumnName
//        } ?: return null // Do not crash if there are missing fields in the Exposed tables
//        val constraintUpdateRule = get("UPDATE_RULE")?.toString()?.let {
//            resolveReferenceOption(it)
//        }
//        val constraintDeleteRule = get("DELETE_RULE")?.toString()?.let {
//            resolveReferenceOption(it)
//        }
//        return fromTableName to ForeignKeyConstraint(
//            target = targetColumn,
//            from = fromColumn,
//            onUpdate = constraintUpdateRule,
//            onDelete = constraintDeleteRule,
//            name = constraintName
//        )
//    }

    @OptIn(InternalApi::class)
    override fun resolveReferenceOption(refOption: String): ReferenceOption? {
        TODO("Not yet implemented")
    }

    override fun cleanCache() {
        existingIndicesCache.clear()
    }

    companion object {
        private val identityManagerCache = ConcurrentHashMap<String, R2dbcIdentifierManager>()
    }

    private suspend fun <T> fetchMetadata(
        sqlQuery: String,
        body: (Row, RowMetadata) -> T
    ): List<T> = withContext(scope.coroutineContext) {
        flow {
            connection
                .createStatement(sqlQuery)
                .execute()
                .collect { r ->
                    r.map { row, metadata ->
                        body(row, metadata)
                    }
                        .collect { emit(it) }
                }
        }.toList()
    }

    private fun Row.getString(name: String): String? = get(name, java.lang.String::class.java)?.toString()

    private fun Row.getString(index: Int): String? = get(index, java.lang.String::class.java)?.toString()

    private fun Row.getBoolean(name: String): Boolean = get(name, java.lang.Boolean::class.java)?.booleanValue() == true

    private fun Row.getBoolean(index: Int): Boolean = get(index, java.lang.Boolean::class.java)?.booleanValue() == true

    private fun Row.getInt(name: String): Int? = get(name, java.lang.Integer::class.java)?.toInt()
}
