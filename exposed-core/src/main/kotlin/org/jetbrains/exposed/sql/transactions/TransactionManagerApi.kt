package org.jetbrains.exposed.sql.transactions

import org.jetbrains.exposed.sql.DatabaseApi
import org.jetbrains.exposed.sql.InternalApi
import org.jetbrains.exposed.sql.Transaction
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedDeque
import java.util.concurrent.atomic.AtomicReference

private object NotInitializedManager : TransactionManagerApi {
    override var defaultIsolationLevel: Int = -1

    override var defaultReadOnly: Boolean = false

    override var defaultMaxAttempts: Int = -1

    override var defaultMinRetryDelay: Long = 0

    override var defaultMaxRetryDelay: Long = 0

    override fun newTransaction(isolation: Int, readOnly: Boolean, outerTransaction: Transaction?): Transaction =
        error("Please call Database.connect() before using this code")

    override fun currentOrNull(): Transaction = error("Please call Database.connect() before using this code")

    override fun bindTransactionToThread(transaction: Transaction?) {
        error("Please call Database.connect() before using this code")
    }
}

/**
 * Represents the manager registered to a database, which is responsible for creating new transactions
 * and storing data related to the database and its transactions.
 */
interface TransactionManagerApi {
    /** The default transaction isolation level. Unless specified, the database-specific level will be used. */
    var defaultIsolationLevel: Int

    /** Whether transactions should be performed in read-only mode. Unless specified, the database default will be used. */
    var defaultReadOnly: Boolean

    /** The default maximum amount of attempts that will be made to perform a transaction. */
    var defaultMaxAttempts: Int

    /** The default minimum number of milliseconds to wait before retrying a transaction if an exception is thrown. */
    var defaultMinRetryDelay: Long

    /** The default maximum number of milliseconds to wait before retrying a transaction if an exception is thrown. */
    var defaultMaxRetryDelay: Long

    /**
     * Returns a [Transaction] instance.
     *
     * The returned value may be a new transaction, or it may return the [outerTransaction] if called from within
     * an existing transaction with the database not configured to `useNestedTransactions`.
     */
    fun newTransaction(
        isolation: Int = defaultIsolationLevel,
        readOnly: Boolean = defaultReadOnly,
        outerTransaction: Transaction? = null
    ): Transaction

    /** Returns the current [Transaction], or `null` if none exists. */
    fun currentOrNull(): Transaction?

    /** Sets the current thread's copy of the manager's thread-local variable to the specified [transaction]. */
    fun bindTransactionToThread(transaction: Transaction?)
}

/**
 * Represents the object responsible for storing internal data related to each registered database
 * and its transaction manager.
 */
@InternalApi
object CoreManager {
    private val databases = ConcurrentLinkedDeque<DatabaseApi>()

    private val currentDefaultDatabase = AtomicReference<DatabaseApi>()

    fun getDefaultDatabase(): DatabaseApi? = currentDefaultDatabase.get()

    fun getDefaultDatabaseOrFirst(): DatabaseApi? = getDefaultDatabase() ?: databases.firstOrNull()

    fun setDefaultDatabase(db: DatabaseApi?) { currentDefaultDatabase.set(db) }

    private val registeredDatabases = ConcurrentHashMap<DatabaseApi, TransactionManagerApi>()

    fun getDatabaseManager(db: DatabaseApi): TransactionManagerApi? = registeredDatabases[db]

    private val currentThreadManager = TransactionManagerThreadLocal()

    fun registerDatabaseManager(db: DatabaseApi, manager: TransactionManagerApi) {
        if (getDefaultDatabaseOrFirst() == null) {
            currentThreadManager.remove()
        }
        if (!registeredDatabases.containsKey(db)) {
            databases.push(db)
        }

        registeredDatabases[db] = manager
    }

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

    fun getCurrentThreadManager(): TransactionManagerApi = currentThreadManager.get()

    fun resetCurrentThreadManager(manager: TransactionManagerApi?) {
        manager?.let { currentThreadManager.set(it) } ?: currentThreadManager.remove()
    }

    fun currentTransactionOrNull(): Transaction? = getCurrentThreadManager().currentOrNull()

    fun currentTransaction(): Transaction = currentTransactionOrNull() ?: error("No transaction in context.")

    private class TransactionManagerThreadLocal : ThreadLocal<TransactionManagerApi>() {
        var isInitialized = false

        override fun get(): TransactionManagerApi {
            return super.get()
        }

        override fun initialValue(): TransactionManagerApi {
            isInitialized = true
            return getDefaultDatabaseOrFirst()?.let { registeredDatabases.getValue(it) } ?: NotInitializedManager
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
