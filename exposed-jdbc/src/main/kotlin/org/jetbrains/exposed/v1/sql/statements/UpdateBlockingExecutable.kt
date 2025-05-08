package org.jetbrains.exposed.v1.sql.statements

import org.jetbrains.exposed.v1.sql.InternalApi
import org.jetbrains.exposed.v1.sql.JdbcTransaction
import org.jetbrains.exposed.v1.sql.statements.api.JdbcPreparedStatementApi

// TODO KDocs should be added
open class UpdateBlockingExecutable(
    override val statement: UpdateStatement
) : BlockingExecutable<Int, UpdateStatement> {
    override fun JdbcPreparedStatementApi.executeInternal(transaction: JdbcTransaction): Int {
        @OptIn(InternalApi::class)
        if (statement.values.isEmpty()) return 0
        return executeUpdate()
    }
}
