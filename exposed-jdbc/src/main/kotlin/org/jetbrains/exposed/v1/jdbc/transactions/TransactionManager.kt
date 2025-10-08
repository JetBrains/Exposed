package org.jetbrains.exposed.v1.jdbc.transactions

import kotlinx.coroutines.currentCoroutineContext
import org.jetbrains.exposed.v1.core.InternalApi
import org.jetbrains.exposed.v1.core.Transaction
import org.jetbrains.exposed.v1.core.statements.api.ExposedSavepoint
import org.jetbrains.exposed.v1.core.transactions.DatabasesManagerImpl
import org.jetbrains.exposed.v1.core.transactions.ThreadLocalTransactionsStack
import org.jetbrains.exposed.v1.core.transactions.TransactionManagerApi
import org.jetbrains.exposed.v1.core.transactions.TransactionManagersContainerImpl
import org.jetbrains.exposed.v1.core.transactions.suspend.TransactionContextElement
import org.jetbrains.exposed.v1.core.transactions.suspend.TransactionContextHolder
import org.jetbrains.exposed.v1.core.transactions.suspend.TransactionContextHolderImpl
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.statements.api.ExposedConnection
import kotlin.coroutines.CoroutineContext

/**
 * [TransactionManager] implementation registered to the provided database value [db].
 *
 * [setupTxConnection] can be provided to override the default configuration of transaction settings when a
 * connection is retrieved from the database.
 */
