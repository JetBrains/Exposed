package org.jetbrains.exposed.sql.vendors

/**
 * Base class responsible for providing metadata from a database, which cannot be retrieved through the
 * standard metadata provided by the connection.
 */
abstract class MetadataProvider(
    override val propertyProvider: PropertyProvider,
    override val typeProvider: SqlTypeProvider
) : QueryProvider {
    // store details about db, versions, urls, username, etc?
}
