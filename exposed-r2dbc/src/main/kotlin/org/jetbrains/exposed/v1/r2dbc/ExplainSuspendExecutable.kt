package org.jetbrains.exposed.v1.r2dbc

import io.r2dbc.spi.R2dbcException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import org.jetbrains.exposed.v1.core.ExplainQuery
import org.jetbrains.exposed.v1.core.ExplainResultRow
import org.jetbrains.exposed.v1.core.statements.Statement
import org.jetbrains.exposed.v1.core.statements.StatementBuilder
import org.jetbrains.exposed.v1.core.statements.api.ResultApi
import org.jetbrains.exposed.v1.core.statements.buildStatement
import org.jetbrains.exposed.v1.r2dbc.statements.SuspendExecutable
import org.jetbrains.exposed.v1.r2dbc.statements.api.R2dbcPreparedStatementApi
import org.jetbrains.exposed.v1.r2dbc.statements.api.R2dbcResult
import org.jetbrains.exposed.v1.r2dbc.statements.api.metadata
import org.jetbrains.exposed.v1.r2dbc.transactions.TransactionManager

/**
 * Represents the execution logic for an SQL statement that obtains information about a statement execution plan.
 */
open class ExplainSuspendExecutable(
    override val statement: ExplainQuery
) : SuspendExecutable<ResultApi, ExplainQuery>, Flow<ExplainResultRow> {
    override suspend fun R2dbcPreparedStatementApi.executeInternal(transaction: R2dbcTransaction): R2dbcResult = executeQuery()

    override suspend fun collect(collector: FlowCollector<ExplainResultRow>) {
        val rs = TransactionManager.current().exec(this)!! as R2dbcResult
        var fieldIndex: Map<String, Int>? = null

        try {
            rs.mapRows { row ->
                if (fieldIndex == null) {
                    fieldIndex = row.metadata.columnMetadatas.withIndex().associate { it.value.name to it.index }
                }

                ExplainResultRow.create(row, fieldIndex!!)
            }.collect { rr -> rr?.let { collector.emit(it) } }
        } catch (cause: R2dbcException) {
            throw ExposedR2dbcException(cause, statement.getContexts(), TransactionManager.current())
        }
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
 * @sample org.jetbrains.exposed.v1.r2dbc.sql.tests.shared.dml.ExplainTests.testExplainWithStatementsNotExecuted
 */
fun R2dbcTransaction.explain(
    analyze: Boolean = false,
    options: String? = null,
    body: StatementBuilder.() -> Statement<*>
): ExplainSuspendExecutable {
    val stmt = ExplainQuery(analyze, options, buildStatement(body))
    return ExplainSuspendExecutable(stmt)
}
