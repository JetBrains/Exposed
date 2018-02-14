package org.jetbrains.exposed.sql.vendors

import org.h2.engine.Session
import org.h2.jdbc.JdbcConnection
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.TransactionManager
import java.sql.Wrapper

internal object H2DataTypeProvider : DataTypeProvider() {
    override fun uuidType(): String = "UUID"
}

internal class H2Dialect: VendorDialect(dialectName, H2DataTypeProvider) {

    override fun isAllowedAsColumnDefault(e: Expression<*>): Boolean = true

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
            ((TransactionManager.current().connection as Wrapper).unwrap(JdbcConnection::class.java).session as? Session)?.database?.mode?.name ?: ""

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

    override fun createIndex(index: Index): String {
        if (index.columns.any { it.columnType is TextColumnType }) {
            exposedLogger.warn("Index on ${index.table.tableName} for ${index.columns.joinToString {it.name}} can't be created in H2")
            return ""
        }
        return super.createIndex(index)
    }

    companion object {
        const val dialectName = "h2"
    }
}