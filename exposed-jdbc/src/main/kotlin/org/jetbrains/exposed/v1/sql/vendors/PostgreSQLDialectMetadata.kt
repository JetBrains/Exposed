package org.jetbrains.exposed.v1.sql.vendors

open class PostgreSQLDialectMetadata : DatabaseDialectMetadata() {
    override fun supportsLimitWithUpdateOrDelete(): Boolean = false
}

class PostgreSQLNGDialectMetadata : PostgreSQLDialectMetadata()
