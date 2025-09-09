package org.jetbrains.exposed.v1.r2dbc.transactions

import kotlinx.coroutines.ThreadContextElement
import org.jetbrains.exposed.v1.core.exposedLogger
import org.jetbrains.exposed.v1.r2dbc.R2dbcTransaction
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

internal class TransactionContextElement(
    private val transaction: R2dbcTransaction
) : ThreadContextElement<R2dbcTransaction?>, AbstractCoroutineContextElement(TransactionContextElement) {

    companion object Key : CoroutineContext.Key<TransactionContextElement>

    override fun updateThreadContext(context: CoroutineContext): R2dbcTransaction? {
        val previousTransaction = ThreadLocalTransactionsStack.getTransactionOrNull()
        ThreadLocalTransactionsStack.pushTransaction(transaction)
        TransactionManager.resetCurrent(transaction.db.transactionManager)
        return previousTransaction
    }

    override fun restoreThreadContext(context: CoroutineContext, oldState: R2dbcTransaction?) {
        val poppedTransaction = ThreadLocalTransactionsStack.popTransaction()
        if (poppedTransaction.id != transaction.id) {
            exposedLogger.warn("Popped transaction ${poppedTransaction.id} doesn't match expected ${transaction.id}")
        }

        TransactionManager.resetCurrent(oldState?.db?.transactionManager)
    }
}
