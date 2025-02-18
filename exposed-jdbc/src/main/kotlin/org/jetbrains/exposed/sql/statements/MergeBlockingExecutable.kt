package org.jetbrains.exposed.sql.statements

import org.jetbrains.exposed.sql.JdbcTransaction
import org.jetbrains.exposed.sql.statements.api.JdbcPreparedStatementApi

open class MergeBlockingExecutable<S : MergeStatement>(
    override val statement: S
) : BlockingExecutable<Int, S> {
    override fun JdbcPreparedStatementApi.executeInternal(transaction: JdbcTransaction): Int? {
        return executeUpdate()
    }
}
