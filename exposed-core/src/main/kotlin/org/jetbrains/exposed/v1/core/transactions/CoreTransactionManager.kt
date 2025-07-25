package org.jetbrains.exposed.v1.core.transactions

import org.jetbrains.exposed.v1.core.DatabaseApi
import org.jetbrains.exposed.v1.core.InternalApi
import org.jetbrains.exposed.v1.core.Transaction
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedDeque
import java.util.concurrent.atomic.AtomicReference
import kotlin.collections.set

/**
 * Represents the object responsible for storing internal data related to each registered database
 * and its transaction manager.
 */
@InternalApi
object CoreTransactionManager {
    private val databases = ConcurrentLinkedDeque<DatabaseApi>()

    private val currentDefaultDatabase = AtomicReference<DatabaseApi>()

    /** Returns the database that has been set as the default for all transactions. */
    fun getDefaultDatabase(): DatabaseApi? = currentDefaultDatabase.get()

    /**
     * Returns the database that has been set as the default for all transactions, or, if none was set,
     * the last instance created.
     */
    fun getDefaultDatabaseOrFirst(): DatabaseApi? = getDefaultDatabase() ?: databases.firstOrNull()

    /** Sets the specified database instance as the default for all transactions. */
    fun setDefaultDatabase(db: DatabaseApi?) {
        currentDefaultDatabase.set(db)
    }

    private val registeredDatabases = ConcurrentHashMap<DatabaseApi, TransactionManagerApi>()

    /**
     * Returns the transaction manager instance that is associated with the provided database key,
     * or `null` if  a manager has not been registered for the database.
     */
    fun getDatabaseManager(db: DatabaseApi): TransactionManagerApi? = registeredDatabases[db]

    private val currentThreadManager = TransactionManagerThreadLocal()

    /** Stores the specified database instance as a key for the provided transaction manager value. */
    fun registerDatabaseManager(db: DatabaseApi, manager: TransactionManagerApi) {
        if (getDefaultDatabaseOrFirst() == null) {
            currentThreadManager.remove()
        }
        if (!registeredDatabases.containsKey(db)) {
            databases.push(db)
        }

        registeredDatabases[db] = manager
    }

    /**
     * Clears any association between the provided database instance and its transaction manager,
     * and completely removes the database instance from the internal storage.
     */
    fun closeAndUnregisterDatabase(db: DatabaseApi) {
        val manager = getDatabaseManager(db)
        manager?.let {
            registeredDatabases.remove(db)
            databases.remove(db)
            currentDefaultDatabase.compareAndSet(db, null)
            if (currentThreadManager.isInitialized && getCurrentThreadManager() == it) {
                currentThreadManager.remove()
            }
        }
    }

    /** Returns the transaction manager instance stored in the current thread's copy of its thread-local variable. */
    fun getCurrentThreadManager(): TransactionManagerApi = currentThreadManager.get()

    /**
     * Sets the current thread's copy of its thread-local variable to the specified [manager] instance,
     * or removes the value entirely if a `null` instance is provided.
     */
    fun resetCurrentThreadManager(manager: TransactionManagerApi?) {
        manager?.let { currentThreadManager.set(it) } ?: currentThreadManager.remove()
    }

    /**
     * Returns the current [Transaction] from the current transaction manager instance,
     * or `null` if none exists.
     */
    fun currentTransactionOrNull(): Transaction? = getCurrentThreadManager().currentOrNull()

    /**
     * Returns the current [Transaction] from the current transaction manager instance.
     *
     * @throws IllegalStateException If a transaction is not currently open.
     */
    fun currentTransaction(): Transaction = currentTransactionOrNull() ?: error("No transaction in context.")

    private class TransactionManagerThreadLocal : ThreadLocal<TransactionManagerApi>() {
        var isInitialized = false

        override fun get(): TransactionManagerApi {
            return super.get()
        }

        override fun initialValue(): TransactionManagerApi {
            isInitialized = true
            return getDefaultDatabaseOrFirst()?.let { registeredDatabases.getValue(it) } ?: NotInitializedTransactionManager
        }

        override fun set(value: TransactionManagerApi?) {
            isInitialized = true
            super.set(value)
        }

        override fun remove() {
            isInitialized = false
            super.remove()
        }
    }
}

private object NotInitializedTransactionManager : TransactionManagerApi {
    override var defaultReadOnly: Boolean = false

    override var defaultMaxAttempts: Int = -1

    override var defaultMinRetryDelay: Long = 0

    override var defaultMaxRetryDelay: Long = 0

    override fun currentOrNull(): Transaction = error(
        "Please call Database.connect() or R2dbcDatabase.connect() before using this code"
    )

    override fun bindTransactionToThread(transaction: Transaction?) {
        error("Please call Database.connect() or R2dbcDatabase.connect() before using this code")
    }
}
