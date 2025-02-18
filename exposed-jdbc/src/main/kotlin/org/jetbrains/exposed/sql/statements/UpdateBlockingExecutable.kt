package org.jetbrains.exposed.sql.statements

import org.jetbrains.exposed.sql.InternalApi
import org.jetbrains.exposed.sql.JdbcTransaction
import org.jetbrains.exposed.sql.statements.api.JdbcPreparedStatementApi

open class UpdateBlockingExecutable(
    override val statement: UpdateStatement
) : BlockingExecutable<Int, UpdateStatement> {
    override fun JdbcPreparedStatementApi.executeInternal(transaction: JdbcTransaction): Int {
        @OptIn(InternalApi::class)
        if (statement.values.isEmpty()) return 0
        return executeUpdate()
    }
}
