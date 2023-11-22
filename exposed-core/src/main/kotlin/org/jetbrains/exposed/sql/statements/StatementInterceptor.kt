package org.jetbrains.exposed.sql.statements

import org.jetbrains.exposed.sql.Key
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.statements.api.PreparedStatementApi

/**
 * In general, statement execution flow works in a following way :
 * 1) beforeExecution
 * 2) PreparedStatement is created
 * 3) afterStatementPrepared with prepared statement, which was created at phase 2
 * 4) Execute SQL query
 * 5) afterExecution
 */

interface StatementInterceptor {
    fun beforeExecution(transaction: Transaction, context: StatementContext) {}
    fun afterStatementPrepared(transaction: Transaction, preparedStatement: PreparedStatementApi) {}
    fun afterExecution(transaction: Transaction, contexts: List<StatementContext>, executedStatement: PreparedStatementApi) {}

    fun beforeCommit(transaction: Transaction) {}

    fun afterCommit(transaction: Transaction) {}

    fun beforeRollback(transaction: Transaction) {}

    fun afterRollback(transaction: Transaction) {}

    fun keepUserDataInTransactionStoreOnCommit(userData: Map<Key<*>, Any?>): Map<Key<*>, Any?> = emptyMap()
}

interface GlobalStatementInterceptor : StatementInterceptor
