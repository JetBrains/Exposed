package org.jetbrains.exposed.sql.transactions

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ThreadContextElement
import org.jetbrains.exposed.sql.R2dbcTransaction
import kotlin.coroutines.CoroutineContext

internal class TransactionContext(val manager: R2dbcTransactionManager?, val transaction: R2dbcTransaction?)

internal class TransactionScope(
    val tx: Lazy<R2dbcTransaction>,
    parent: CoroutineContext
) : CoroutineScope, CoroutineContext.Element {
    private val baseScope = CoroutineScope(parent)
    override val coroutineContext get() = baseScope.coroutineContext + this
    override val key = Companion

    fun holdsSameTransaction(transaction: R2dbcTransaction?) =
        transaction != null && tx.isInitialized() && tx.value == transaction
    companion object : CoroutineContext.Key<TransactionScope>
}

internal class TransactionCoroutineElement(
    private val newTransaction: Lazy<R2dbcTransaction>,
    val manager: R2dbcTransactionManager
) : ThreadContextElement<TransactionContext> {
    override val key: CoroutineContext.Key<TransactionCoroutineElement> = Companion

    override fun updateThreadContext(context: CoroutineContext): TransactionContext {
        val currentTransaction = TransactionManager.currentOrNull() as? R2dbcTransaction
        val currentManager = currentTransaction?.db?.transactionManager as? R2dbcTransactionManager
        manager.bindTransactionToThread(newTransaction.value)
        TransactionManager.resetCurrent(manager)
        return TransactionContext(currentManager, currentTransaction)
    }

    override fun restoreThreadContext(context: CoroutineContext, oldState: TransactionContext) {
        manager.bindTransactionToThread(oldState.transaction)
        TransactionManager.resetCurrent(oldState.manager)
    }

    companion object : CoroutineContext.Key<TransactionCoroutineElement>
}
