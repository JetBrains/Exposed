package org.jetbrains.exposed.v1.jdbc.statements

import org.jetbrains.exposed.v1.core.statements.InsertSelectStatement
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.statements.api.JdbcPreparedStatementApi

// TODO KDocs should be added
open class InsertSelectBlockingExecutable(
    override val statement: org.jetbrains.exposed.v1.core.statements.InsertSelectStatement
) : BlockingExecutable<Int, InsertSelectStatement> {
    override fun JdbcPreparedStatementApi.executeInternal(transaction: JdbcTransaction): Int? = executeUpdate()
}
