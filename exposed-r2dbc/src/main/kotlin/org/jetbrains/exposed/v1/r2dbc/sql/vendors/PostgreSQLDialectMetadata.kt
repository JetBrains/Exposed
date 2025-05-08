package org.jetbrains.exposed.v1.r2dbc.sql.vendors

open class PostgreSQLDialectMetadata : DatabaseDialectMetadata() {
    override suspend fun supportsLimitWithUpdateOrDelete(): Boolean = false
}

class PostgreSQLNGDialectMetadata : PostgreSQLDialectMetadata()
