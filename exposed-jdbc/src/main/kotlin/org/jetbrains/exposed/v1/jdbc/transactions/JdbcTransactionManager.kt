package org.jetbrains.exposed.v1.jdbc.transactions

import kotlinx.coroutines.currentCoroutineContext
import org.jetbrains.exposed.v1.core.InternalApi
import org.jetbrains.exposed.v1.core.Transaction
import org.jetbrains.exposed.v1.core.transactions.ThreadLocalTransactionsStack
import org.jetbrains.exposed.v1.core.transactions.TransactionManagerApi
import org.jetbrains.exposed.v1.core.transactions.suspend.TransactionContextElement
import org.jetbrains.exposed.v1.core.transactions.suspend.TransactionContextHolder
import org.jetbrains.exposed.v1.core.transactions.suspend.TransactionContextHolderImpl
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import kotlin.coroutines.CoroutineContext

/**
 * Represents the JDBC transaction manager interface, responsible for creating
 * and managing JDBC transactions.
 */
interface JdbcTransactionManager : TransactionManagerApi {
    /** The database instance associated with this transaction manager. */
    val db: Database

    /** The default transaction isolation level. Unless specified, the database-specific level will be used. */
    var defaultIsolationLevel: Int

    /**
     * Returns a [JdbcTransaction] instance.
     *
     * The returned value may be a new transaction, or it may return the [outerTransaction] if called from within
     * an existing transaction with the database not configured to `useNestedTransactions`.
     */
    fun newTransaction(
        isolation: Int = defaultIsolationLevel,
        readOnly: Boolean = defaultReadOnly,
        outerTransaction: JdbcTransaction? = null
    ): JdbcTransaction

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
fun JdbcTransactionManager.createTransactionContext(transaction: Transaction): CoroutineContext {
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
 * Returns the current JDBC transaction from the coroutine context, or null if none exists.
 *
 * This method performs type checking to ensure the transaction in the context is actually
 * a [JdbcTransaction]. If a non-JDBC transaction is found in the context, an error is thrown
 * to prevent type confusion between JDBC and R2DBC transactions.
 *
 * @return The current [JdbcTransaction] from the coroutine context, or null if no transaction exists
 * @throws [IllegalStateException] If the transaction in the context is not a [JdbcTransaction]
 */
internal suspend fun JdbcTransactionManager.getCurrentContextTransaction(): JdbcTransaction? {
    val transaction = currentCoroutineContext()[contextKey]?.transaction
    return when {
        transaction == null -> null
        transaction is JdbcTransaction -> transaction
        else -> error(
            "Expected JdbcTransaction in coroutine context but found ${transaction::class.simpleName}. " +
                "This may indicate mixing JDBC and R2DBC transactions incorrectly."
        )
    }
}

/**
 * Returns the current JDBC transaction from the thread-local stack for this manager's database, or null if none exists.
 *
 * @return The current [JdbcTransaction] for this manager's database, or null if no transaction is active.
 */
fun JdbcTransactionManager.currentOrNull(): JdbcTransaction? {
    @OptIn(InternalApi::class)
    return ThreadLocalTransactionsStack.getTransactionOrNull(db) as? JdbcTransaction
}
