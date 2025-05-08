package org.jetbrains.exposed.v1.sql.statements

import org.jetbrains.exposed.v1.sql.Expression
import org.jetbrains.exposed.v1.sql.IColumnType
import org.jetbrains.exposed.v1.sql.Table
import org.jetbrains.exposed.v1.sql.Transaction
import org.jetbrains.exposed.v1.sql.statements.api.ResultApi

/**
 * Represents the underlying SQL [mainStatement] that also returns a result set with data from any modified rows.
 *
 * @param table Table to perform the main statement on and return results from.
 * @param returningExpressions Columns or expressions to include in the returned result set.
 * @param mainStatement The statement to append the RETURNING clause to. This may be an insert, update, or delete statement.
 */
open class ReturningStatement(
    val table: Table,
    val returningExpressions: List<Expression<*>>,
    val mainStatement: Statement<*>
) : Statement<ResultApi>(mainStatement.type, listOf(table)) {
    override fun arguments(): Iterable<Iterable<Pair<IColumnType<*>, Any?>>> = mainStatement.arguments()

    override fun prepareSQL(transaction: Transaction, prepared: Boolean): String {
        val mainSql = mainStatement.prepareSQL(transaction, prepared)
        return transaction.db.dialect.functionProvider.returning(mainSql, returningExpressions, transaction)
    }
}
