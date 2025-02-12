package org.jetbrains.exposed.sql.statements

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import org.jetbrains.exposed.sql.R2dbcTransaction
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.statements.api.R2dbcPreparedStatementApi
import org.jetbrains.exposed.sql.statements.api.ResultApi
import org.jetbrains.exposed.sql.statements.r2dbc.R2dbcResult
import org.jetbrains.exposed.sql.transactions.TransactionManager

open class ReturningExecutable(
    override val statement: ReturningStatement
) : Executable<ResultApi, ReturningStatement>, Flow<ResultRow> {
    override suspend fun R2dbcPreparedStatementApi.executeInternal(transaction: R2dbcTransaction): R2dbcResult = executeQuery()

    override suspend fun collect(collector: FlowCollector<ResultRow>) {
        val fieldIndex = statement.returningExpressions.withIndex()
            .associateBy({ it.value }, { it.index })
        val rs = TransactionManager.current().exec(this)!! as R2dbcResult

        collector.emit(ResultRow.create(rs, fieldIndex))
    }
}
