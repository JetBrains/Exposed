package org.jetbrains.exposed.v1.jdbc.transactions.suspend

import kotlinx.coroutines.ThreadContextElement
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager
import org.jetbrains.exposed.v1.jdbc.transactions.transactionManager
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

internal class TransactionContextElement(
    private val transaction: JdbcTransaction
) : ThreadContextElement<JdbcTransaction?>, AbstractCoroutineContextElement(TransactionContextElement) {

    companion object Key : CoroutineContext.Key<TransactionContextElement>

    override fun updateThreadContext(context: CoroutineContext): JdbcTransaction? {
        val currentTransaction = TransactionManager.currentOrNull()

        val newManager = transaction.db.transactionManager
        newManager.bindTransactionToThread(transaction)
        TransactionManager.resetCurrent(newManager)

        return currentTransaction
    }

    override fun restoreThreadContext(context: CoroutineContext, oldState: JdbcTransaction?) {
        val newManager = transaction.db.transactionManager
        newManager.bindTransactionToThread(oldState)
        TransactionManager.resetCurrent(oldState?.db.transactionManager)
    }
}
