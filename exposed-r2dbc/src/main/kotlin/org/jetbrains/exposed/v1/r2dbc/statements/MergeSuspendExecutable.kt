package org.jetbrains.exposed.v1.r2dbc.statements

import kotlinx.coroutines.flow.reduce
import org.jetbrains.exposed.v1.core.statements.MergeStatement
import org.jetbrains.exposed.v1.r2dbc.R2dbcTransaction
import org.jetbrains.exposed.v1.r2dbc.statements.api.R2dbcPreparedStatementApi

/**
 * Represents the execution logic for an SQL MERGE statement that encapsulates the logic to perform conditional
 * updates, insertions, or deletions.
 */
open class MergeSuspendExecutable<S : MergeStatement>(
    override val statement: S
) : SuspendExecutable<Int, S> {
    override suspend fun R2dbcPreparedStatementApi.executeInternal(transaction: R2dbcTransaction): Int? {
        executeUpdate()
        return try {
            this.getResultRow()?.rowsUpdated()?.reduce(Int::plus) ?: 0
        } catch (_: NoSuchElementException) { // flow might be empty
            0
        }
    }
}
