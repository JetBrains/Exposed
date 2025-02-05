package org.jetbrains.exposed.sql.statements

import org.jetbrains.exposed.sql.JdbcTransaction
import org.jetbrains.exposed.sql.statements.api.JdbcPreparedStatementApi

// TODO KDocs should be added
open class DeleteBlockingExecutable(
    override val statement: DeleteStatement
) : BlockingExecutable<Int, DeleteStatement> {
    override fun JdbcPreparedStatementApi.executeInternal(transaction: JdbcTransaction): Int {
        return executeUpdate()
    }
}
