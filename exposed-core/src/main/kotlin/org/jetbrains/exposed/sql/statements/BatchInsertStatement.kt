package org.jetbrains.exposed.sql.statements

import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.statements.api.PreparedStatementApi
import java.sql.ResultSet

class BatchDataInconsistentException(message: String) : Exception(message)

open class BatchInsertStatement(
    table: Table,
    ignore: Boolean = false,
    shouldReturnGeneratedValues: Boolean = true
) : BaseBatchInsertStatement(table, ignore, shouldReturnGeneratedValues)

private const val OUTPUT_ROW_LIMIT = 1000

open class SQLServerBatchInsertStatement(table: Table, ignore: Boolean = false, shouldReturnGeneratedValues: Boolean = true) :
    BatchInsertStatement(table, ignore, shouldReturnGeneratedValues) {
    override val isAlwaysBatch: Boolean = false

    override fun validateLastBatch() {
        super.validateLastBatch()
        if (data.size > OUTPUT_ROW_LIMIT) {
            throw BatchDataInconsistentException("Too much rows in one batch. Exceed $OUTPUT_ROW_LIMIT limit")
        }
    }

    private val columnToReturnValue = table.autoIncColumn?.takeIf { shouldReturnGeneratedValues && it.autoIncColumnType?.nextValExpression == null }

    override fun prepareSQL(transaction: Transaction): String {
        val values = arguments!!
        val sql = if (values.isEmpty()) ""
        else {
            val output = columnToReturnValue?.let {
                " OUTPUT inserted.${transaction.identity(it)} AS GENERATED_KEYS"
            }.orEmpty()

            QueryBuilder(true).apply {
                values.appendTo(prefix = "$output VALUES") {
                    it.appendTo(prefix = "(", postfix = ")") { (col, value) ->
                        registerArgument(col, value)
                    }
                }
            }.toString()
        }
        return transaction.db.dialect.functionProvider.insert(isIgnore, table, values.firstOrNull()?.map { it.first }.orEmpty(), sql, transaction)
    }

    override fun arguments() = listOfNotNull(super.arguments().flatten().takeIf { data.isNotEmpty() })

    override fun PreparedStatementApi.execInsertFunction(): Pair<Int, ResultSet?> {
        val rs = if (columnToReturnValue != null) {
            executeQuery()
        } else {
            executeUpdate()
            null
        }
        return arguments!!.size to rs
    }
}
