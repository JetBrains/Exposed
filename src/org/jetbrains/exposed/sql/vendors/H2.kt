package org.jetbrains.exposed.sql.vendors

import org.h2.jdbc.JdbcConnection
import org.jetbrains.exposed.sql.*

/**
 * User: Andrey.Tarashevskiy
 * Date: 05.10.2015
 */

internal object H2Dialect: VendorDialect("h2") {

    // h2 supports only JDBC API from Java 1.6
    override fun getDatabase(): String {
        return Transaction.current().connection.catalog
    }

    override fun supportsSelectForUpdate() = false

    override fun replace(table: Table, data: List<Pair<Column<*>, Any?>>, transaction: Transaction): String {
        if (currentMode() != "MySQL") throw UnsupportedOperationException("REPLACE is only supported in MySQL compatibility more for H2")

        val builder = QueryBuilder(true)
        val values = data.map { builder.registerArgument(it.second, it.first.columnType) }

        val inlineBuilder = QueryBuilder(false)
        val preparedValues = data.map { transaction.identity(it.first) to inlineBuilder.registerArgument(it.second, it.first.columnType) }


        return "INSERT INTO ${transaction.identity(table)} (${preparedValues.map { it.first }.joinToString()}) VALUES (${values.joinToString()}) ON DUPLICATE KEY UPDATE ${preparedValues.map { "${it.first}=${it.second}" }.joinToString()}"
    }

    private fun currentMode(): String {
        return ((Transaction.current().connection as? JdbcConnection)?.session as? org.h2.engine.Session)?.database?.mode?.name ?: ""
    }
}
