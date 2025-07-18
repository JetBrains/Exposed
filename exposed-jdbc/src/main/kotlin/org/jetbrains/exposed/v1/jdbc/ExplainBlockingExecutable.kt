package org.jetbrains.exposed.v1.jdbc

import org.jetbrains.exposed.v1.core.ExplainQuery
import org.jetbrains.exposed.v1.core.ExplainResultRow
import org.jetbrains.exposed.v1.core.statements.Statement
import org.jetbrains.exposed.v1.core.statements.StatementBuilder
import org.jetbrains.exposed.v1.core.statements.api.ResultApi
import org.jetbrains.exposed.v1.core.statements.buildStatement
import org.jetbrains.exposed.v1.jdbc.statements.BlockingExecutable
import org.jetbrains.exposed.v1.jdbc.statements.StatementIterator
import org.jetbrains.exposed.v1.jdbc.statements.api.JdbcPreparedStatementApi
import org.jetbrains.exposed.v1.jdbc.statements.jdbc.JdbcResult
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager
import java.sql.ResultSet

/**
 * Represents the execution logic for an SQL statement that obtains information about a statement execution plan.
 */
open class ExplainBlockingExecutable(
    override val statement: ExplainQuery
) : BlockingExecutable<ResultApi, ExplainQuery>, Iterable<ExplainResultRow> {
    override fun JdbcPreparedStatementApi.executeInternal(transaction: JdbcTransaction): JdbcResult = executeQuery()

    override fun iterator(): Iterator<ExplainResultRow> {
        val rs = TransactionManager.current().exec(this)!! as JdbcResult
        val resultIterator = ResultIterator(rs.result)
        return Iterable { resultIterator }.iterator()
    }

    private inner class ResultIterator(rs: ResultSet) : StatementIterator<String, ExplainResultRow>(rs) {
        override val fieldIndex: Map<String, Int> = List(result.metaData.columnCount) { i ->
            result.metaData.getColumnName(i + 1) to i
        }.toMap()

        init {
            hasNext = result.next()
        }

        override fun createResultRow(): ExplainResultRow = ExplainResultRow.create(JdbcResult(result), fieldIndex)
    }
}

/**
 * Creates an [ExplainQuery] using the `EXPLAIN` keyword, which obtains information about a statement execution plan.
 *
 * **Note:** This operation is not supported by all vendors, please check the documentation.
 *
 * @param analyze (optional) Whether the statement whose execution plan is being queried should actually be executed as well.
 * **Note:** The `ANALYZE` parameter is not supported by all vendors, please check the documentation.
 * @param options (optional) String of comma-separated parameters to append after the `EXPLAIN` keyword.
 * **Note:** Optional parameters are not supported by all vendors, please check the documentation.
 * @param body The statement for which an execution plan should be queried. This can be a `SELECT`, `INSERT`,
 * `REPLACE`, `UPDATE` or `DELETE` statement.
 * @sample org.jetbrains.exposed.v1.tests.shared.dml.ExplainTests.testExplainWithStatementsNotExecuted
 */
fun JdbcTransaction.explain(
    analyze: Boolean = false,
    options: String? = null,
    body: StatementBuilder.() -> Statement<*>
): ExplainBlockingExecutable {
    val stmt = ExplainQuery(analyze, options, buildStatement(body))
    return ExplainBlockingExecutable(stmt)
}
