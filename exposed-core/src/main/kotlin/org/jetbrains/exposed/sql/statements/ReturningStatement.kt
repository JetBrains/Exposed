package org.jetbrains.exposed.sql.statements

import org.jetbrains.exposed.sql.Expression
import org.jetbrains.exposed.sql.IColumnType
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.statements.api.PreparedStatementApi
import java.sql.ResultSet

/**
 * Represents the underlying SQL [mainStatement] that also returns a result set with data from any modified rows.
 *
 * @param table Table to perform the main statement on and return results from.
 * @param returningColumns Columns or expressions to include in the returned result set.
 * @param mainStatement The statement to append the RETURNING clause to. This may be an insert, update, or delete statement.
 */
open class ReturningStatement(
    val table: Table,
    val returningExpressions: List<Expression<*>>,
    val mainStatement: Statement<*>
) : Statement<List<ResultRow>>(mainStatement.type, listOf(table)) {
    override fun prepareSQL(transaction: Transaction, prepared: Boolean): String {
        val mainSql = mainStatement.prepareSQL(transaction, prepared)
        return transaction.db.dialect.functionProvider.returning(mainSql, returningExpressions, transaction)
    }

    override fun arguments(): Iterable<Iterable<Pair<IColumnType<*>, Any?>>> = mainStatement.arguments()

    override fun PreparedStatementApi.executeInternal(transaction: Transaction): List<ResultRow> {
        val resultSet = executeQuery()
        return processResults(resultSet, transaction)
    }

    private fun processResults(rs: ResultSet, transaction: Transaction): List<ResultRow> {
        val fieldIndex = returningExpressions.withIndex().associateBy({ it.value }, { it.index })

        val results = mutableListOf<ResultRow>()
        while (rs.next()) {
            results.add(ResultRow.create(rs, fieldIndex))
        }

        rs.statement?.close()
        transaction.openResultSetsCount.dec().coerceAtLeast(0)
        return results
    }
}
