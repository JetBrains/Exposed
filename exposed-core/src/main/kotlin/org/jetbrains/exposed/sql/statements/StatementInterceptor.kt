package org.jetbrains.exposed.sql.statements

import org.jetbrains.exposed.sql.Key
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.statements.api.PreparedStatementApi

interface StatementInterceptor {
    fun beforeExecution(transaction: Transaction, context: StatementContext) {}
    fun afterExecution(transaction: Transaction, contexts: List<StatementContext>, executedStatement: PreparedStatementApi) {}

    fun beforeCommit(transaction: Transaction) {}

    @Deprecated("using afterCommit with transaction")
    // @Deprecated("using afterCommit with transaction", level = DeprecationLevel.ERROR) \\ next version
    // @Deprecated("using afterCommit with transaction", level = DeprecationLevel.HIDDEN) \\ next version, backward compatibility
    fun afterCommit() {}
    fun afterCommit(transaction: Transaction) { afterCommit() }

    fun beforeRollback(transaction: Transaction) {}

    @Deprecated("using afterRollback with transaction")
    // @Deprecated("using afterRollback with transaction", level = DeprecationLevel.ERROR) \\ next version
    // @Deprecated("using afterRollback with transaction", level = DeprecationLevel.HIDDEN) \\ next version, backward compatibility
    fun afterRollback() {}
    fun afterRollback(transaction: Transaction) { afterRollback() }

    fun keepUserDataInTransactionStoreOnCommit(userData: Map<Key<*>, Any?>): Map<Key<*>, Any?> = emptyMap()
}

interface GlobalStatementInterceptor : StatementInterceptor
