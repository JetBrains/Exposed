package org.jetbrains.exposed.v1.spring7.reactive.transaction

import org.jetbrains.exposed.v1.core.DatabaseApi
import org.jetbrains.exposed.v1.core.InternalApi
import org.jetbrains.exposed.v1.core.Transaction
import org.jetbrains.exposed.v1.core.transactions.TransactionsStack
import java.util.*

@OptIn(InternalApi::class)
internal object SpringReactiveTransactionsStack : TransactionsStack {
    private var transactions: Stack<Transaction>? = null

    override val priority: Int
        get() = 1

    override val size: Int
        get() = transactions?.size ?: 0

    override fun pushTransaction(transaction: Transaction) {
        if (transactions == null) {
            transactions = Stack()
        }
        transactions?.push(transaction) ?: error("Error on transaction stack")
    }

    override fun popTransaction(): Transaction {
        val stack = transactions?.ifEmpty { null } ?: error("No transaction to pop")
        val result = stack.pop()

        if (stack.isEmpty()) {
            // Remove the ThreadLocal entirely when stack is empty.
            transactions = null
        }

        return result
    }

    internal fun popUntilSynced(transaction: Transaction) {
        while (true) {
            val popped = popTransaction()
            if (popped.transactionId == transaction.transactionId) break
        }
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

    override fun isEmpty(): Boolean {
        val stack = transactions ?: return true
        return stack.isEmpty()
    }

    override fun getTransactionsAsIds(): List<String> {
        return transactions?.map { it.transactionId } ?: emptyList()
    }
}

@OptIn(InternalApi::class)
internal class SpringReactiveTransactionsStackProxy : TransactionsStack {
    override val size: Int
        get() = SpringReactiveTransactionsStack.size

    override fun pushTransaction(transaction: Transaction) {
        SpringReactiveTransactionsStack.pushTransaction(transaction)
    }

    override fun popTransaction(): Transaction = SpringReactiveTransactionsStack.popTransaction()
    override fun getTransactionOrNull(): Transaction? = SpringReactiveTransactionsStack.getTransactionOrNull()
    override fun getTransactionOrNull(db: DatabaseApi): Transaction? = SpringReactiveTransactionsStack.getTransactionOrNull(db)
    override fun <T : Transaction> getTransactionIsInstance(klass: Class<T>): T? = SpringReactiveTransactionsStack.getTransactionIsInstance(klass)
    override fun isEmpty(): Boolean = SpringReactiveTransactionsStack.isEmpty()
    override fun getTransactionsAsIds(): List<String> = SpringReactiveTransactionsStack.getTransactionsAsIds()
}
