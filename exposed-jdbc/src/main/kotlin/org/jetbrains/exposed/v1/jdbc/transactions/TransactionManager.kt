package org.jetbrains.exposed.v1.jdbc.transactions

import org.jetbrains.exposed.v1.core.InternalApi
import org.jetbrains.exposed.v1.core.Transaction
import org.jetbrains.exposed.v1.core.statements.api.ExposedSavepoint
import org.jetbrains.exposed.v1.core.transactions.DatabasesManagerImpl
import org.jetbrains.exposed.v1.core.transactions.TransactionManagerApi
import org.jetbrains.exposed.v1.core.transactions.TransactionManagersContainerImpl
import org.jetbrains.exposed.v1.core.transactions.TransactionsHolderProvider
import org.jetbrains.exposed.v1.core.transactions.suspend.TransactionContextHolder
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.statements.api.ExposedConnection
import kotlin.coroutines.CoroutineContext

/**
 * [JdbcTransactionManager] implementation registered to the provided database value [db].
 *
 * [setupTxConnection] can be provided to override the default configuration of transaction settings when a
 * connection is retrieved from the database.
 */
class TransactionManager(
    override val db: Database,
    private val setupTxConnection: ((ExposedConnection<*>, JdbcTransactionInterface) -> Unit)? = null
) : JdbcTransactionManager {
    @Volatile
    private var configuredIsolationLevel: Int? = db.config.defaultIsolationLevel.takeUnless { it == -1 }

    @OptIn(InternalApi::class)
    @Volatile
    private var configuredReadOnly: Boolean? = db.config.explicitReadOnly

    @Volatile
    override var defaultMaxAttempts: Int = db.config.defaultMaxAttempts

    @Volatile
    override var defaultMinRetryDelay: Long = db.config.defaultMinRetryDelay

    @Volatile
    override var defaultMaxRetryDelay: Long = db.config.defaultMaxRetryDelay

    /** The default transaction isolation level. Unless specified, the database-specific level will be used. */
    override var defaultIsolationLevel: Int
        get() = configuredIsolationLevel ?: db.dataSourceSettings?.isolationLevel ?: run {
            val isolation = Database.getDefaultIsolationLevel(db)
            // Discovering the dialect may initialize the current transaction's connection and cache its settings.
            db.dataSourceSettings?.isolationLevel ?: isolation
        }
        set(value) {
            configuredIsolationLevel = value.takeUnless { it == -1 }
        }

    override var defaultReadOnly: Boolean
        get() = configuredReadOnly ?: db.dataSourceSettings?.readOnly ?: false
        set(value) {
            configuredReadOnly = value
        }

    override fun toString(): String {
        return "JdbcTransactionManager[${hashCode()}](db=$db)"
    }

    /**
     * Returns a [JdbcTransaction] instance.
     *
     * The returned value may be a new transaction, or it may return the [outerTransaction] if called from within
     * an existing transaction with the database not configured to `useNestedTransactions`.
     */
    override fun newTransaction(
        isolation: Int,
        readOnly: Boolean,
        outerTransaction: JdbcTransaction?
    ): JdbcTransaction = createTransaction(isolation, readOnly, outerTransaction)

    internal fun createTransaction(isolation: Int?, readOnly: Boolean?, outerTransaction: JdbcTransaction?): JdbcTransaction {
        if (outerTransaction != null && !db.useNestedTransactions) return outerTransaction

        val explicitIsolation = isolation?.takeUnless { it == -1 }
        val inheritsFromDataSource = db.connectsViaDataSource && setupTxConnection == null
        return JdbcTransaction(
            ThreadLocalTransaction(
                db = db,
                manager = this,
                setupTxConnection = setupTxConnection,
                requestedIsolation = if (inheritsFromDataSource) {
                    explicitIsolation ?: configuredIsolationLevel
                } else {
                    explicitIsolation ?: defaultIsolationLevel
                },
                requestedReadOnly = if (inheritsFromDataSource) {
                    readOnly ?: configuredReadOnly
                } else {
                    readOnly ?: defaultReadOnly
                },
                inheritsFromDataSource = inheritsFromDataSource,
                outerTransaction = outerTransaction
            )
        )
    }

    companion object {
        @OptIn(InternalApi::class)
        private val databases = object : DatabasesManagerImpl<Database>() {}

        @OptIn(InternalApi::class)
        private val transactionManagers = object : TransactionManagersContainerImpl<Database>(databases) {
            override fun transactionClass(): Class<out Transaction> = JdbcTransaction::class.java
        }

        /**
         * Storage for coroutine context keys associated with each transaction manager.
         * Each transaction manager gets a unique context key for transaction isolation.
         */
        private val contextKeys =
            mutableMapOf<JdbcTransactionManager, CoroutineContext.Key<TransactionContextHolder>>()

        /**
         * Returns the context key for the given transaction manager.
         * @suppress
         */
        @InternalApi
        fun getContextKey(manager: JdbcTransactionManager): CoroutineContext.Key<TransactionContextHolder> {
            return contextKeys[manager]
                ?: error("No context key found for transaction manager $manager. Ensure the transaction manager is registered.")
        }

        /**
         * The currently active database, which is either the default database or the last instance created.
         * Returns `null` if no database has been registered.
         */
        val primaryDatabase: Database?
            get() = databases.getPrimaryDatabase()

        /**
         * The database to use by default in all transactions.
         *
         * **Note:** The default database could be null until it is set explicitly.
         * Use `primaryDatabase` to get the default database if it is set, or the last registered database otherwise.
         */
        var defaultDatabase: Database?
            get() = databases.getDefaultDatabase()
            set(value) = databases.setDefaultDatabase(value)

        /** Associates the provided [database] with a specific [manager]. */
        @Synchronized
        fun registerManager(database: Database, manager: JdbcTransactionManager) {
            contextKeys[manager] = object : CoroutineContext.Key<TransactionContextHolder> {}
            transactionManagers.registerDatabaseManager(database, manager)
        }

        /**
         * Clears any association between the provided [database] and its [TransactionManager],
         * and ensures that the [database] instance will not be available for use in future transactions.
         */
        @Synchronized
        fun closeAndUnregister(database: Database) {
            val manager = transactionManagers.getTransactionManager(database)
            if (manager != null) {
                contextKeys.remove(manager)
            }
            transactionManagers.closeAndUnregisterDatabase(database)
        }

        /** Returns the current [JdbcTransaction], or `null` if none exists. */
        fun currentOrNull(): JdbcTransaction? {
            @OptIn(InternalApi::class)
            return TransactionsHolderProvider.holder.getTransactionIsInstance(JdbcTransaction::class.java)
        }

        /**
         * Returns the current [JdbcTransaction].
         *
         * @throws IllegalStateException If no transaction exists.
         */
        fun current(): JdbcTransaction = currentOrNull()
            ?: error("No transaction in context.")

        /**
         * Returns the [JdbcTransactionManager] instance associated with the provided [database].
         *
         * @param database Database instance for which to retrieve the transaction manager.
         * @return The [JdbcTransactionManager] associated with the database.
         * @throws IllegalStateException if no transaction manager is registered for the given database.
         */
        fun managerFor(database: Database): JdbcTransactionManager =
            transactionManagers.getTransactionManager(database)?.let { it as JdbcTransactionManager } ?: error("No transaction manager for db $database")

        /**
         * Returns the [JdbcTransactionManager] for the current context.
         *
         * This property attempts to resolve the transaction manager in the following order:
         * 1. From the current transaction, if one exists
         * 2. From the current database, if one is set
         *
         * @throws IllegalStateException if no transaction manager can be found in either the current
         *         transaction or the current database.
         */
        val manager: JdbcTransactionManager
            get() = currentOrNull()?.transactionManager
                ?: primaryDatabase?.transactionManager
                ?: error("No transaction manager found")

        /**
         * Returns the current [JdbcTransaction], or creates a new transaction with the provided [isolation] level.
         * An [isolation] of -1 uses the transaction manager or DataSource defaults.
         */
        fun currentOrNew(isolation: Int = -1): JdbcTransaction = currentOrNull()
            ?: manager.newTransactionWithDefaults(isolation)
    }

    private class ThreadLocalTransaction(
        override val db: Database,
        private val manager: TransactionManager,
        private val setupTxConnection: ((ExposedConnection<*>, JdbcTransactionInterface) -> Unit)?,
        private val requestedIsolation: Int?,
        private val requestedReadOnly: Boolean?,
        private val inheritsFromDataSource: Boolean,
        override val outerTransaction: JdbcTransaction?
    ) : JdbcTransactionInterface {
        private var appliedIsolation: Int? = null
        private var appliedReadOnly: Boolean? = null

        override val transactionIsolation: Int
            get() = outerTransaction?.transactionIsolation ?: appliedIsolation ?: requestedIsolation ?: manager.defaultIsolationLevel

        override val readOnly: Boolean
            get() = outerTransaction?.readOnly ?: appliedReadOnly ?: requestedReadOnly ?: manager.defaultReadOnly

        private val connectionLazy = lazy(LazyThreadSafetyMode.NONE) {
            outerTransaction?.connection ?: db.connector().apply {
                @Suppress("TooGenericExceptionCaught")
                try {
                    setupTxConnection?.invoke(this, this@ThreadLocalTransaction) ?: run {
                        // The order of setters here is important.
                        // Transaction isolation should go first as readOnly or autoCommit can start transaction with wrong isolation level
                        // Some drivers start a transaction right after `setAutoCommit(false)`,
                        // which makes `setReadOnly` throw an exception if it is called after `setAutoCommit`
                        val defaults = if (inheritsFromDataSource) db.dataSourceSettings(this) else null
                        val isolation = requestedIsolation ?: defaults?.isolationLevel ?: manager.defaultIsolationLevel
                        val readOnly = requestedReadOnly ?: defaults?.readOnly ?: manager.defaultReadOnly
                        if (defaults == null || isolation != defaults.isolationLevel) this.transactionIsolation = isolation
                        if (defaults == null || readOnly != defaults.readOnly) this.readOnly = readOnly
                        autoCommit = false
                        appliedIsolation = isolation
                        appliedReadOnly = readOnly
                    }
                } catch (e: Exception) {
                    try {
                        close()
                    } catch (closeException: Exception) {
                        e.addSuppressed(closeException)
                    }
                    throw e
                }
            }
        }

        override val connection: ExposedConnection<*>
            get() = connectionLazy.value

        private val useSavePoints = outerTransaction != null && db.useNestedTransactions
        private var savepoint: ExposedSavepoint? = if (useSavePoints) connection.setSavepoint(savepointName) else null

        override fun commit() {
            if (connectionLazy.isInitialized()) {
                if (!useSavePoints) {
                    connection.commit()
                } else {
                    // do nothing in nested. close() will commit everything and release savepoint.
                }
            }
        }

        override fun rollback() {
            if (connectionLazy.isInitialized() && !connection.isClosed) {
                if (useSavePoints && savepoint != null) {
                    connection.rollback(savepoint!!)
                    savepoint = connection.setSavepoint(savepointName)
                } else {
                    connection.rollback()
                }
            }
        }

        override fun close() {
            if (!useSavePoints) {
                if (connectionLazy.isInitialized()) connection.close()
            } else {
                savepoint?.let {
                    connection.releaseSavepoint(it)
                    savepoint = null
                }
            }
        }

        private val savepointName: String
            get() {
                var nestedLevel = 0
                var currenTransaction = outerTransaction
                while (currenTransaction?.outerTransaction != null) {
                    nestedLevel++
                    currenTransaction = currenTransaction.outerTransaction
                }
                return "Exposed_savepoint_$nestedLevel"
            }
        override val transactionManager: TransactionManagerApi
            get() = db.transactionManager
    }
}
