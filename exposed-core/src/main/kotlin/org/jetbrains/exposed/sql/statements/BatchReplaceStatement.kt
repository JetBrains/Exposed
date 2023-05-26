package org.jetbrains.exposed.sql.statements

import org.jetbrains.exposed.sql.QueryBuilder
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.appendTo

open class BatchReplaceStatement(
    table: Table,
    shouldReturnGeneratedValues: Boolean = true
) : BaseBatchInsertStatement(table, ignore = false, shouldReturnGeneratedValues) {
    override fun prepareSQL(transaction: Transaction): String {
        val values = arguments!!.first()
        val valuesSql = if (values.isEmpty()) {
            ""
        } else {
            values.appendTo(QueryBuilder(true), prefix = "VALUES (", postfix = ")") { (column, value) ->
                registerArgument(column, value)
            }.toString()
        }
        val functionProvider = transaction.db.dialect.functionProvider
        return functionProvider.replace(table, values.unzip().first, valuesSql, transaction)
    }
}
