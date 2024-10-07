package org.jetbrains.exposed.sql.statements

import org.jetbrains.exposed.sql.Expression
import org.jetbrains.exposed.sql.IColumnType
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.statements.api.PreparedStatementApi
import org.jetbrains.exposed.sql.statements.api.ResultApi
import org.jetbrains.exposed.sql.transactions.TransactionManager

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
) : Iterable<ResultRow>, Statement<ResultApi>(mainStatement.type, listOf(table)) {
    protected val transaction
        get() = TransactionManager.current()

    override suspend fun PreparedStatementApi.executeInternal(transaction: Transaction): ResultApi = executeQuery()

    override fun arguments(): Iterable<Iterable<Pair<IColumnType<*>, Any?>>> = mainStatement.arguments()

    override fun prepareSQL(transaction: Transaction, prepared: Boolean): String {
        val mainSql = mainStatement.prepareSQL(transaction, prepared)
        return transaction.db.dialect.functionProvider.returning(mainSql, returningExpressions, transaction)
    }

    override fun iterator(): Iterator<ResultRow> {
        val resultIterator = ResultIterator(transaction.exec(this)!!)
        return Iterable { resultIterator }.iterator()
    }

    private inner class ResultIterator(
        rs: ResultApi
    ) : StatementIterator<ResultApi, Expression<*>, ResultRow>(rs) {
        override val fieldIndex = returningExpressions.withIndex().associateBy({ it.value }, { it.index })

        init {
            hasNext = result.next()
        }

        override fun createResultRow(): ResultRow = ResultRow.create(result, fieldIndex)
    }
}
