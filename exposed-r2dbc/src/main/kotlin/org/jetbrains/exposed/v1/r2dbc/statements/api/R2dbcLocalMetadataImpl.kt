package org.jetbrains.exposed.v1.r2dbc.statements.api

import io.r2dbc.spi.IsolationLevel
import org.jetbrains.exposed.v1.core.CheckConstraint
import org.jetbrains.exposed.v1.core.ForeignKeyConstraint
import org.jetbrains.exposed.v1.core.Index
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.Sequence
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.Version
import org.jetbrains.exposed.v1.core.statements.api.IdentifierManagerApi
import org.jetbrains.exposed.v1.core.utils.CacheWithSuspendableDefault
import org.jetbrains.exposed.v1.core.vendors.ColumnMetadata
import org.jetbrains.exposed.v1.core.vendors.PrimaryKeyMetadata
import org.jetbrains.exposed.v1.core.vendors.SchemaMetadata
import org.jetbrains.exposed.v1.r2dbc.vendors.metadata.MetadataProvider

/**
 * Class responsible for retrieving and storing information about the underlying database, which can be achieved
 * without relying on a database connection.
 */
open class R2dbcLocalMetadataImpl(
    database: String,
    vendorDialect: String
) : R2dbcExposedDatabaseMetadata(database) {
    private val metadataProvider: MetadataProvider = MetadataProvider.getProvider(vendorDialect)

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

    override fun resolveReferenceOption(refOption: String): ReferenceOption? {
        val refOptionInt = refOption.toIntOrNull() ?: return null

        val dialectMapping = metadataProvider.typeProvider.referenceOptions
        return dialectMapping.keys.first { dialectMapping[it] == refOptionInt }
    }

    companion object {
        const val CONNECTION_REQUIRED_ERROR = "This method requires that an `io.r2dbc.spi.Connection` has already been opened. " +
            "Please use `connectionMetadata()` to ensure that a connection is retrieved if one is not already open."

        const val SUSPEND_REQUIRED_ERROR = "This method requires retrieval of metadata through a database query. " +
            "Please use `metadata()` to ensure that a connection is retrieved to send a query, if one is not already open."
    }

    override val identifierManager: IdentifierManagerApi get() = error(CONNECTION_REQUIRED_ERROR)

    override fun getVersion(): Version = error(CONNECTION_REQUIRED_ERROR)

    override fun getMajorVersion(): Int = error(CONNECTION_REQUIRED_ERROR)

    override fun getMinorVersion(): Int = error(CONNECTION_REQUIRED_ERROR)

    override fun getDatabaseDialectName(): String = error(CONNECTION_REQUIRED_ERROR)

    override suspend fun getDatabaseDialectMode(): String? = error(SUSPEND_REQUIRED_ERROR)

    override fun getDatabaseProductVersion(): String = error(CONNECTION_REQUIRED_ERROR)

    override fun getDefaultIsolationLevel(): IsolationLevel = error(CONNECTION_REQUIRED_ERROR)

    override suspend fun tableNames(): CacheWithSuspendableDefault<String, List<String>> = error(SUSPEND_REQUIRED_ERROR)

    override suspend fun schemaNames(): List<String> = error(SUSPEND_REQUIRED_ERROR)

    override suspend fun tableNamesByCurrentSchema(
        tableNamesCache: CacheWithSuspendableDefault<String, List<String>>?
    ): SchemaMetadata = error(SUSPEND_REQUIRED_ERROR)

    override suspend fun columns(vararg tables: Table): Map<Table, List<ColumnMetadata>> = error(SUSPEND_REQUIRED_ERROR)

    override suspend fun existingIndices(vararg tables: Table): Map<Table, List<Index>> = error(SUSPEND_REQUIRED_ERROR)

    override suspend fun existingCheckConstraints(
        vararg tables: Table
    ): Map<Table, List<CheckConstraint>> = error(SUSPEND_REQUIRED_ERROR)

    override suspend fun existingPrimaryKeys(
        vararg tables: Table
    ): Map<Table, PrimaryKeyMetadata?> = error(SUSPEND_REQUIRED_ERROR)

    override suspend fun existingSequences(
        vararg tables: Table
    ): Map<Table, List<Sequence>> = error(SUSPEND_REQUIRED_ERROR)

    override suspend fun sequences(): List<String> = error(SUSPEND_REQUIRED_ERROR)

    override suspend fun tableConstraints(
        tables: List<Table>
    ): Map<String, List<ForeignKeyConstraint>> = error(SUSPEND_REQUIRED_ERROR)

    override fun resetCurrentScheme() {
        error(SUSPEND_REQUIRED_ERROR)
    }

    override fun cleanCache() {
        error(SUSPEND_REQUIRED_ERROR)
    }
}
