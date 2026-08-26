package org.jetbrains.exposed.v1.core.statements

import org.jetbrains.exposed.v1.core.InternalApi
import org.jetbrains.exposed.v1.core.QueryBuilder
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.Transaction
import org.jetbrains.exposed.v1.core.vendors.SQLiteDialect
import org.jetbrains.exposed.v1.core.vendors.currentDialect

/** The documented hard limit on bind parameters per statement (protocol int16 count field) for most supported databases. */
private const val DEFAULT_PARAMETER_LIMIT = 65535

/** The documented hard limit on bind parameters per statement for SQLite. */
private const val SQLITE_PARAMETER_LIMIT = 32766

/**
 * Represents the SQL statement that batch inserts new rows into a table by using a single multi-row
 * `INSERT ... VALUES (...), (...), ...` statement instead of executing one bound statement per row.
 *
 * Before adding each new batch, the class validates that the database's maximum bind-parameter count is not being exceeded.
 */
open class MultiRowValuesInsertStatement(
    table: Table,
    ignore: Boolean = false,
    shouldReturnGeneratedValues: Boolean = true
) : BatchInsertStatement(table, ignore, shouldReturnGeneratedValues) {
    @OptIn(InternalApi::class)
    override fun validateLastBatch() {
        super.validateLastBatch()
        val parameterCount = data.size.toLong() * table.columns.size.toLong()
        val limit = if (currentDialect is SQLiteDialect) SQLITE_PARAMETER_LIMIT else DEFAULT_PARAMETER_LIMIT
        if (parameterCount > limit) {
            throw BatchDataInconsistentException(
                "Too many parameters in one batch. Exceeds the database's limit of $limit bind parameters."
            )
        }
    }

    override fun prepareSQL(transaction: Transaction, prepared: Boolean): String {
        val values = arguments!!
        val sql = if (values.isEmpty()) {
            ""
        } else {
            QueryBuilder(prepared).apply {
                values.appendTo(prefix = "VALUES ") {
                    it.appendTo(prefix = "(", postfix = ")") { (col, value) ->
                        registerArgument(col, value)
                    }
                }
            }.toString()
        }
        val columnsToUse = values.firstOrNull()?.map { it.first }.orEmpty()
        return transaction.db.dialect.functionProvider
            .insertMultiRowValues(isIgnore, table, columnsToUse, sql, values.size, transaction)
    }

    override fun arguments() = listOfNotNull(
        @OptIn(InternalApi::class)
        super.arguments().flatten().takeIf { data.isNotEmpty() }
    )
}
