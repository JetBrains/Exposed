package org.jetbrains.exposed.v1.r2dbc.statements

import io.r2dbc.spi.R2dbcException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import org.jetbrains.exposed.v1.core.InternalApi
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.statements.ReturningStatement
import org.jetbrains.exposed.v1.core.statements.api.ResultApi
import org.jetbrains.exposed.v1.r2dbc.ExposedR2dbcException
import org.jetbrains.exposed.v1.r2dbc.R2dbcTransaction
import org.jetbrains.exposed.v1.r2dbc.getContexts
import org.jetbrains.exposed.v1.r2dbc.statements.api.R2dbcPreparedStatementApi
import org.jetbrains.exposed.v1.r2dbc.statements.api.R2dbcResult
import org.jetbrains.exposed.v1.r2dbc.transactions.TransactionManager

/**
 * Represents the execution logic for an underlying SQL statement that also returns a result with data from any modified rows.
 */
open class ReturningSuspendExecutable(
    override val statement: ReturningStatement
) : SuspendExecutable<ResultApi, ReturningStatement>, Flow<ResultRow> {
    override suspend fun R2dbcPreparedStatementApi.executeInternal(transaction: R2dbcTransaction): R2dbcResult = executeQuery()

    @OptIn(InternalApi::class)
    override suspend fun collect(collector: FlowCollector<ResultRow>) {
        val fieldIndex = statement.returningExpressions.withIndex()
            .associateBy({ it.value }, { it.index })
        // Row-invariant for this result set, so build it once and share across every row.
        val columnTypes = ResultRow.columnTypesOf(fieldIndex)
        val rs = TransactionManager.current().execQuery(this)
        try {
            rs.mapRows {
                ResultRow.create(it, fieldIndex, columnTypes)
            }.collect { rr -> rr?.let { collector.emit(it) } }
        } catch (cause: R2dbcException) {
            throw ExposedR2dbcException(cause, statement.getContexts(), TransactionManager.current())
        }
    }
}
