package org.jetbrains.exposed.v1.r2dbc.sql.vendors.metadata

/**
 * Base class responsible for providing metadata from a database, which cannot be retrieved through the
 * standard metadata provided by the connection.
 */
internal abstract class MetadataProvider(
    override val propertyProvider: PropertyProvider,
    override val typeProvider: SqlTypeProvider
) : QueryProvider {
    companion object {
        fun getProvider(dialect: String): MetadataProvider = when (dialect) {
            "PostgreSQL" -> PostgreSQLMetadata()
            "MySQL" -> MySQLMetadata()
            "MariaDB" -> org.jetbrains.exposed.v1.r2dbc.sql.vendors.metadata.MariaDBMetadata()
            "Oracle" -> OracleMetadata()
            "SQLServer" -> SQLServerMetadata()
            "H2" -> H2Metadata()
            else -> error("Dialect does not have supported metadata provider: $dialect")
        }
    }
}
