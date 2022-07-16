package org.jetbrains.exposed.sql.statements

import org.jetbrains.exposed.sql.Key
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.statements.api.PreparedStatementApi

interface StatementInterceptor {
    fun beforeExecution(transaction: Transaction, context: StatementContext) {}
    fun afterExecution(transaction: Transaction, contexts: List<StatementContext>, executedStatement: PreparedStatementApi) {}

    fun beforeCommit(transaction: Transaction) { beforeCommit() }

    @Deprecated("using beforeCommit with transaction")
    //@Deprecated("using beforeCommit with transaction", level = DeprecationLevel.ERROR) \\ next version
    //@Deprecated("using beforeCommit with transaction", level = DeprecationLevel.HIDDEN) \\ next version, backward compatibility
    fun beforeCommit() {}
    fun afterCommit(transaction: Transaction) { afterCommit() }

    @Deprecated("using afterCommit with transaction")
    //@Deprecated("using afterRollback with transaction", level = DeprecationLevel.ERROR) \\ next version
    //@Deprecated("using afterRollback with transaction", level = DeprecationLevel.HIDDEN) \\ next version, backward compatibility
    fun afterCommit() {}

    fun beforeRollback(transaction: Transaction) {}
    fun afterRollback(transaction: Transaction) {}

    fun keepUserDataInTransactionStoreOnCommit(userData: Map<Key<*>, Any?>): Map<Key<*>, Any?> = emptyMap()
}

interface GlobalStatementInterceptor : StatementInterceptor
