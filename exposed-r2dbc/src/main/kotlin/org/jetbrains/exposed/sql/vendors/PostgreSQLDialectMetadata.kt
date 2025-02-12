package org.jetbrains.exposed.sql.vendors

open class PostgreSQLDialectMetadata : DatabaseDialectMetadata() {
    override suspend fun supportsLimitWithUpdateOrDelete(): Boolean = false
}

class PostgreSQLNGDialectMetadata : PostgreSQLDialectMetadata()
