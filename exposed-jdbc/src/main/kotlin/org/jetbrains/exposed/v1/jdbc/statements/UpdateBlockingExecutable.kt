package org.jetbrains.exposed.v1.jdbc.statements

import org.jetbrains.exposed.v1.core.InternalApi
import org.jetbrains.exposed.v1.core.statements.UpdateStatement
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.statements.api.JdbcPreparedStatementApi

/**
 * Represents the execution logic for an SQL statement that updates rows of a table.
 */
open class UpdateBlockingExecutable(
    override val statement: UpdateStatement
) : BlockingExecutable<Int, UpdateStatement> {
    override fun JdbcPreparedStatementApi.executeInternal(transaction: JdbcTransaction): Int {
        @OptIn(InternalApi::class)
        if (statement.values.isEmpty()) return 0
        return executeUpdate()
    }
}
