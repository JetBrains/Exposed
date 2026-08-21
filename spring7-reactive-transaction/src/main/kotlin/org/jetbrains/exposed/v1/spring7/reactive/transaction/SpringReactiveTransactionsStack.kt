package org.jetbrains.exposed.v1.spring7.reactive.transaction

import org.jetbrains.exposed.v1.core.DatabaseApi
import org.jetbrains.exposed.v1.core.InternalApi
import org.jetbrains.exposed.v1.core.Transaction
import org.jetbrains.exposed.v1.core.transactions.TransactionManagerApi
import org.jetbrains.exposed.v1.core.transactions.TransactionsHolder
import org.jetbrains.exposed.v1.r2dbc.transactions.R2dbcTransactionManager
import java.util.*

/**
 * A stack for managing [Transaction] objects determined upstream by Spring's `TransactionContext` and Spring's
 * transaction synchronization resource manager.
 */
@OptIn(InternalApi::class)
internal object SpringReactiveTransactionsStack : TransactionsHolder {
    private var transactions: Stack<Transaction>? = null

    override val priority: Int
        get() = 1

    override val size: Int
        get() = transactions?.size ?: 0

    override fun storeTransaction(transaction: Transaction) {
        if (transactions == null) {
            transactions = Stack()
        }
        transactions?.push(transaction) ?: error("Error on transaction stack")
    }

    override fun removeTransaction(): Transaction {
        val stack = transactions?.ifEmpty { null } ?: error("No transaction to pop")
        val result = stack.pop()

        if (stack.isEmpty()) {
            transactions = null
        }

        return result
    }

    override fun getTransactionOrNull(): Transaction? {
        val stack = transactions ?: return null
        return if (stack.isEmpty()) null else stack.peek()
    }

    override fun getTransactionOrNull(db: DatabaseApi): Transaction? {
        return transactions?.findLast { it.db == db }
    }

    override fun <T : Transaction> getTransactionIsInstance(klass: Class<T>): T? {
        return transactions?.filterIsInstance(klass)?.lastOrNull()
    }

    override suspend fun getTransactionFromContextOrNull(manager: TransactionManagerApi): Transaction? {
        return getTransactionOrNull((manager as R2dbcTransactionManager).db)
    }

    override fun isEmpty(): Boolean {
        val stack = transactions ?: return true
        return stack.isEmpty()
    }

    override fun snapshot(): List<Transaction> = transactions?.toList().orEmpty()

    override fun restore(snapshot: List<Transaction>) {
        transactions = if (snapshot.isEmpty()) {
            null
        } else {
            Stack<Transaction>().apply { addAll(snapshot) }
        }
    }
}

/**
 * A proxy class for [SpringReactiveTransactionsStack] to allow detection and registering by a `ServiceLoader` in
 * the core module.
 */
@OptIn(InternalApi::class)
internal class SpringReactiveTransactionsStackProxy : TransactionsHolder {
    override val size: Int
        get() = SpringReactiveTransactionsStack.size

    override fun storeTransaction(transaction: Transaction) {
        SpringReactiveTransactionsStack.storeTransaction(transaction)
    }

    override fun removeTransaction(): Transaction = SpringReactiveTransactionsStack.removeTransaction()
    override fun getTransactionOrNull(): Transaction? = SpringReactiveTransactionsStack.getTransactionOrNull()
    override fun getTransactionOrNull(db: DatabaseApi): Transaction? = SpringReactiveTransactionsStack.getTransactionOrNull(db)
    override fun <T : Transaction> getTransactionIsInstance(klass: Class<T>): T? = SpringReactiveTransactionsStack.getTransactionIsInstance(klass)
    override suspend fun getTransactionFromContextOrNull(
        manager: TransactionManagerApi
    ): Transaction? = SpringReactiveTransactionsStack.getTransactionFromContextOrNull(manager)
    override fun isEmpty(): Boolean = SpringReactiveTransactionsStack.isEmpty()
    override fun snapshot(): List<Transaction> = SpringReactiveTransactionsStack.snapshot()
    override fun restore(snapshot: List<Transaction>) {
        SpringReactiveTransactionsStack.restore(snapshot)
    }
}

/**
 * Marker class for storing a snapshot of [SpringReactiveTransactionsStack] data to be used by
 * [SpringReactiveTransactionContextElement] and its context restoration methods.
 */
internal data class TransactionStackState(
    val transactions: List<Transaction>
)
