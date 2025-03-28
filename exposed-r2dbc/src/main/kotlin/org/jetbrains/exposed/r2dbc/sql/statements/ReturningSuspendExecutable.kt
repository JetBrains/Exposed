package org.jetbrains.exposed.r2dbc.sql.statements

import io.r2dbc.spi.R2dbcException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import org.jetbrains.exposed.r2dbc.exceptions.ExposedR2dbcException
import org.jetbrains.exposed.r2dbc.exceptions.getContexts
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
        val rs = TransactionManager.current().exec(this)!!
        try {
            rs.mapRows { ResultRow.create(it, fieldIndex) }.collect(collector)
        } catch (cause: R2dbcException) {
            throw ExposedR2dbcException(cause, statement.getContexts(), TransactionManager.current())
        }
    }
}
