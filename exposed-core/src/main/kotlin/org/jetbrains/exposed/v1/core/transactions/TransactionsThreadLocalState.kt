package org.jetbrains.exposed.v1.core.transactions

import org.jetbrains.exposed.v1.core.InternalApi
import org.jetbrains.exposed.v1.core.Transaction

/**
 * An immutable snapshot of a thread's transaction stack, ordered from bottom (oldest/outermost) to top
 * (most recently pushed/current).
 *
 * Used to capture, transport, and restore [ThreadLocalTransactionsStack] contents across suspension points,
 * for example when a coroutine execution segment is projected onto a carrier thread and must be restored
 * exactly afterward.
 * @suppress
 */
@InternalApi
data class TransactionsThreadLocalState(
    val transactions: List<Transaction>
)

/**
 * Captures the current thread's [ThreadLocalTransactionsStack] contents as an immutable [TransactionsThreadLocalState].
 * Does not modify the stack.
 * @suppress
 */
@InternalApi
fun captureTransactionsThreadLocalState(): TransactionsThreadLocalState =
    TransactionsThreadLocalState(ThreadLocalTransactionsStack.snapshot())

/**
 * Replaces the current thread's [ThreadLocalTransactionsStack] contents with the provided [state].
 * Passing a [TransactionsThreadLocalState] with an empty list clears the current thread's stack entirely.
 * @suppress
 */
@InternalApi
fun restoreTransactionsThreadLocalState(state: TransactionsThreadLocalState) {
    ThreadLocalTransactionsStack.restore(state.transactions)
}
