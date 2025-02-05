package org.jetbrains.exposed.sql.vendors

open class PostgreSQLDialectMetadata : DatabaseDialectMetadata() {
    override fun supportsLimitWithUpdateOrDelete(): Boolean = true
}

class PostgreSQLNGDialectMetadata : PostgreSQLDialectMetadata()
