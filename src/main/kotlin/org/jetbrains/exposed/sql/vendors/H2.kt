package org.jetbrains.exposed.sql.vendors

import org.h2.engine.Session
import org.h2.jdbc.JdbcConnection
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.TransactionManager

internal object H2DataTypeProvider : DataTypeProvider() {
    override fun uuidType(): String = "UUID"
}

internal object H2Dialect: VendorDialect("h2", H2DataTypeProvider) {

    override val supportsMultipleGeneratedKeys: Boolean = false

    override fun replace(table: Table, data: List<Pair<Column<*>, Any?>>, transaction: Transaction): String {
        if (currentMode() != "MySQL") throw UnsupportedOperationException("REPLACE is only supported in MySQL compatibility more for H2")

        val builder = QueryBuilder(true)
        val values = data.map { builder.registerArgument(it.first.columnType, it.second) }

        val inlineBuilder = QueryBuilder(false)
        val preparedValues = data.map { transaction.identity(it.first) to inlineBuilder.registerArgument(it.first.columnType, it.second) }


        return "INSERT INTO ${transaction.identity(table)} (${preparedValues.map { it.first }.joinToString()}) VALUES (${values.joinToString()}) ON DUPLICATE KEY UPDATE ${preparedValues.map { "${it.first}=${it.second}" }.joinToString()}"
    }

    private fun currentMode(): String {
        return ((TransactionManager.current().connection as? JdbcConnection)?.session as? Session)?.database?.mode?.name ?: ""
    }

    override fun existingIndices(vararg tables: Table): Map<Table, List<Index>> {
        return super.existingIndices(*tables).mapValues { it.value.filterNot { it.indexName.startsWith("PRIMARY_KEY_")  } }.filterValues { it.isNotEmpty() }
    }
}