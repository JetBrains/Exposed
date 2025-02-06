package org.jetbrains.exposed.sql.statements

import org.jetbrains.exposed.sql.InternalApi
import org.jetbrains.exposed.sql.R2dbcTransaction
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.statements.api.R2dbcPreparedStatementApi
import org.jetbrains.exposed.sql.statements.r2dbc.R2dbcResult

abstract class BaseBatchInsertExecutable<S : BaseBatchInsertStatement>(
    override val statement: S
) : InsertExecutable<List<ResultRow>, S>(statement) {
    override val isAlwaysBatch = true

    override suspend fun prepared(transaction: R2dbcTransaction, sql: String): R2dbcPreparedStatementApi {
        return if (!statement.shouldReturnGeneratedValues) {
            transaction.connection.prepareStatement(sql, false)
        } else {
            super.prepared(transaction, sql)
        }
    }
}

open class BatchInsertExecutable(
    override val statement: BatchInsertStatement
) : BaseBatchInsertExecutable<BatchInsertStatement>(statement)

open class SQLServerBatchInsertExecutable(
    override val statement: SQLServerBatchInsertStatement
) : BaseBatchInsertExecutable<SQLServerBatchInsertStatement>(statement) {
    override val isAlwaysBatch: Boolean = false

    override suspend fun R2dbcPreparedStatementApi.execInsertFunction(): Pair<Int, R2dbcResult?> {
        @OptIn(InternalApi::class)
        val rs = if (statement.columnToReturnValue != null) {
            executeQuery()
        } else {
            executeUpdate()
            null
        }
        return statement.arguments!!.size to rs
    }
}

@Suppress("Unchecked_Cast")
internal fun <S : BatchInsertStatement> S.executable(): BaseBatchInsertExecutable<S> {
    return when (this) {
        is SQLServerBatchInsertStatement -> SQLServerBatchInsertExecutable(this)
        else -> BatchInsertExecutable(this)
    } as BaseBatchInsertExecutable<S>
}
