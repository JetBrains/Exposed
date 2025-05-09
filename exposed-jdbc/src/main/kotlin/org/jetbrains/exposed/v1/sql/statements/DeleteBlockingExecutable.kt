package org.jetbrains.exposed.v1.sql.statements

import org.jetbrains.exposed.v1.core.statements.DeleteStatement
import org.jetbrains.exposed.v1.sql.JdbcTransaction
import org.jetbrains.exposed.v1.sql.statements.api.JdbcPreparedStatementApi

// TODO KDocs should be added
open class DeleteBlockingExecutable(
    override val statement: DeleteStatement
) : BlockingExecutable<Int, DeleteStatement> {
    override fun JdbcPreparedStatementApi.executeInternal(transaction: JdbcTransaction): Int {
        return executeUpdate()
    }
}
