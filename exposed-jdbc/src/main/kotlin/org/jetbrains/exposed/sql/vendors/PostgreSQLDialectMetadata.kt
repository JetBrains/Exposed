package org.jetbrains.exposed.sql.vendors

class PostgreSQLDialectMetadata : DatabaseDialectMetadata() {
    override fun supportsLimitWithUpdateOrDelete(): Boolean = true
}

class PostgreSQLNGDialectMetadata : DatabaseDialectMetadata()
