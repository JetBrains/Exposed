package org.jetbrains.exposed.sql.statements

import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.statements.api.PreparedStatementApi
import org.jetbrains.exposed.sql.transactions.ITransaction

interface StatementInterceptor {
    fun beforeExecution(transaction: ITransaction, context: StatementContext) {}
    fun afterExecution(transaction: ITransaction, contexts: List<StatementContext>, executedStatement: PreparedStatementApi) {}

    fun beforeCommit(transaction: ITransaction) {}
    fun afterCommit() {}

    fun beforeRollback(transaction: ITransaction) {}
    fun afterRollback() {}
}

interface GlobalStatementInterceptor : StatementInterceptor