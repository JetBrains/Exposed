package org.jetbrains.exposed.sql.vendors

import org.h2.jdbc.JdbcConnection
import org.jetbrains.exposed.sql.Transaction

/**
 * User: Andrey.Tarashevskiy
 * Date: 05.10.2015
 */

internal object H2Dialect: VendorDialect() {

    // h2 supports only JDBC API from Java 1.6
    override fun getDatabase(): String {
        return Transaction.current().connection.catalog
    }

    override fun replace(table: String, columns: List<String>, values: List<String>): String {
        if (currentMode() != "MySQL") throw UnsupportedOperationException("REPLACE is only supported in MySQL compatibility more for H2")
        return "INSERT INTO $table (${columns.joinToString()}) VALUES (${values.joinToString()}) ON DUPLICATE KEY UPDATE ${columns.zip(values).map { "${it.first}=${it.second}" }.joinToString()}"
    }

    private fun currentMode(): String {
        return ((Transaction.current().connection as? JdbcConnection)?.session as? org.h2.engine.Session)?.database?.mode?.name ?: ""
    }
}
