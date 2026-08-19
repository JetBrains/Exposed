package org.jetbrains.exposed.v1.core.statements

import org.jetbrains.exposed.v1.core.InternalApi
import org.jetbrains.exposed.v1.core.QueryBuilder
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.Transaction

/** PostgreSQL's hard limit on bind parameters per statement (protocol int16 count field). */
private const val POSTGRESQL_PARAMETER_LIMIT = 65535

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
        if (parameterCount > POSTGRESQL_PARAMETER_LIMIT) {
            throw BatchDataInconsistentException(
                "Too many parameters in one batch. Exceeds the database's limit of $POSTGRESQL_PARAMETER_LIMIT bind parameters."
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
        return transaction.db.dialect.functionProvider
            .insert(isIgnore, table, values.firstOrNull()?.map { it.first }.orEmpty(), sql, transaction)
    }

    override fun arguments() = listOfNotNull(
        @OptIn(InternalApi::class)
        super.arguments().flatten().takeIf { data.isNotEmpty() }
    )
}
