package org.jetbrains.exposed.r2dbc.sql.statements

import kotlinx.coroutines.flow.collect
import org.jetbrains.exposed.r2dbc.sql.R2dbcTransaction
import org.jetbrains.exposed.r2dbc.sql.statements.api.R2dbcPreparedStatementApi
import org.jetbrains.exposed.r2dbc.sql.statements.api.R2dbcResult
import org.jetbrains.exposed.sql.InternalApi
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.statements.BaseBatchInsertStatement
import org.jetbrains.exposed.sql.statements.BatchInsertStatement
import org.jetbrains.exposed.sql.statements.SQLServerBatchInsertStatement

open class BatchInsertSuspendExecutable<S : BaseBatchInsertStatement>(
    override val statement: S
) : InsertSuspendExecutable<List<ResultRow>, S>(statement) {
    override val isAlwaysBatch = true

    override suspend fun prepared(transaction: R2dbcTransaction, sql: String): R2dbcPreparedStatementApi {
        return if (!statement.shouldReturnGeneratedValues) {
            transaction.connection.prepareStatement(sql, false)
        } else {
            super.prepared(transaction, sql)
        }
    }
}

open class SQLServerBatchInsertSuspendExecutable(
    override val statement: SQLServerBatchInsertStatement
) : BatchInsertSuspendExecutable<SQLServerBatchInsertStatement>(statement) {
    override val isAlwaysBatch: Boolean = false

    override suspend fun R2dbcPreparedStatementApi.execInsertFunction(): Pair<Int, R2dbcResult?> {
        @OptIn(InternalApi::class)
        val rs = if (statement.columnToReturnValue != null) {
            executeQuery()
        } else {
            executeUpdate()
            // since no result will be processed in this case, must apply a terminal operator to collect the flow
            getResultRow()?.mapRows { }?.collect()
            null
        }
        return statement.arguments!!.size to rs
    }
}

@Suppress("Unchecked_Cast")
internal fun <S : BatchInsertStatement> S.executable(): BatchInsertSuspendExecutable<S> {
    return when (this) {
        is SQLServerBatchInsertStatement -> SQLServerBatchInsertSuspendExecutable(this)
        else -> BatchInsertSuspendExecutable(this)
    } as BatchInsertSuspendExecutable<S>
}
