package org.jetbrains.exposed.v1.sql.statements

import org.jetbrains.exposed.v1.sql.JdbcTransaction
import org.jetbrains.exposed.v1.sql.statements.api.JdbcPreparedStatementApi

// TODO KDocs should be added
open class InsertSelectBlockingExecutable(
    override val statement: InsertSelectStatement
) : BlockingExecutable<Int, InsertSelectStatement> {
    override fun JdbcPreparedStatementApi.executeInternal(transaction: JdbcTransaction): Int? = executeUpdate()
}
