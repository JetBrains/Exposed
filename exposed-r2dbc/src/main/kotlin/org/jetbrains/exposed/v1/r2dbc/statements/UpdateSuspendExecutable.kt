package org.jetbrains.exposed.v1.r2dbc.statements

import kotlinx.coroutines.flow.singleOrNull
import org.jetbrains.exposed.v1.core.InternalApi
import org.jetbrains.exposed.v1.core.statements.UpdateStatement
import org.jetbrains.exposed.v1.r2dbc.R2dbcTransaction
import org.jetbrains.exposed.v1.r2dbc.statements.api.R2dbcPreparedStatementApi

/**
 * Represents the execution logic for an SQL statement that updates rows of a table.
 */
open class UpdateSuspendExecutable(
    override val statement: UpdateStatement
) : SuspendExecutable<Int, UpdateStatement> {
    override suspend fun R2dbcPreparedStatementApi.executeInternal(transaction: R2dbcTransaction): Int {
        @OptIn(InternalApi::class)
        if (statement.values.isEmpty()) return 0
        executeUpdate()

        return this.getResultRow()?.rowsUpdated()?.singleOrNull() ?: 0
    }
}
