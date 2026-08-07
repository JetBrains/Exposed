package org.jetbrains.exposed.v1.core.transactions

import org.jetbrains.exposed.v1.core.DatabaseApi
import org.jetbrains.exposed.v1.core.InternalApi
import org.jetbrains.exposed.v1.core.Transaction
import java.util.*

/**
 * Base data structure for managing [Transaction] instances based on the underlying driver and/or manager combination.
 * @suppress
 */
@InternalApi
interface TransactionsStack {
    /**
     * When multiple implementations of this interface are detected, this value determines which one will be used,
     * with the highest value implementation being chosen.
     *
     * Exposed built-in implementations will have a default priority of either 0 or 1
     * (for specific cases like Spring's transaction management). Custom implementations with a higher priority value
     * will override these defaults when the registry first searches for an implementation.
     * @suppress
     */
    val priority: Int
        get() = 0

    /**
     * The amount of [Transaction] instances being actively managed.
     * @suppress
     */
    val size: Int

    /**
     * Stores the provided [transaction] to the underlying data structure.
     * @suppress
     */
    fun pushTransaction(transaction: Transaction)

    /**
     * Removes the currently active [Transaction] from the underlying data structure.
     * @suppress
     */
    fun popTransaction(): Transaction

    /**
     * Returns the currently active [Transaction] from the underlying data structure,
     * without modification of the collection, or `null` if none is active.
     * @suppress
     */
    fun getTransactionOrNull(): Transaction?

    /**
     * Returns the most recently active [Transaction] for the provided [db] from the underlying data structure,
     * without modification of the collection, or `null` if none is found.
     * @suppress
     */
    fun getTransactionOrNull(db: DatabaseApi): Transaction?

    /**
     * Returns the most recently active [Transaction] of the provided type [klass] from the underlying data structure,
     * without modification of the collection, or `null` if none is found.
     * @suppress
     */
    fun <T : Transaction> getTransactionIsInstance(klass: Class<T>): T?

    /**
     * Returns whether the underlying data structure stores any [Transaction] instances.
     */
    fun isEmpty(): Boolean

    /**
     * Returns the stored [Transaction] instances as a list of their String id values.
     */
    fun getTransactionsAsIds(): List<String>
}

/**
 * Object responsible for locating and providing the appropriate implementation
 * for the underlying transaction data structure.
 * @suppress
 */
@InternalApi
object TransactionsStackProvider {
    /**
     * @suppress
     */
    @OptIn(InternalApi::class)
    val stackImpl: TransactionsStack = ServiceLoader
        .load(TransactionsStack::class.java, TransactionsStack::class.java.classLoader)
        .maxByOrNull { it.priority }
        ?: ThreadLocalTransactionsStack
}
