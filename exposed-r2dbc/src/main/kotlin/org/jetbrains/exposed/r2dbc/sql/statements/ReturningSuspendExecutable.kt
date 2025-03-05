package org.jetbrains.exposed.r2dbc.sql.statements

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.single
import org.jetbrains.exposed.r2dbc.sql.R2dbcTransaction
import org.jetbrains.exposed.r2dbc.sql.statements.api.R2dbcPreparedStatementApi
import org.jetbrains.exposed.r2dbc.sql.statements.api.R2dbcResult
import org.jetbrains.exposed.r2dbc.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.statements.ReturningStatement
import org.jetbrains.exposed.sql.statements.api.ResultApi

open class ReturningSuspendExecutable(
    override val statement: ReturningStatement
) : SuspendExecutable<ResultApi, ReturningStatement>, Flow<ResultRow> {
    override suspend fun R2dbcPreparedStatementApi.executeInternal(transaction: R2dbcTransaction): R2dbcResult = executeQuery()

    override suspend fun collect(collector: FlowCollector<ResultRow>) {
        val fieldIndex = statement.returningExpressions.withIndex()
            .associateBy({ it.value }, { it.index })
        val rs = TransactionManager.current().exec(this)!! as R2dbcResult
        rs.use {
            collector.emit(ResultRow.create(it.rows().single(), fieldIndex))
        }
    }
}
