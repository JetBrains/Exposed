package org.jetbrains.exposed.v1.sql.statements

import org.jetbrains.exposed.v1.sql.Key
import org.jetbrains.exposed.v1.sql.Transaction
import org.jetbrains.exposed.v1.sql.statements.api.PreparedStatementApi

/**
 * Represents the processes that should be performed during a statement's lifecycle events in a transaction.
 *
 * In general, statement execution flow works in the following way:
 * 1) [beforeExecution] of the statement
 * 2) Creation of the prepared statement
 * 3) [afterStatementPrepared] using the prepared statement from step 2
 * 4) Execution of the SQL query
 * 5) [afterExecution]
 */
interface StatementInterceptor {
    /** Performs steps before a statement, from the provided [context], is executed in a [transaction]. */
    fun beforeExecution(transaction: Transaction, context: StatementContext) {}

    /**
     * Performs steps after [preparedStatement] has been created in a [transaction], but before the statement
     * has been executed.
     **/
    fun afterStatementPrepared(transaction: Transaction, preparedStatement: PreparedStatementApi) {}

    /** Performs steps after an [executedStatement], from the provided [contexts], is complete in [transaction]. */
    fun afterExecution(transaction: Transaction, contexts: List<StatementContext>, executedStatement: PreparedStatementApi) {}

    /** Performs steps before a [transaction] is committed. */
    fun beforeCommit(transaction: Transaction) {}

    /** Performs steps after a [transaction] is committed. */
    fun afterCommit(transaction: Transaction) {}

    /** Performs steps before a rollback operation is issued on a [transaction]. */
    fun beforeRollback(transaction: Transaction) {}

    /** Performs steps after a rollback operation is issued on a [transaction]. */
    fun afterRollback(transaction: Transaction) {}

    /**
     * Returns a mapping of [userData] that ensures required information is not lost from the transaction scope
     * once the transaction is committed.
     */
    fun keepUserDataInTransactionStoreOnCommit(userData: Map<Key<*>, Any?>): Map<Key<*>, Any?> = emptyMap()
}

/** Represents a [StatementInterceptor] that is loaded whenever a [Transaction] instance is initialized. */
interface GlobalStatementInterceptor : StatementInterceptor
