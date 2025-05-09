package org.jetbrains.exposed.v1.r2dbc.transactions

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ThreadContextElement
import org.jetbrains.exposed.v1.r2dbc.R2dbcTransaction
import org.jetbrains.exposed.v1.r2dbc.mtc.MappedTransactionContext
import kotlin.coroutines.CoroutineContext

internal class TransactionContext(val manager: TransactionManager?, val transaction: R2dbcTransaction?)

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
    val manager: TransactionManager
) : ThreadContextElement<TransactionContext> {
    override val key: CoroutineContext.Key<TransactionCoroutineElement> = Companion

    override fun updateThreadContext(context: CoroutineContext): TransactionContext {
        val currentTransaction = TransactionManager.currentOrNull()
        val currentManager = currentTransaction?.db?.transactionManager
        manager.bindTransactionToThread(newTransaction.value)
        TransactionManager.resetCurrent(manager)
        setCurrentTransaction(newTransaction.value)
        return TransactionContext(currentManager, currentTransaction)
    }

    override fun restoreThreadContext(context: CoroutineContext, oldState: TransactionContext) {
        manager.bindTransactionToThread(oldState.transaction)
        TransactionManager.resetCurrent(oldState.manager)
    }

    private fun setCurrentTransaction(transaction: R2dbcTransaction?) {
        MappedTransactionContext.setTransaction(transaction)
    }

    fun cleanCurrentTransaction() = MappedTransactionContext.clean()

    companion object : CoroutineContext.Key<TransactionCoroutineElement>
}
