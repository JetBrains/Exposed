package org.jetbrains.exposed.sql.vendors

import org.h2.engine.Session
import org.h2.jdbc.JdbcConnection
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.TransactionManager

internal object H2DataTypeProvider : DataTypeProvider() {
    override fun uuidType(): String = "UUID"
}

internal object H2Dialect: VendorDialect("h2", H2DataTypeProvider) {

    private val isMySQLMode: Boolean get() = currentMode() == "MySQL"

    override val supportsMultipleGeneratedKeys: Boolean = false

    override fun replace(table: Table, data: List<Pair<Column<*>, Any?>>, transaction: Transaction): String {
        if (!isMySQLMode) throw UnsupportedOperationException("REPLACE is only supported in MySQL compatibility more for H2")

        val builder = QueryBuilder(true)
        val values = data.map { builder.registerArgument(it.first.columnType, it.second) }

        val inlineBuilder = QueryBuilder(false)
        val preparedValues = data.map { transaction.identity(it.first) to inlineBuilder.registerArgument(it.first.columnType, it.second) }


        return "INSERT INTO ${transaction.identity(table)} (${preparedValues.joinToString { it.first }}) VALUES (${values.joinToString()}) ON DUPLICATE KEY UPDATE ${preparedValues.joinToString { "${it.first}=${it.second}" }}"
    }

    private fun currentMode(): String =
            ((TransactionManager.current().connection as? JdbcConnection)?.session as? Session)?.database?.mode?.name ?: ""

    override fun existingIndices(vararg tables: Table): Map<Table, List<Index>> =
            super.existingIndices(*tables).mapValues { it.value.filterNot { it.indexName.startsWith("PRIMARY_KEY_")  } }.filterValues { it.isNotEmpty() }

    override fun insert(ignore: Boolean, table: Table, columns: List<Column<*>>, expr: String, transaction: Transaction): String {
        val uniqueIdxCols = table.indices.filter { it.second }.flatMap { it.first.toList() }
        val uniqueCols = columns.filter { it.indexInPK != null || it in uniqueIdxCols}
        return if (ignore && uniqueCols.isNotEmpty() && isMySQLMode) {
            val def = super.insert(false, table, columns, expr, transaction)
            def + " ON DUPLICATE KEY UPDATE " + uniqueCols.joinToString { "${transaction.identity(it)}=VALUES(${transaction.identity(it)})" }
        } else {
            super.insert(ignore, table, columns, expr, transaction)
        }
    }
}