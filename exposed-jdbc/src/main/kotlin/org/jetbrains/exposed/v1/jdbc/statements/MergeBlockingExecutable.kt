package org.jetbrains.exposed.v1.jdbc.statements

import org.jetbrains.exposed.v1.core.statements.MergeStatement
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.statements.api.JdbcPreparedStatementApi

/**
 * Represents the execution logic for an SQL MERGE statement that encapsulates the logic to perform conditional
 * updates, insertions, or deletions.
 */
open class MergeBlockingExecutable<S : MergeStatement>(
    override val statement: S
) : BlockingExecutable<Int, S> {
    override fun JdbcPreparedStatementApi.executeInternal(transaction: JdbcTransaction): Int? {
        return executeUpdate()
    }
}
