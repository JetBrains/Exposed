package org.jetbrains.exposed.sql.statements

import org.jetbrains.exposed.sql.*
import java.sql.PreparedStatement

/**
 * isIgnore is supported for mysql only
 */
open class InsertStatement(val table: Table, val isIgnore: Boolean = false) : UpdateBuilder<Int>(StatementType.INSERT, listOf(table)) {
    var generatedKey: Int? = null

    infix operator fun get(column: Column<Int>): Int = generatedKey ?: error("No key generated")

    override fun arguments(): Iterable<Iterable<Pair<ColumnType, Any?>>> = listOf(values.map { it.key.columnType to it.value })

    override fun prepareSQL(transaction: Transaction): String {
        val builder = QueryBuilder(true)
        return transaction.db.dialect.insert(isIgnore, table, values.map { it.key},
                "VALUES (${values.map { builder.registerArgument(it.value, it.key.columnType) }.joinToString()})", transaction)
    }

    override fun PreparedStatement.executeInternal(transaction: Transaction): Int  = executeUpdate().apply {
        if (table.columns.any { it.columnType.autoinc }) {
            generatedKeys?.let { rs ->
                if (rs.next()) {
                    generatedKey = rs.getInt(1)
                }
            }
        }
    }
}
