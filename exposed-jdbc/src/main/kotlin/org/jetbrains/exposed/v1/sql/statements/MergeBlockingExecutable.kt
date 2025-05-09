package org.jetbrains.exposed.v1.sql.statements

import org.jetbrains.exposed.v1.core.statements.MergeStatement
import org.jetbrains.exposed.v1.sql.JdbcTransaction
import org.jetbrains.exposed.v1.sql.statements.api.JdbcPreparedStatementApi

// TODO KDocs should be added
open class MergeBlockingExecutable<S : MergeStatement>(
    override val statement: S
) : BlockingExecutable<Int, S> {
    override fun JdbcPreparedStatementApi.executeInternal(transaction: JdbcTransaction): Int? {
        return executeUpdate()
    }
}
