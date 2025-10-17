package org.jetbrains.exposed.v1.core.transactions

import org.jetbrains.exposed.v1.core.InternalApi
import org.jetbrains.exposed.v1.core.Transaction

/**
 * Returns the current [Transaction] from the current transaction manager instance,
 * or `null` if none exists.
 * @suppress
 */
@InternalApi
fun currentTransactionOrNull(): Transaction? {
    return ThreadLocalTransactionsStack.getTransactionOrNull()
}

/**
 * Returns the current [Transaction] from the current transaction manager instance.
 *
 * @throws IllegalStateException If a transaction is not currently open.
 * @suppress
 */
@InternalApi
fun currentTransaction(): Transaction = currentTransactionOrNull() ?: error("No transaction in context.")

/**
 * The method runs code block within the context of provided transaction.
 * If transaction is null, the code block is executed without any transaction context.
 *
 * Provided transaction will be pushed into [ThreadLocalTransactionsStack] before executing code block,
 * and will be popped from the stack after code block is executed.
 *
 * @param transaction The transaction to be used in the context.
 * @param block The code block to be executed in the context.
 * @return The result of executing the code block.
 * @suppress
 */
@InternalApi
fun <T> withThreadLocalTransaction(transaction: Transaction?, block: () -> T): T {
    if (transaction == null) {
        return block()
    }

    ThreadLocalTransactionsStack.pushTransaction(transaction)
    return try {
        block()
    } finally {
        ThreadLocalTransactionsStack.popTransaction()
    }
}
