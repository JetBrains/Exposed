package org.jetbrains.exposed.v1.r2dbc.transactions

import io.r2dbc.spi.IsolationLevel
import kotlinx.coroutines.currentCoroutineContext
import org.jetbrains.exposed.v1.core.InternalApi
import org.jetbrains.exposed.v1.core.Transaction
import org.jetbrains.exposed.v1.core.transactions.ThreadLocalTransactionsStack
import org.jetbrains.exposed.v1.core.transactions.TransactionManagerApi
import org.jetbrains.exposed.v1.core.transactions.suspend.TransactionContextElement
import org.jetbrains.exposed.v1.core.transactions.suspend.TransactionContextHolder
import org.jetbrains.exposed.v1.core.transactions.suspend.TransactionContextHolderImpl
import org.jetbrains.exposed.v1.r2dbc.R2dbcDatabase
import org.jetbrains.exposed.v1.r2dbc.R2dbcTransaction
import kotlin.coroutines.CoroutineContext

/**
 * Represents the R2DBC transaction manager interface, responsible for creating
 * and managing R2DBC transactions.
 */
interface R2dbcTransactionManager : TransactionManagerApi {
    /** The database instance associated with this transaction manager. */
    val db: R2dbcDatabase

    /** The default transaction isolation level. Unless specified, the database-specific level will be used. */
    var defaultIsolationLevel: IsolationLevel?

    /**
     * Returns an [R2dbcTransaction] instance.
     *
     * The returned value may be a new transaction, or it may return the [outerTransaction] if called from within
     * an existing transaction with the database not configured to `useNestedTransactions`.
     */
    fun newTransaction(
        isolation: IsolationLevel? = defaultIsolationLevel,
        readOnly: Boolean? = defaultReadOnly,
        outerTransaction: R2dbcTransaction? = null
    ): R2dbcTransaction

    /** A unique key for storing coroutine context elements, as [TransactionContextHolder]. */
    val contextKey: CoroutineContext.Key<TransactionContextHolder>
}

/**
 * Creates a coroutine context for the given transaction.
 *
 * @param transaction The transaction for which to create the coroutine context.
 * @return A [CoroutineContext] containing the transaction holder and context element.
 * @throws IllegalStateException if the transaction's manager doesn't match this manager.
 */
fun R2dbcTransactionManager.createTransactionContext(transaction: Transaction): CoroutineContext {
    if (transaction.transactionManager != this) {
        error(
            "TransactionManager must create transaction context only for own transactions. " +
                "Transaction manager of ${transaction.db.url} tried to create transaction context for ${transaction.db.url}"
        )
    }
    @OptIn(InternalApi::class)
    return TransactionContextHolderImpl(transaction, contextKey) + TransactionContextElement(transaction)
}

/**
 * Returns the current R2DBC transaction from the coroutine context, or null if none exists.
 *
 * This method performs type checking to ensure the transaction in the context is actually
 * an [R2dbcTransaction]. If a non-R2DBC transaction is found in the context, an error is thrown
 * to prevent type confusion between JDBC and R2DBC transactions.
 *
 * @return The current [R2dbcTransaction] from the coroutine context, or null if no transaction exists
 * @throws [IllegalStateException] If the transaction in the context is not an [R2dbcTransaction]
 */
suspend fun R2dbcTransactionManager.getCurrentContextTransaction(): R2dbcTransaction? {
    val transaction = currentCoroutineContext()[contextKey]?.transaction
    return when {
        transaction == null -> null
        transaction is R2dbcTransaction -> transaction
        else -> error(
            "Expected R2dbcTransaction in coroutine context but found ${transaction::class.simpleName}. " +
                "This may indicate mixing JDBC and R2DBC transactions incorrectly."
        )
    }
}

/**
 * Returns the current R2DBC transaction from the thread-local stack for this manager's database, or null if none exists.
 *
 * @return The current [R2dbcTransaction] for this manager's database, or null if no transaction is active.
 */
fun R2dbcTransactionManager.currentOrNull(): R2dbcTransaction? {
    @OptIn(InternalApi::class)
    return ThreadLocalTransactionsStack.getTransactionOrNull(db) as? R2dbcTransaction
}
