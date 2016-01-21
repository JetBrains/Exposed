package org.jetbrains.exposed.sql.statements

import org.jetbrains.exposed.sql.*
import java.sql.PreparedStatement

interface StatementInterceptor {
    fun beforeExecution(transaction: Transaction, context: StatementContext)
    fun afterExecution(transaction: Transaction, contexts: List<StatementContext>, executedStatement: PreparedStatement)
}

class StatementMonitor {
    private val interceptors: MutableList<StatementInterceptor> = arrayListOf()

    fun register(interceptor: StatementInterceptor) = interceptors.add(interceptor)
    fun unregister(interceptor: StatementInterceptor) = interceptors.remove(interceptor)

    fun notifyBeforeExecution(transaction: Transaction, context: StatementContext) {
        interceptors.forEach { it.beforeExecution(transaction, context) }
    }

    fun notifyAfterExecution(transaction: Transaction, executedContexts: List<StatementContext>, executedStatement: PreparedStatement) {
        interceptors.forEach { it.afterExecution(transaction, executedContexts, executedStatement) }
    }
}