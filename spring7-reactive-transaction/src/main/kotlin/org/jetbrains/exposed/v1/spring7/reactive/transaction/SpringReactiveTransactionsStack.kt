package org.jetbrains.exposed.v1.spring7.reactive.transaction

import org.jetbrains.exposed.v1.core.DatabaseApi
import org.jetbrains.exposed.v1.core.InternalApi
import org.jetbrains.exposed.v1.core.Transaction
import org.jetbrains.exposed.v1.core.transactions.TransactionManagerApi
import org.jetbrains.exposed.v1.core.transactions.TransactionsHolder
import org.jetbrains.exposed.v1.r2dbc.transactions.R2dbcTransactionManager
import java.util.*
import kotlin.concurrent.getOrSet

/**
 * A stack for managing [Transaction] objects determined upstream by Spring's `TransactionContext` and Spring's
 * transaction synchronization resource manager.
 */
@OptIn(InternalApi::class)
internal object SpringReactiveTransactionsStack : TransactionsHolder {
    private val transactions = ThreadLocal<Stack<Transaction>>()

    private const val SPRING_PRIORITY_LEVEL: Int = 5

    override val priority: Int
        get() = SPRING_PRIORITY_LEVEL

    override val size: Int
        get() = transactions.get()?.size ?: 0

    override fun storeTransaction(transaction: Transaction) {
        transactions.getOrSet { Stack() }.push(transaction)
    }

    override fun removeTransaction(): Transaction {
        val stack = transactions.get()
        require(stack != null && stack.isNotEmpty()) { "No transaction to pop" }
        val result = stack.pop()

        if (stack.isEmpty()) {
            transactions.remove()
        }

        return result
    }

    override fun getTransactionOrNull(): Transaction? {
        val stack = transactions.get() ?: return null
        return if (stack.isEmpty()) null else stack.peek()
    }

    override fun getTransactionOrNull(db: DatabaseApi): Transaction? {
        return transactions.get()?.findLast { it.db == db }
    }

    override fun <T : Transaction> getTransactionIsInstance(klass: Class<T>): T? {
        return transactions.get()?.filterIsInstance(klass)?.lastOrNull()
    }

    override suspend fun getTransactionFromContextOrNull(manager: TransactionManagerApi): Transaction? {
        return getTransactionOrNull((manager as R2dbcTransactionManager).db)
    }

    override fun isEmpty(): Boolean {
        val stack = transactions.get() ?: return true
        return stack.isEmpty()
    }

    override fun snapshot(): List<Transaction> = transactions.get()?.toList().orEmpty()

    fun restore(snapshot: List<Transaction>) {
        if (snapshot.isEmpty()) {
            transactions.remove()
        } else {
            transactions.set(Stack<Transaction>().apply { addAll(snapshot) })
        }
    }
}

/**
 * A proxy class for [SpringReactiveTransactionsStack] to allow detection and registering by a `ServiceLoader` in
 * the core module.
 */
@OptIn(InternalApi::class)
internal class SpringReactiveTransactionsStackProxy : TransactionsHolder {
    override val priority: Int
        get() = SpringReactiveTransactionsStack.priority

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
}

@OptIn(InternalApi::class)
internal fun TransactionsHolder.restoreSpringStack(snapshot: List<Transaction>) {
    require(this is SpringReactiveTransactionsStackProxy) {
        "Spring-Exposed reactive transaction management is overseen by an internal TransactionHolder. " +
            "Custom implementations of this interface should be avoided when using the exposed-spring-boot4-starter-r2dbc."
    }

    SpringReactiveTransactionsStack.restore(snapshot)
}

/**
 * Marker class for storing a snapshot of [SpringReactiveTransactionsStack] data to be used by
 * [SpringReactiveTransactionContextElement] and its context restoration methods.
 */
internal data class TransactionStackState(
    val transactions: List<Transaction>
)
