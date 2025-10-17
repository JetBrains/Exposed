package org.jetbrains.exposed.v1.core.transactions.suspend

import kotlinx.coroutines.ThreadContextElement
import org.jetbrains.exposed.v1.core.InternalApi
import org.jetbrains.exposed.v1.core.Transaction
import org.jetbrains.exposed.v1.core.exposedLogger
import org.jetbrains.exposed.v1.core.transactions.ThreadLocalTransactionsStack
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

/**
 * A coroutine context element that manages thread-local transaction state for coroutines.
 *
 * This element ensures that when a coroutine is suspended and resumed on a different thread,
 * the transaction is properly restored to the thread-local stack.
 *
 * @property transaction The transaction to be managed in the coroutine context.
 */
@InternalApi
class TransactionContextElement(
    private val transaction: Transaction
) : ThreadContextElement<Transaction?>, AbstractCoroutineContextElement(TransactionContextElement) {

    companion object Key : CoroutineContext.Key<TransactionContextElement>

    /**
     * Updates the thread context when the coroutine is resumed on a thread.
     * Pushes the transaction onto the thread-local stack and returns the previous transaction.
     *
     * @param context The coroutine context.
     * @return The previous transaction that was on top of the stack, or null if none existed.
     */
    override fun updateThreadContext(context: CoroutineContext): Transaction? {
        val previousTransaction = ThreadLocalTransactionsStack.getTransactionOrNull() as? Transaction
        ThreadLocalTransactionsStack.pushTransaction(transaction)
        return previousTransaction
    }

    /**
     * Restores the thread context when the coroutine is suspended or completed.
     * Pops the transaction from the thread-local stack and validates it matches the expected transaction.
     *
     * @param context The coroutine context.
     * @param oldState The previous transaction state that was returned by [updateThreadContext].
     */
    override fun restoreThreadContext(context: CoroutineContext, oldState: Transaction?) {
        val poppedTransaction = ThreadLocalTransactionsStack.popTransaction()
        if (poppedTransaction.id != transaction.id) {
            exposedLogger.warn(
                "The current thread local stack of transactions had a transaction ${poppedTransaction.id} on the top. " +
                    "But it differs from the transaction ${transaction.id} in the coroutine context. " +
                    "Normally it should not happen because the coroutine adds its transaction into the thread " +
                    "local stack on the start and removes it on the end of execution"
            )
            exposedLogger.warn("Popped transaction ${poppedTransaction.id} doesn't match expected ${transaction.id}")
        }
    }
}
