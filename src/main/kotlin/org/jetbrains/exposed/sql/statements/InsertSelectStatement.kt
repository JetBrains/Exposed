package org.jetbrains.exposed.sql.statements

import org.jetbrains.exposed.sql.Query
import org.jetbrains.exposed.sql.QueryBuilder
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.Transaction
import java.lang.IllegalStateException

class InsertSelectStatement(val table: Table, val selectQuery: Query, val isIgnore: Boolean = false): Statement<Int>(StatementType.INSERT, listOf(table)) {
    var generatedKey: Int? = null

    operator fun get(column: org.jetbrains.exposed.sql.Column<Int>): Int = generatedKey ?: error("Statement is not executed or table has not any auto-generated fields")

    override fun java.sql.PreparedStatement.executeInternal(transaction: Transaction): Int? = executeUpdate().apply {
        if (table.columns.any { it.columnType.autoinc }) {
            generatedKey = generatedKeys?.let { rs ->
                if (rs.next()) {
                    return rs.getInt(1)
                } else if (!isIgnore) {
                    throw IllegalStateException("No key generated after statement: ${prepareSQL(transaction)}")
                } else {
                    null
                }
            }
        }
    }

    override fun arguments(): Iterable<Iterable<Pair<org.jetbrains.exposed.sql.ColumnType, Any?>>> = emptyList()

    override fun prepareSQL(transaction: Transaction): String {
        val columns = table.columns.filter { !it.columnType.autoinc }
        return transaction.db.dialect.insert(isIgnore,  table, columns, selectQuery.prepareSQL(QueryBuilder(false)), transaction)
    }
}
