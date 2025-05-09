package org.jetbrains.exposed.v1.sql.statements

import org.jetbrains.exposed.v1.sql.JdbcTransaction
import org.jetbrains.exposed.v1.sql.statements.api.JdbcPreparedStatementApi

// TODO KDocs should be added
open class InsertSelectBlockingExecutable(
    override val statement: org.jetbrains.exposed.v1.core.statements.InsertSelectStatement
) : BlockingExecutable<Int, org.jetbrains.exposed.v1.core.statements.InsertSelectStatement> {
    override fun JdbcPreparedStatementApi.executeInternal(transaction: JdbcTransaction): Int? = executeUpdate()
}
