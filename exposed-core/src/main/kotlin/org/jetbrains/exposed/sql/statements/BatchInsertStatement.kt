package org.jetbrains.exposed.sql.statements

import org.jetbrains.exposed.sql.QueryBuilder
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.autoIncColumnType
import org.jetbrains.exposed.sql.statements.api.PreparedStatementApi
import org.jetbrains.exposed.sql.statements.api.ResultApi

/** An exception thrown when the provided data cannot be validated or processed to prepare a batch statement. */
class BatchDataInconsistentException(message: String) : Exception(message)

/** Represents the SQL statement that batch inserts new rows into a table. */
open class BatchInsertStatement(
    table: Table,
    ignore: Boolean = false,
    shouldReturnGeneratedValues: Boolean = true
) : BaseBatchInsertStatement(table, ignore, shouldReturnGeneratedValues)

private const val OUTPUT_ROW_LIMIT = 1000

/**
 * Represents the SQL statement that batch inserts new rows into a table, specifically for the SQL Server database.
 *
 * Before adding each new batch, the class validates that the database's maximum number of inserted rows (1000)
 * is not being exceeded.
 */
open class SQLServerBatchInsertStatement(
    table: Table,
    ignore: Boolean = false,
    shouldReturnGeneratedValues: Boolean = true
) : BatchInsertStatement(table, ignore, shouldReturnGeneratedValues) {
    override val isAlwaysBatch: Boolean = false

    override fun validateLastBatch() {
        super.validateLastBatch()
        if (data.size > OUTPUT_ROW_LIMIT) {
            throw BatchDataInconsistentException("Too much rows in one batch. Exceed $OUTPUT_ROW_LIMIT limit")
        }
    }

    private val columnToReturnValue = table.autoIncColumn?.takeIf {
        shouldReturnGeneratedValues && it.autoIncColumnType?.nextValExpression == null
    }

    override fun prepareSQL(transaction: Transaction, prepared: Boolean): String {
        val values = arguments!!
        val sql = if (values.isEmpty()) {
            ""
        } else {
            val output = columnToReturnValue?.let {
                " OUTPUT inserted.${transaction.identity(it)} AS GENERATED_KEYS"
            }.orEmpty()

            QueryBuilder(prepared).apply {
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

    override suspend fun PreparedStatementApi.execInsertFunction(): Pair<Int, ResultApi?> {
        val rs = if (columnToReturnValue != null) {
            executeQuery()
        } else {
            executeUpdate()
            null
        }
        return arguments!!.size to rs
    }
}