class TransactionManager(
    private val db: Database,
    private val setupTxConnection: ((ExposedConnection<*>, JdbcTransactionInterface) -> Unit)? = null
) : TransactionManagerApi {
    @Volatile
    override var defaultMaxAttempts: Int = db.config.defaultMaxAttempts

    @Volatile
    override var defaultMinRetryDelay: Long = db.config.defaultMinRetryDelay

    @Volatile
    override var defaultMaxRetryDelay: Long = db.config.defaultMaxRetryDelay

    /** The default transaction isolation level. Unless specified, the database-specific level will be used. */
    @Volatile
    var defaultIsolationLevel: Int = db.config.defaultIsolationLevel
        get() {
            when {
                field == -1 -> {
                    if (db.connectsViaDataSource) loadDataSourceIsolationLevel = true
                    field = Database.getDefaultIsolationLevel(db)
                }
                db.connectsViaDataSource && loadDataSourceIsolationLevel -> {
                    if (db.dataSourceIsolationLevel != -1) {
                        loadDataSourceIsolationLevel = false
                        field = db.dataSourceIsolationLevel
                    }
                }
            }
            return field
        }

    /**
     * Whether the transaction isolation level of the underlying DataSource should be retrieved from the database.
     *
     * This should only be set to `true` if [Database.connectsViaDataSource] has also been set to `true` and if
     * an initial connection to the database has not already been made.
     */
    private var loadDataSourceIsolationLevel = false

    @Volatile
    override var defaultReadOnly: Boolean = db.config.defaultReadOnly

    override fun toString(): String {
        return "JdbcTransactionManager[${hashCode()}](db=$db)"
    }

    /**
     * Returns a [JdbcTransaction] instance.
     *
     * The returned value may be a new transaction, or it may return the [outerTransaction] if called from within
     * an existing transaction with the database not configured to `useNestedTransactions`.
     */
    fun newTransaction(
        isolation: Int = defaultIsolationLevel,
        readOnly: Boolean = defaultReadOnly,
        outerTransaction: JdbcTransaction? = null
    ): JdbcTransaction {
        val transaction = outerTransaction?.takeIf { !db.useNestedTransactions }
            ?: JdbcTransaction(
                ThreadLocalTransaction(
                    db = db,
                    readOnly = outerTransaction?.readOnly ?: readOnly,
                    transactionIsolation = outerTransaction?.transactionIsolation ?: isolation,
                    setupTxConnection = setupTxConnection,
                    outerTransaction = outerTransaction,
                    loadDataSourceIsolationLevel = loadDataSourceIsolationLevel,
                ),
            )

        return transaction
    }

    @OptIn(InternalApi::class)
    override fun currentOrNull(): JdbcTransaction? =
        ThreadLocalTransactionsStack.getTransactionOrNull(db) as JdbcTransaction?

    /** A unique key for storing coroutine context elements, as [TransactionContextHolder]. */
    private val contextKey = object : CoroutineContext.Key<TransactionContextHolder> {}

    @OptIn(InternalApi::class)
    override fun createTransactionContext(transaction: Transaction): CoroutineContext {
        if (transaction.transactionManager != this) {
            error(
                "TransactionManager must create transaction context only for own transactions. " +
                    "Transaction manager of ${db.url} tried to create transaction context for ${transaction.db.url}"
            )
        }
        return TransactionContextHolderImpl(transaction, contextKey) + TransactionContextElement(transaction)
    }

    /**
     * Returns the current JDBC transaction from the coroutine context, or null if none exists.
     *
     * This method performs type checking to ensure the transaction in the context is actually
     * a [JdbcTransaction]. If a non-JDBC transaction is found in the context, an error is thrown
     * to prevent type confusion between JDBC and R2DBC transactions.
     *
     * @return The current [JdbcTransaction] from the coroutine context, or null if no transaction exists
     * @throws [IllegalStateException] If the transaction in the context is not a [JdbcTransaction]
     */
    internal suspend fun getCurrentContextTransaction(): JdbcTransaction? {
        val transaction = currentCoroutineContext()[contextKey]?.transaction
        return when {
            transaction == null -> null
            transaction is JdbcTransaction -> transaction
            else -> error(
                "Expected JdbcTransaction in coroutine context but found ${transaction::class.simpleName}. " +
                    "This may indicate mixing JDBC and R2DBC transactions incorrectly."
            )
        }
    }

    companion object {
        @OptIn(InternalApi::class)
        private val databases = object : DatabasesManagerImpl<Database>() {}

        @OptIn(InternalApi::class)
        private val transactionManagers = object : TransactionManagersContainerImpl<Database>(databases) {}

        /**
         * The currently active database, which is either the default database or the last instance created.
         * Returns `null` if no database has been registered.
         */
        val currentDatabase: Database?
            get() = databases.getCurrentDatabase()

        /**
         * The database to use by default in all transactions.
         *
         * **Note** If this value is not set, the last [Database] instance created will be used.
         */
        var defaultDatabase: Database?
            get() = databases.getDefaultDatabase()
            set(value) = databases.setDefaultDatabase(value)

        /**
         * The current transaction manager associated with the active transaction or database.
         * Returns `null` if no transaction is active and no database has been registered.
         */
        val currentTransactionManager
            get() = transactionManagers.getCurrentTransactionManagerOrNull()

        /** Associates the provided [database] with a specific [manager]. */

        @Synchronized
        fun registerManager(database: Database, manager: TransactionManagerApi) {
            @OptIn(InternalApi::class)
            transactionManagers.registerDatabaseManager(database, manager)
        }

        /**
         * Clears any association between the provided [database] and its [TransactionManager],
         * and ensures that the [database] instance will not be available for use in future transactions.
         */
        @Synchronized
        fun closeAndUnregister(database: Database) {
            @OptIn(InternalApi::class)
            transactionManagers.closeAndUnregisterDatabase(database)
        }

        /** Returns the current [JdbcTransaction], or `null` if none exists. */
        @OptIn(InternalApi::class)
        fun currentOrNull(): JdbcTransaction? =
            ThreadLocalTransactionsStack.getTransactionOrNull() as JdbcTransaction?

        /**
         * Returns the current [JdbcTransaction].
         *
         * @throws IllegalStateException If no transaction exists.
         */
        fun current(): JdbcTransaction = currentOrNull()
            ?: error("No transaction in context.")

        /**
         * Returns the [TransactionManager] instance associated with the provided [db].
         *
         * @param db Database instance for which to retrieve the transaction manager.
         * @return The [TransactionManager] associated with the database.
         */
        fun getTransactionManager(db: Database): TransactionManager =
            transactionManagers.getTransactionManager(db) as TransactionManager
    }

    private class ThreadLocalTransaction(
        override val db: Database,
        private val setupTxConnection: ((ExposedConnection<*>, JdbcTransactionInterface) -> Unit)?,
        override val transactionIsolation: Int,
        override val readOnly: Boolean,
        override val outerTransaction: JdbcTransaction?,
        private val loadDataSourceIsolationLevel: Boolean
    ) : JdbcTransactionInterface {

        private val connectionLazy = lazy(LazyThreadSafetyMode.NONE) {
            outerTransaction?.connection ?: db.connector().apply {
                @Suppress("TooGenericExceptionCaught")
                try {
                    setupTxConnection?.invoke(this, this@ThreadLocalTransaction) ?: run {
                        // The order of setters here is important.
                        // Transaction isolation should go first as readOnly or autoCommit can start transaction with wrong isolation level
                        // Some drivers start a transaction right after `setAutoCommit(false)`,
                        // which makes `setReadOnly` throw an exception if it is called after `setAutoCommit`
                        if (db.connectsViaDataSource && loadDataSourceIsolationLevel && db.dataSourceIsolationLevel == -1) {
                            // retrieves the setting of the datasource connection & caches it
                            db.dataSourceIsolationLevel = transactionIsolation
                            db.dataSourceReadOnly = readOnly
                        } else if (
                            !db.connectsViaDataSource ||
                            db.dataSourceIsolationLevel != this@ThreadLocalTransaction.transactionIsolation ||
                            db.dataSourceReadOnly != this@ThreadLocalTransaction.readOnly
                        ) {
                            // only set the level if there is no cached datasource value or if the value differs
                            transactionIsolation = this@ThreadLocalTransaction.transactionIsolation
                            readOnly = this@ThreadLocalTransaction.readOnly
                        }
                        autoCommit = false
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
