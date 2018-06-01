package org.jetbrains.exposed.sql.statements

import org.jetbrains.exposed.sql.*
import java.sql.PreparedStatement

interface StatementInterceptor {
    fun beforeExecution(transaction: Transaction, context: StatementContext) {}
    fun afterExecution(transaction: Transaction, contexts: List<StatementContext>, executedStatement: PreparedStatement) {}

    fun beforeCommit(transaction: Transaction) {}
    fun afterCommit() {}

    fun beforeRollback(transaction: Transaction) {}
    fun afterRollback() {}
}