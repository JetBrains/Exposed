package org.jetbrains.exposed.r2dbc.sql

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import org.jetbrains.exposed.r2dbc.sql.statements.SuspendExecutable
import org.jetbrains.exposed.r2dbc.sql.statements.api.R2dbcPreparedStatementApi
import org.jetbrains.exposed.r2dbc.sql.statements.api.R2dbcResult
import org.jetbrains.exposed.r2dbc.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.ExplainQuery
import org.jetbrains.exposed.sql.ExplainResultRow
import org.jetbrains.exposed.sql.statements.IStatementBuilder
import org.jetbrains.exposed.sql.statements.Statement
import org.jetbrains.exposed.sql.statements.api.ResultApi
import org.jetbrains.exposed.sql.statements.buildStatement

open class ExplainSuspendExecutable(
    override val statement: ExplainQuery
) : SuspendExecutable<ResultApi, ExplainQuery>, Flow<ExplainResultRow> {
    override suspend fun R2dbcPreparedStatementApi.executeInternal(transaction: R2dbcTransaction): R2dbcResult = executeQuery()

    override suspend fun collect(collector: FlowCollector<ExplainResultRow>) {
        val rs = TransactionManager.current().exec(this)!! as R2dbcResult
        val fieldIndex = mutableMapOf<String, Int>()

        rs.mapRows { ExplainResultRow.Companion.create(it, fieldIndex) }
            .collect(collector)
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
 * @sample org.jetbrains.exposed.sql.tests.shared.dml.ExplainTests.testExplainWithStatementsNotExecuted
 */
fun R2dbcTransaction.explain(
    analyze: Boolean = false,
    options: String? = null,
    body: IStatementBuilder.() -> Statement<*>
): ExplainSuspendExecutable {
    val stmt = ExplainQuery(analyze, options, buildStatement(body))
    return ExplainSuspendExecutable(stmt)
}
