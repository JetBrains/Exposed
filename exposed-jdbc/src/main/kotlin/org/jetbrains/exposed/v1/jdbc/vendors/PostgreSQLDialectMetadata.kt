package org.jetbrains.exposed.v1.jdbc.vendors

/**
 * PostgreSQL dialect metadata implementation.
 */
open class PostgreSQLDialectMetadata : DatabaseDialectMetadata() {
    override fun supportsLimitWithUpdateOrDelete(): Boolean = false
}

/**
 * PostgreSQL dialect metadata implementation using the pgjdbc-ng JDBC driver.
 */
class PostgreSQLNGDialectMetadata : PostgreSQLDialectMetadata()
