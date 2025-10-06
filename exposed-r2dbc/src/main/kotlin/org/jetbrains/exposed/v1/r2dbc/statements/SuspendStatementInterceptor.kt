package org.jetbrains.exposed.v1.r2dbc.statements

import org.jetbrains.exposed.v1.core.Key
import org.jetbrains.exposed.v1.core.statements.GlobalStatementInterceptor
import org.jetbrains.exposed.v1.core.statements.StatementContext
import org.jetbrains.exposed.v1.core.statements.StatementInterceptor
import org.jetbrains.exposed.v1.r2dbc.R2dbcTransaction
import org.jetbrains.exposed.v1.r2dbc.statements.api.R2dbcPreparedStatementApi

/**
 * Represents the processes that should be performed during a statement's lifecycle events in a suspend transaction.
 *
 * In general, statement execution flow works in the following way:
 * 1) [beforeExecution] of the statement
 * 2) Creation of the prepared statement
 * 3) [afterStatementPrepared] using the prepared statement from step 2
 * 4) Execution of the SQL query
 * 5) [afterExecution]
 */
interface SuspendStatementInterceptor {
    /** Performs steps before a statement, from the provided [context], is executed in a [transaction]. */
    suspend fun beforeExecution(transaction: R2dbcTransaction, context: StatementContext) {}

    /**
     * Performs steps after [preparedStatement] has been created in a [transaction], but before the statement
     * has been executed.
     **/
    suspend fun afterStatementPrepared(transaction: R2dbcTransaction, preparedStatement: R2dbcPreparedStatementApi) {}

    /** Performs steps after an [executedStatement], from the provided [contexts], is complete in [transaction]. */
    suspend fun afterExecution(
        transaction: R2dbcTransaction,
        contexts: List<StatementContext>,
        executedStatement: R2dbcPreparedStatementApi
    ) {}

    /** Performs steps before a [transaction] is committed. */
    suspend fun beforeCommit(transaction: R2dbcTransaction) {}

    /** Performs steps after a [transaction] is committed. */
    suspend fun afterCommit(transaction: R2dbcTransaction) {}

    /** Performs steps before a rollback operation is issued on a [transaction]. */
    suspend fun beforeRollback(transaction: R2dbcTransaction) {}

    /** Performs steps after a rollback operation is issued on a [transaction]. */
    suspend fun afterRollback(transaction: R2dbcTransaction) {}

    /**
     * Returns a mapping of [userData] that ensures required information is not lost from the transaction scope
     * once the transaction is committed.
     */
    fun keepUserDataInTransactionStoreOnCommit(userData: Map<Key<*>, Any?>): Map<Key<*>, Any?> = emptyMap()
}

/** Represents a [SuspendStatementInterceptor] that is loaded whenever a [R2dbcTransaction] instance is initialized. */
interface GlobalSuspendStatementInterceptor : SuspendStatementInterceptor

/**
 * Wrapper class to consolidate usage of core [StatementInterceptor] with R2DBC-specific [SuspendStatementInterceptor],
 * so they can be processed together using a single `R2dbcTransaction.interceptors` property.
 */
internal class StatementInterceptorWrapper(
    internal val originalInterceptor: StatementInterceptor
) : SuspendStatementInterceptor {
    override suspend fun beforeExecution(transaction: R2dbcTransaction, context: StatementContext) {
        originalInterceptor.beforeExecution(transaction, context)
    }

    override suspend fun afterStatementPrepared(
        transaction: R2dbcTransaction,
        preparedStatement: R2dbcPreparedStatementApi
    ) {
        originalInterceptor.afterStatementPrepared(transaction, preparedStatement)
    }

    override suspend fun afterExecution(
        transaction: R2dbcTransaction,
        contexts: List<StatementContext>,
        executedStatement: R2dbcPreparedStatementApi
    ) {
        originalInterceptor.afterExecution(transaction, contexts, executedStatement)
    }

    override suspend fun beforeCommit(transaction: R2dbcTransaction) { originalInterceptor.beforeCommit(transaction) }

    override suspend fun afterCommit(transaction: R2dbcTransaction) { originalInterceptor.afterCommit(transaction) }

    override suspend fun beforeRollback(transaction: R2dbcTransaction) { originalInterceptor.beforeRollback(transaction) }

    override suspend fun afterRollback(transaction: R2dbcTransaction) { originalInterceptor.afterRollback(transaction) }

    override fun keepUserDataInTransactionStoreOnCommit(userData: Map<Key<*>, Any?>): Map<Key<*>, Any?> {
        return originalInterceptor.keepUserDataInTransactionStoreOnCommit(userData)
    }
}

/**
 * Wrapper class to consolidate usage of core [GlobalStatementInterceptor] with R2DBC-specific [GlobalSuspendStatementInterceptor],
 * so they can be processed together using a single `R2dbcTransaction.globalInterceptors` property.
 */
internal class GlobalStatementInterceptorWrapper(
    private val wrapper: StatementInterceptorWrapper,
) : GlobalSuspendStatementInterceptor, SuspendStatementInterceptor by wrapper {
    constructor(originalInterceptor: GlobalStatementInterceptor) : this(StatementInterceptorWrapper(originalInterceptor))
}
