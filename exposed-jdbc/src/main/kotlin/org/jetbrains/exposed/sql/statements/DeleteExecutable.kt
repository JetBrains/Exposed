package org.jetbrains.exposed.sql.statements

import org.jetbrains.exposed.sql.JdbcTransaction
import org.jetbrains.exposed.sql.statements.api.JdbcPreparedStatementApi

open class DeleteExecutable(
    override val statement: DeleteStatement
) : Executable<Int, DeleteStatement> {
    override fun JdbcPreparedStatementApi.executeInternal(transaction: JdbcTransaction): Int {
        return executeUpdate()
    }
}
