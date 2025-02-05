package org.jetbrains.exposed.sql.statements

import org.jetbrains.exposed.sql.JdbcTransaction
import org.jetbrains.exposed.sql.statements.api.JdbcPreparedStatementApi

open class InsertSelectExecutable(
    override val statement: InsertSelectStatement
) : Executable<Int, InsertSelectStatement> {
    override fun JdbcPreparedStatementApi.executeInternal(transaction: JdbcTransaction): Int? = executeUpdate()
}
