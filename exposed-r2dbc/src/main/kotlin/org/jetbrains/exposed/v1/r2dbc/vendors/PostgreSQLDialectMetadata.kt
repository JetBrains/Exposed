package org.jetbrains.exposed.v1.r2dbc.vendors

/**
 * PostgreSQL dialect metadata implementation.
 */
open class PostgreSQLDialectMetadata : DatabaseDialectMetadata() {
    override suspend fun supportsLimitWithUpdateOrDelete(): Boolean = false
}
