package org.jetbrains.exposed.r2dbc.sql.statements

import org.jetbrains.exposed.r2dbc.sql.R2dbcTransaction
import org.jetbrains.exposed.r2dbc.sql.statements.api.R2dbcPreparedStatementApi
import org.jetbrains.exposed.sql.statements.BatchUpdateStatement

open class BatchUpdateSuspendExecutable(
    override val statement: BatchUpdateStatement
) : UpdateSuspendExecutable(statement) {
    override suspend fun R2dbcPreparedStatementApi.executeInternal(transaction: R2dbcTransaction): Int {
        return if (this@BatchUpdateSuspendExecutable.statement.data.size == 1) executeUpdate() else executeBatch().sum()
    }
}
