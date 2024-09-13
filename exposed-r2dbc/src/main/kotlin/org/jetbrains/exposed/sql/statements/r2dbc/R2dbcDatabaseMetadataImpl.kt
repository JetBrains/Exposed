package org.jetbrains.exposed.sql.statements.r2dbc

import io.r2dbc.spi.Connection
import io.r2dbc.spi.Result
import io.r2dbc.spi.Row
import io.r2dbc.spi.RowMetadata
import kotlinx.coroutines.reactive.awaitFirst
import org.jetbrains.exposed.r2dbc.R2dbcDatabase
import org.jetbrains.exposed.sql.ForeignKeyConstraint
import org.jetbrains.exposed.sql.Index
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.statements.api.ExposedDatabaseMetadata
import org.jetbrains.exposed.sql.statements.api.IdentifierManagerApi
import org.jetbrains.exposed.sql.vendors.*
import java.math.BigDecimal
import java.util.concurrent.ConcurrentHashMap

@Suppress("UnusedParameter", "UnusedPrivateMember")
class R2dbcDatabaseMetadataImpl(
    database: String,
    val metadata: MetadataProvider,
    private val connection: Connection
) : ExposedDatabaseMetadata(database) {
    private val connectionData = connection.metadata

    override val url: String by lazyMetadata { getUrl() }

    override val version: BigDecimal by lazy { BigDecimal(connectionData.databaseVersion) }

    override val databaseDialectName: String by lazy {
        when (connectionData.databaseProductName) {
            "MySQL" -> MysqlDialect.dialectName
            "MariaDB" -> MariaDBDialect.dialectName
            "H2" -> H2Dialect.dialectName
            "PostgreSQL" -> PostgreSQLDialect.dialectName
            "Oracle JDBC driver" -> OracleDialect.dialectName
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

    override val supportsAlterTableWithDropColumn: Boolean by lazyMetadata { true }

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
        TODO("Not yet implemented")
    }

    override val schemaNames: List<String> = schemaNames()

    private fun schemaNames(): List<String> = with(metadata) {
        TODO("Not yet implemented")
    }

    override fun tableNamesByCurrentSchema(tableNamesCache: Map<String, List<String>>?): SchemaMetadata {
        val tablesInSchema = (tableNamesCache ?: tableNames).getValue(currentSchema!!)
        return SchemaMetadata(currentSchema!!, tablesInSchema)
    }

    override fun columns(vararg tables: Table): Map<Table, List<ColumnMetadata>> {
        TODO("Not yet implemented")
    }

    private val existingIndicesCache = HashMap<Table, List<Index>>()

    override fun existingIndices(vararg tables: Table): Map<Table, List<Index>> {
        TODO("Not yet implemented")
    }

    override fun existingPrimaryKeys(vararg tables: Table): Map<Table, PrimaryKeyMetadata?> {
        TODO("Not yet implemented")
    }

    override fun sequences(): List<String> {
        TODO("Not yet implemented")
    }

    override fun tableConstraints(tables: List<Table>): Map<String, List<ForeignKeyConstraint>> {
        TODO("Not yet implemented")
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

    private suspend fun fetchMetadata(query: String): Result {
        return connection.createStatement(query).execute().awaitFirst()
    }

    private suspend fun <T> Result.iterate(body: Pair<Row, RowMetadata>.() -> T): List<T> {
        val result = mutableListOf<T>()
        map { row, metadata ->
            result.add(body(row to metadata))
        }
        return result
    }
}
