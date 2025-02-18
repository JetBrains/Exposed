package org.jetbrains.exposed.sql.statements

import org.jetbrains.exposed.sql.Expression
import org.jetbrains.exposed.sql.JdbcTransaction
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.statements.api.JdbcPreparedStatementApi
import org.jetbrains.exposed.sql.statements.api.ResultApi
import org.jetbrains.exposed.sql.statements.jdbc.JdbcResult
import org.jetbrains.exposed.sql.transactions.TransactionManager
import java.sql.ResultSet

open class ReturningBlockingExecutable(
    override val statement: ReturningStatement
) : BlockingExecutable<ResultApi, ReturningStatement>, Iterable<ResultRow> {
    override fun JdbcPreparedStatementApi.executeInternal(transaction: JdbcTransaction): JdbcResult = executeQuery()

    override fun iterator(): Iterator<ResultRow> {
        val rs = TransactionManager.current().exec(this)!! as JdbcResult
        val resultIterator = ResultIterator(rs.result)
        return Iterable { resultIterator }.iterator()
    }

    private inner class ResultIterator(rs: ResultSet) : StatementIterator<Expression<*>, ResultRow>(rs) {
        override val fieldIndex = statement.returningExpressions.withIndex()
            .associateBy({ it.value }, { it.index })

        init {
            hasNext = result.next()
        }

        override fun createResultRow(): ResultRow = ResultRow.create(JdbcResult(result), fieldIndex)
    }
}
