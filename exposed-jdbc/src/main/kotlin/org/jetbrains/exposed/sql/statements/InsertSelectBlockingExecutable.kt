package org.jetbrains.exposed.sql.statements

import org.jetbrains.exposed.sql.JdbcTransaction
import org.jetbrains.exposed.sql.statements.api.JdbcPreparedStatementApi

open class InsertSelectBlockingExecutable(
    override val statement: InsertSelectStatement
) : BlockingExecutable<Int, InsertSelectStatement> {
    override fun JdbcPreparedStatementApi.executeInternal(transaction: JdbcTransaction): Int? = executeUpdate()
}
