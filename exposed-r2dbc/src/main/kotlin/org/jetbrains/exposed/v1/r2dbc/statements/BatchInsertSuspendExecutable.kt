package org.jetbrains.exposed.v1.r2dbc.statements

import org.jetbrains.exposed.v1.core.InternalApi
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.statements.BatchInsertStatement
import org.jetbrains.exposed.v1.core.statements.SQLServerBatchInsertStatement
import org.jetbrains.exposed.v1.r2dbc.R2dbcTransaction
import org.jetbrains.exposed.v1.r2dbc.statements.api.R2dbcPreparedStatementApi
import org.jetbrains.exposed.v1.r2dbc.statements.api.R2dbcResult

/**
 * Represents the execution logic for an SQL statement that batch inserts new rows into a table.
 */
open class BatchInsertSuspendExecutable<S : BatchInsertStatement>(
    override val statement: S
) : InsertSuspendExecutable<List<ResultRow>, S>(statement) {
    override val isAlwaysBatch = true

    override suspend fun prepared(transaction: R2dbcTransaction, sql: String): R2dbcPreparedStatementApi {
        return if (!statement.shouldReturnGeneratedValues) {
            transaction.connection().prepareStatement(sql, false)
        } else {
            super.prepared(transaction, sql)
        }
    }
}

/**
 * Represents the execution logic for an SQL statement that batch inserts new rows into a table,
 * specifically for the SQL Server database.
 */
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
            getResultRow()?.collect()
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
