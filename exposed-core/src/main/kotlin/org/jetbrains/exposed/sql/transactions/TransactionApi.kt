package org.jetbrains.exposed.sql.transactions

import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.statements.api.DatabaseApi
import org.jetbrains.exposed.sql.statements.api.ExposedConnection
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedDeque
import java.util.concurrent.atomic.AtomicReference

/** Represents a unit block of work that is performed on a database. */
interface TransactionInterface {
    /** The database on which the transaction tasks are performed. */
    val db: DatabaseApi

    /** The database connection used by the transaction. */
    val connection: ExposedConnection<*>

    /** The transaction isolation level of the transaction, which may differ from the set database level. */
    val transactionIsolation: Int

    /** Whether the transaction is in read-only mode. */
    val readOnly: Boolean

    /** The parent transaction of a nested transaction; otherwise, `null` if the transaction is a top-level instance. */
    val outerTransaction: Transaction?

    /** Saves all changes since the last commit or rollback operation. */
    fun commit()

    /** Reverts all changes since the last commit or rollback operation, or to the last set savepoint, if applicable. */
    fun rollback()

    /** Closes the transaction and releases any savepoints. */
    fun close()
}

private object NotInitializedManager : TransactionManager {
    override var defaultIsolationLevel: Int = -1

    override var defaultReadOnly: Boolean = false

    override var defaultMaxAttempts: Int = -1

    override var defaultMinRetryDelay: Long = 0

    override var defaultMaxRetryDelay: Long = 0

    @Deprecated("This will be removed when the interface property is fully deprecated", level = DeprecationLevel.ERROR)
    override var defaultRepetitionAttempts: Int = -1

    @Deprecated("This will be removed when the interface property is fully deprecated", level = DeprecationLevel.ERROR)
    override var defaultMinRepetitionDelay: Long = 0

    @Deprecated("This will be removed when the interface property is fully deprecated", level = DeprecationLevel.ERROR)
    override var defaultMaxRepetitionDelay: Long = 0

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
interface TransactionManager {
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

    @Deprecated(
        message = "This property will be removed in future releases",
        replaceWith = ReplaceWith("defaultMaxAttempts"),
        level = DeprecationLevel.ERROR
    )
    var defaultRepetitionAttempts: Int

    @Deprecated(
        message = "This property will be removed in future releases",
        replaceWith = ReplaceWith("defaultMinRetryDelay"),
        level = DeprecationLevel.ERROR
    )
    var defaultMinRepetitionDelay: Long

    @Deprecated(
        message = "This property will be removed in future releases",
        replaceWith = ReplaceWith("defaultMaxRetryDelay"),
        level = DeprecationLevel.ERROR
    )
    var defaultMaxRepetitionDelay: Long

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

    companion object {
        internal val currentDefaultDatabase = AtomicReference<DatabaseApi>()

        /**
         * The database to use by default in all transactions.
         *
         * **Note** If this value is not set, the last [Database] instance created will be used.
         */
        @Suppress("SpacingBetweenDeclarationsWithAnnotations")
        var defaultDatabase: DatabaseApi?
            @Synchronized get() = currentDefaultDatabase.get() ?: databases.firstOrNull()
            @Synchronized set(value) {
                currentDefaultDatabase.set(value)
            }

        private val databases = ConcurrentLinkedDeque<DatabaseApi>()

        private val registeredDatabases = ConcurrentHashMap<DatabaseApi, TransactionManager>()

        /** Associates the provided [database] with a specific [manager]. */
        @Synchronized
        fun registerManager(database: DatabaseApi, manager: TransactionManager) {
            if (defaultDatabase == null) {
                currentThreadManager.remove()
            }
            if (!registeredDatabases.containsKey(database)) {
                databases.push(database)
            }

            registeredDatabases[database] = manager
        }

        /**
         * Clears any association between the provided [database] and its [TransactionManager],
         * and ensures that the [database] instance will not be available for use in future transactions.
         */
        @Synchronized
        fun closeAndUnregister(database: DatabaseApi) {
            val manager = registeredDatabases[database]
            manager?.let {
                registeredDatabases.remove(database)
                databases.remove(database)
                currentDefaultDatabase.compareAndSet(database, null)
                if (currentThreadManager.isInitialized && currentThreadManager.get() == it) {
                    currentThreadManager.remove()
                }
            }
        }

        /**
         * Returns the [TransactionManager] instance that is associated with the provided [database],
         * or `null` if  a manager has not been registered for the [database].
         *
         * **Note** If the provided [database] is `null`, this will return the current thread's [TransactionManager]
         * instance, which may not be initialized if `Database.connect()` was not called at some point previously.
         */
        fun managerFor(database: DatabaseApi?) = if (database != null) registeredDatabases[database] else manager

        private class TransactionManagerThreadLocal : ThreadLocal<TransactionManager>() {
            var isInitialized = false

            override fun get(): TransactionManager {
                return super.get()
            }

            override fun initialValue(): TransactionManager {
                isInitialized = true
                return defaultDatabase?.let { registeredDatabases.getValue(it) } ?: NotInitializedManager
            }

            override fun set(value: TransactionManager?) {
                isInitialized = true
                super.set(value)
            }

            override fun remove() {
                isInitialized = false
                super.remove()
            }
        }

        private val currentThreadManager = TransactionManagerThreadLocal()

        /** The current thread's [TransactionManager] instance. */
        val manager: TransactionManager
            get() = currentThreadManager.get()

        /** Sets the current thread's copy of the [TransactionManager] instance to the specified [manager]. */
        fun resetCurrent(manager: TransactionManager?) {
            manager?.let { currentThreadManager.set(it) } ?: currentThreadManager.remove()
        }

        /** Returns the current [Transaction], or creates a new transaction with the provided [isolation] level. */
        fun currentOrNew(isolation: Int): Transaction = currentOrNull() ?: manager.newTransaction(isolation)

        /** Returns the current [Transaction], or `null` if none exists. */
        fun currentOrNull(): Transaction? = manager.currentOrNull()

        /**
         * Returns the current [Transaction].
         *
         * @throws [IllegalStateException] If no transaction exists.
         */
        fun current(): Transaction = currentOrNull() ?: error("No transaction in context.")

        /** Whether any [TransactionManager] instance has been initialized by a database. */
        fun isInitialized(): Boolean = defaultDatabase != null
    }
}

@Suppress("TooGenericExceptionCaught")
internal fun TransactionInterface.rollbackLoggingException(log: (Exception) -> Unit) {
    try {
        rollback()
    } catch (e: Exception) {
        log(e)
    }
}

@Suppress("TooGenericExceptionCaught")
internal inline fun TransactionInterface.closeLoggingException(log: (Exception) -> Unit) {
    try {
        close()
    } catch (e: Exception) {
        log(e)
    }
}

/**
 * The [TransactionManager] instance that is associated with this [Database].
 *
 * @throws [RuntimeException] If a manager has not been registered for the database.
 */
@Suppress("TooGenericExceptionThrown")
val DatabaseApi?.transactionManager: TransactionManager
    get() = TransactionManager.managerFor(this)
        ?: throw RuntimeException("Database $this does not have any transaction manager")
