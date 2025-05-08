package org.jetbrains.exposed.v1.sql.statements

import org.jetbrains.exposed.v1.sql.InternalApi
import org.jetbrains.exposed.v1.sql.JdbcTransaction
import org.jetbrains.exposed.v1.sql.ResultRow
import org.jetbrains.exposed.v1.sql.statements.api.JdbcPreparedStatementApi
import java.sql.ResultSet

// TODO KDocs should be added
open class BatchInsertBlockingExecutable<S : BaseBatchInsertStatement>(
    override val statement: S
) : InsertBlockingExecutable<List<ResultRow>, S>(statement) {
    override val isAlwaysBatch = true

    override fun prepared(transaction: JdbcTransaction, sql: String): JdbcPreparedStatementApi {
        return if (!statement.shouldReturnGeneratedValues) {
            transaction.connection.prepareStatement(sql, false)
        } else {
            super.prepared(transaction, sql)
        }
    }
}

open class SQLServerBatchInsertBlockingExecutable(
    override val statement: SQLServerBatchInsertStatement
) : BatchInsertBlockingExecutable<SQLServerBatchInsertStatement>(statement) {
    override val isAlwaysBatch: Boolean = false

    override fun JdbcPreparedStatementApi.execInsertFunction(): Pair<Int, ResultSet?> {
        @OptIn(InternalApi::class)
        val rs = if (statement.columnToReturnValue != null) {
            executeQuery()
        } else {
            executeUpdate()
            null
        }
        return statement.arguments!!.size to rs?.result
    }
}

@Suppress("Unchecked_Cast")
internal fun <S : BatchInsertStatement> S.executable(): BatchInsertBlockingExecutable<S> {
    return when (this) {
        is SQLServerBatchInsertStatement -> SQLServerBatchInsertBlockingExecutable(this)
        else -> BatchInsertBlockingExecutable(this)
    } as BatchInsertBlockingExecutable<S>
}
