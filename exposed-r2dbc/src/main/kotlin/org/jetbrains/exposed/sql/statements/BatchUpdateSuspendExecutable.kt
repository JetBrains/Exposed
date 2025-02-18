package org.jetbrains.exposed.sql.statements

import org.jetbrains.exposed.sql.R2dbcTransaction
import org.jetbrains.exposed.sql.statements.api.R2dbcPreparedStatementApi

open class BatchUpdateSuspendExecutable(
    override val statement: BatchUpdateStatement
) : UpdateSuspendExecutable(statement) {
    override suspend fun R2dbcPreparedStatementApi.executeInternal(transaction: R2dbcTransaction): Int {
        return if (this@BatchUpdateSuspendExecutable.statement.data.size == 1) executeUpdate() else executeBatch().sum()
    }
}
