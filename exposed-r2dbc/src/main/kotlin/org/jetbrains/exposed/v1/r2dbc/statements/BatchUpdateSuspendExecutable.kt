package org.jetbrains.exposed.v1.r2dbc.statements

import kotlinx.coroutines.flow.reduce
import org.jetbrains.exposed.v1.core.statements.BatchUpdateStatement
import org.jetbrains.exposed.v1.r2dbc.R2dbcTransaction
import org.jetbrains.exposed.v1.r2dbc.statements.api.R2dbcPreparedStatementApi

/**
 * Represents the execution logic for an SQL statement that batch updates rows of a table.
 */
open class BatchUpdateSuspendExecutable(
    override val statement: BatchUpdateStatement
) : UpdateSuspendExecutable(statement) {
    override suspend fun R2dbcPreparedStatementApi.executeInternal(transaction: R2dbcTransaction): Int {
        if (this@BatchUpdateSuspendExecutable.statement.data.size == 1) executeUpdate() else executeBatch().sum()

        return try {
            this.getResultRow()?.rowsUpdated()?.reduce(Int::plus) ?: 0
        } catch (_: NoSuchElementException) { // flow might be empty
            0
        }
    }
}
