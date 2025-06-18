package org.jetbrains.exposed.v1.core.statements

import org.jetbrains.exposed.v1.core.InternalApi
import org.jetbrains.exposed.v1.core.QueryBuilder
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.Transaction
import org.jetbrains.exposed.v1.core.autoIncColumnType

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
    @OptIn(InternalApi::class)
    override fun validateLastBatch() {
        super.validateLastBatch()
        if (data.size > OUTPUT_ROW_LIMIT) {
            throw BatchDataInconsistentException("Too much rows in one batch. Exceed $OUTPUT_ROW_LIMIT limit")
        }
    }

    @InternalApi
    val columnToReturnValue = table.autoIncColumn?.takeIf {
        shouldReturnGeneratedValues && it.autoIncColumnType?.nextValExpression == null
    }

    override fun prepareSQL(transaction: Transaction, prepared: Boolean): String {
        val values = arguments!!
        val sql = if (values.isEmpty()) {
            ""
        } else {
            @OptIn(InternalApi::class)
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

    override fun arguments() = listOfNotNull(
        @OptIn(InternalApi::class)
        super.arguments().flatten().takeIf { data.isNotEmpty() }
    )
}
