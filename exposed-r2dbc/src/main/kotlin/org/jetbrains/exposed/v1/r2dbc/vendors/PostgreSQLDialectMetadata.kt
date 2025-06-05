package org.jetbrains.exposed.v1.r2dbc.vendors

/**
 * PostgreSQL dialect metadata implementation.
 */
open class PostgreSQLDialectMetadata : DatabaseDialectMetadata() {
    override suspend fun supportsLimitWithUpdateOrDelete(): Boolean = false
}

// TODO clean up PostgresqlNG - if not supported, why here & in R2dbcDatabase.registerDialect()???
class PostgreSQLNGDialectMetadata : PostgreSQLDialectMetadata()
