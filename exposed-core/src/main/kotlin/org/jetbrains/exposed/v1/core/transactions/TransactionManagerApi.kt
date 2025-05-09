package org.jetbrains.exposed.v1.core.transactions

import org.jetbrains.exposed.v1.core.DatabaseApi
import org.jetbrains.exposed.v1.core.InternalApi
import org.jetbrains.exposed.v1.core.Transaction
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedDeque
import java.util.concurrent.atomic.AtomicReference

@Suppress("ForbiddenComment")
// TODO: break down this to a separate files

private object NotInitializedTransactionManager : TransactionManagerApi {
    override var defaultReadOnly: Boolean = false

    override var defaultMaxAttempts: Int = -1

    override var defaultMinRetryDelay: Long = 0

    override var defaultMaxRetryDelay: Long = 0

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
    /** Whether transactions should be performed in read-only mode. Unless specified, the database default will be used. */
    var defaultReadOnly: Boolean

    /** The default maximum amount of attempts that will be made to perform a transaction. */
    var defaultMaxAttempts: Int

    /** The default minimum number of milliseconds to wait before retrying a transaction if an exception is thrown. */
    var defaultMinRetryDelay: Long

    /** The default maximum number of milliseconds to wait before retrying a transaction if an exception is thrown. */
    var defaultMaxRetryDelay: Long

    /** Returns the current [Transaction], or `null` if none exists. */
    fun currentOrNull(): Transaction?

    /** Sets the current thread's copy of the manager's thread-local variable to the specified [transaction]. */
    fun bindTransactionToThread(transaction: Transaction?)
}

/**
 * Represents the object responsible for storing internal data related to each registered database
 * and its transaction manager.
 */
@Suppress("ForbiddenComment")
// TODO: move/add kdocs from TransactionManager
@InternalApi
object CoreTransactionManager {
    private val databases = ConcurrentLinkedDeque<DatabaseApi>()

    private val currentDefaultDatabase = AtomicReference<DatabaseApi>()

    fun getDefaultDatabase(): DatabaseApi? = currentDefaultDatabase.get()

    fun getDefaultDatabaseOrFirst(): DatabaseApi? = getDefaultDatabase() ?: databases.firstOrNull()

    fun setDefaultDatabase(db: DatabaseApi?) {
        currentDefaultDatabase.set(db)
    }

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
