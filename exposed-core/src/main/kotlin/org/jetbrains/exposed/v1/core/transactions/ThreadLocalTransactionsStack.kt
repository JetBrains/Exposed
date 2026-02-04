package org.jetbrains.exposed.v1.core.transactions

import org.jetbrains.exposed.v1.core.DatabaseApi
import org.jetbrains.exposed.v1.core.InternalApi
import org.jetbrains.exposed.v1.core.Transaction
import java.util.Stack
import kotlin.concurrent.getOrSet

/**
 * A thread-local stack for managing Transaction objects.
 *
 * Each thread keeps its own stack so transactions are isolated per thread.
 * Coroutines that hop threads must pair every push with a pop to avoid leaks.
 *
 * Intended usage:
 * - At the start of work in a coroutine context, push the active transaction.
 * - After the work completes, pop it exactly once.
 *
 * To avoid misuse and potential memory leaks, prefer using the utilities:
 * - withTransactionContext(...)
 * - withThreadLocalTransaction(...)
 * @suppress
 */
@InternalApi
object ThreadLocalTransactionsStack {
    private val transactions = ThreadLocal<Stack<Transaction>>()

    /**
     * Pushes the given transaction onto the current thread's stack.
     * If the stack does not exist yet for this thread, it is created.
     */
    fun pushTransaction(transaction: Transaction) {
        transactions.getOrSet { Stack() }.push(transaction)
    }

    /**
     * Pops the top transaction from the current thread's stack.
     *
     * Throws:
     * - IllegalArgumentException if there is no transaction to pop.
     *
     * Automatically clears the thread-local when the stack becomes empty,
     * helping the GC and preventing thread-local leaks.
     */
    fun popTransaction(): Transaction {
        val stack = transactions.get()
        require(stack != null && stack.isNotEmpty()) { "No transaction to pop" }
        val result = stack.pop()

        if (stack.isEmpty()) {
            // Remove the ThreadLocal entirely when stack is empty.
            transactions.remove()
        }

        return result
    }

    /**
     * Returns the current top transaction or null if none is present.
     * Does not modify the stack.
     */
    fun getTransactionOrNull(): Transaction? {
        val stack = transactions.get() ?: return null
        return if (stack.isEmpty()) null else stack.peek()
    }

    /**
     * Returns the most recent transaction for the specified [db] from the stack,
     * or null if none is found.
     *
     * This method performs a linear search through the transaction stack to find
     * the most recent transaction associated with the given database. Does not modify the stack.
     *
     * **Performance Note**: The current implementation uses [List.findLast] which has O(n) time complexity.
     * For scenarios with many concurrent database connections, this may become a bottleneck.
     * Consider using a more efficient data structure (e.g., a Map of database to transaction stack)
     * if performance profiling indicates this is a problem.
     *
     * @param db The database for which to find the transaction
     * @return The most recent transaction for the specified database, or null if not found
     */
    // TODO make search in list is not optimal, another structure should be used
    //  Related issue: https://youtrack.jetbrains.com/issue/EXPOSED-915/ThreadLocalTransactionsStack-makes-inefficient-operations
    fun getTransactionOrNull(db: DatabaseApi): Transaction? {
        return transactions.get()?.findLast { it.db == db }
    }

    fun <T : Transaction> getTransactionIsInstance(klass: Class<T>): T? {
        return transactions.get()?.filterIsInstance(klass)?.lastOrNull()
    }

    /**
     * Returns true if the current thread has no transactions,
     * or if the stack exists but is empty.
     */
    fun isEmpty(): Boolean {
        val stack = transactions.get() ?: return true
        return stack.isEmpty()
    }

    /**
     * Returns transactions that belong to the current thread.
     *
     * Made for testing purposes. It's better to avoid manipulating the stack directly.
     */
    fun threadTransactions(): Stack<Transaction>? {
        return transactions.get()
    }
}
