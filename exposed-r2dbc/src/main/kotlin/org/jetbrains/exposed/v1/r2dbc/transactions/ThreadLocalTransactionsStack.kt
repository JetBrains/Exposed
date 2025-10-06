package org.jetbrains.exposed.v1.r2dbc.transactions

import org.jetbrains.exposed.v1.r2dbc.R2dbcTransaction
import java.util.Stack
import kotlin.concurrent.getOrSet

/**
 * A thread-local stack for managing R2dbcTransaction objects.
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
 */
internal object ThreadLocalTransactionsStack {
    private val transactions = ThreadLocal<Stack<R2dbcTransaction>>()

    /**
     * Pushes the given transaction onto the current thread's stack.
     * If the stack does not exist yet for this thread, it is created.
     */
    fun pushTransaction(transaction: R2dbcTransaction) {
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
    fun popTransaction(): R2dbcTransaction {
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
    fun getTransactionOrNull(): R2dbcTransaction? {
        val stack = transactions.get() ?: return null
        return if (stack.isEmpty()) null else stack.peek()
    }

    /**
     * Returns true if the current thread has no transactions,
     * or if the stack exists but is empty.
     */
    fun isEmpty(): Boolean {
        val stack = transactions.get() ?: return true
        return stack.isEmpty()
    }
}
