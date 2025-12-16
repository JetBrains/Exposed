package org.jetbrains.exposed.v1.r2dbc.transactions

import io.r2dbc.spi.IsolationLevel
import org.jetbrains.exposed.v1.core.InternalApi
import org.jetbrains.exposed.v1.core.Transaction
import org.jetbrains.exposed.v1.core.statements.api.ExposedSavepoint
import org.jetbrains.exposed.v1.core.transactions.DatabasesManagerImpl
import org.jetbrains.exposed.v1.core.transactions.ThreadLocalTransactionsStack
import org.jetbrains.exposed.v1.core.transactions.TransactionManagerApi
import org.jetbrains.exposed.v1.core.transactions.TransactionManagersContainerImpl
import org.jetbrains.exposed.v1.core.transactions.suspend.TransactionContextHolder
import org.jetbrains.exposed.v1.r2dbc.R2dbcDatabase
import org.jetbrains.exposed.v1.r2dbc.R2dbcDatabaseConfig
import org.jetbrains.exposed.v1.r2dbc.R2dbcTransaction
import org.jetbrains.exposed.v1.r2dbc.statements.api.R2dbcExposedConnection
import kotlin.coroutines.CoroutineContext

/**
 * [R2dbcTransactionManager] implementation registered to the provided database value [db].
 *
 * [setupTxConnection] can be provided to override the default configuration of transaction settings when a
 * connection is retrieved from the database.
 */
class TransactionManager(
    override val db: R2dbcDatabase,
    private val setupTxConnection:
    ((R2dbcExposedConnection<*>, R2dbcTransactionInterface) -> Unit)? = null
) : R2dbcTransactionManager {
    override var defaultMaxAttempts: Int = db.config.defaultMaxAttempts

    override var defaultMinRetryDelay: Long = db.config.defaultMinRetryDelay

    override var defaultMaxRetryDelay: Long = db.config.defaultMaxRetryDelay

    override var defaultIsolationLevel: IsolationLevel? = (db.config as R2dbcDatabaseConfig).defaultR2dbcIsolationLevel
        get() = if (field == null) R2dbcDatabase.getDefaultIsolationLevel(db) else field

    override var defaultReadOnly: Boolean = db.config.defaultReadOnly

    override fun toString(): String {
        return "R2dbcTransactionManager[${hashCode()}](db=$db)"
    }

    override fun newTransaction(
        isolation: IsolationLevel?,
        readOnly: Boolean?,
        outerTransaction: R2dbcTransaction?
    ): R2dbcTransaction {
        val transaction = outerTransaction?.takeIf { !db.useNestedTransactions }
            ?: R2dbcTransaction(
                R2dbcLocalTransaction(
                    db = db,
                    readOnly = outerTransaction?.readOnly ?: readOnly ?: false,
                    transactionIsolation = outerTransaction?.transactionIsolation ?: isolation,
                    setupTxConnection = setupTxConnection,
                    outerTransaction = outerTransaction,
                ),
            )

        return transaction
    }

    companion object {
        @OptIn(InternalApi::class)
        private val databases = object : DatabasesManagerImpl<R2dbcDatabase>() {}

        @OptIn(InternalApi::class)
        private val transactionManagers = object : TransactionManagersContainerImpl<R2dbcDatabase>(databases) {
            override fun transactionClass(): Class<out Transaction> = R2dbcTransaction::class.java
        }

        /**
         * Storage for coroutine context keys associated with each transaction manager.
         * Each transaction manager gets a unique context key for transaction isolation.
         */
        private val contextKeys =
            mutableMapOf<R2dbcTransactionManager, CoroutineContext.Key<TransactionContextHolder>>()

        /**
         * Returns the context key for the given transaction manager.
         * @suppress
         */
        @InternalApi
        fun getContextKey(manager: R2dbcTransactionManager): CoroutineContext.Key<TransactionContextHolder> {
            return contextKeys[manager]
                ?: error("No context key found for transaction manager $manager. Ensure the transaction manager is registered.")
        }

        /**
         * The currently active database, which is either the default database or the last instance created.
         * Returns `null` if no database has been registered.
         */
        val primaryDatabase: R2dbcDatabase?
            get() = databases.getPrimaryDatabase()

        /**
         * The database to use by default in all transactions.
         *
         * **Note:** The default database could be null until it is set explicitly.
         * Use `primaryDatabase` to get the default database if it is set, or the last registered database otherwise.
         */
        var defaultDatabase: R2dbcDatabase?
            get() = databases.getDefaultDatabase()
            set(value) = databases.setDefaultDatabase(value)

        /** Associates the provided [database] with a specific [manager]. */
        @Synchronized
        fun registerManager(database: R2dbcDatabase, manager: R2dbcTransactionManager) {
            contextKeys[manager] = object : CoroutineContext.Key<TransactionContextHolder> {}
            @OptIn(InternalApi::class)
            transactionManagers.registerDatabaseManager(database, manager)
        }

        /**
         * Clears any association between the provided [database] and its [TransactionManager],
         * and ensures that the [database] instance will not be available for use in future transactions.
         */
        @Synchronized
        fun closeAndUnregister(database: R2dbcDatabase) {
            @OptIn(InternalApi::class)
            val manager = transactionManagers.getTransactionManager(database)
            if (manager != null) {
                contextKeys.remove(manager)
            }
            @OptIn(InternalApi::class)
            transactionManagers.closeAndUnregisterDatabase(database)
        }

        /** Returns the current [R2dbcTransaction], or `null` if none exists. */
        fun currentOrNull(): R2dbcTransaction? {
            @OptIn(InternalApi::class)
            return ThreadLocalTransactionsStack.getTransactionIsInstance(R2dbcTransaction::class.java)
        }

        /**
         * Returns the current [R2dbcTransaction].
         *
         * @throws [IllegalStateException] If no transaction exists.
         */
        fun current(): R2dbcTransaction = currentOrNull() ?: error("No transaction in context.")

        /**
         * Returns the [R2dbcTransactionManager] instance associated with the provided [database].
         *
         * @param database Database instance for which to retrieve the transaction manager.
         * @return The [R2dbcTransactionManager] associated with the database.
         * @throws IllegalStateException if no transaction manager is registered for the given database.
         */
        fun managerFor(database: R2dbcDatabase): R2dbcTransactionManager =
            transactionManagers.getTransactionManager(database)?.let { it as R2dbcTransactionManager } ?: error("No transaction manager for db $database")

        /**
         * Returns the [R2dbcTransactionManager] for the current context.
         *
         * This property attempts to resolve the transaction manager in the following order:
         * 1. From the current transaction, if one exists
         * 2. From the current database, if one is set
         *
         * @throws IllegalStateException if no transaction manager can be found in either the current
         *         transaction or the current database.
         */
        val manager: R2dbcTransactionManager
            get() = currentOrNull()?.transactionManager
                ?: primaryDatabase?.transactionManager
                ?: error("No transaction manager found")

        /** Returns the current [R2dbcTransaction], or creates a new transaction with the provided [isolation] level. */
        fun currentOrNew(isolation: IsolationLevel? = null): R2dbcTransaction = currentOrNull()
            ?: manager.newTransaction(isolation)
    }

    private class R2dbcLocalTransaction(
        override val db: R2dbcDatabase,
        private val setupTxConnection:
        ((R2dbcExposedConnection<*>, R2dbcTransactionInterface) -> Unit)?,
        override val transactionIsolation: IsolationLevel?,
        override val readOnly: Boolean,
        override val outerTransaction: R2dbcTransaction?,
    ) : R2dbcTransactionInterface {

        override val transactionManager: TransactionManagerApi
            get() = db.transactionManager

        private var connectionLazy: R2dbcExposedConnection<*>? = null

        @Suppress("NestedBlockDepth")
        private suspend fun getConnection(): R2dbcExposedConnection<*> = outerTransaction?.connection()
            ?.also {
                if (useSavePoints) {
                    savepoint = it.setSavepoint(savepointName)
                }
            }
            ?: db.connector().apply {
                @Suppress("TooGenericExceptionCaught")
                try {
                    setupTxConnection?.invoke(this, this@R2dbcLocalTransaction) ?: run {
                        setTransactionDefinition(
                            R2dbcTransactionDefinition(
                                this@R2dbcLocalTransaction.transactionIsolation,
                                this@R2dbcLocalTransaction.readOnly,
                            )
                        )
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

        override suspend fun connection(): R2dbcExposedConnection<*> = connectionLazy
            ?: getConnection().also { connectionLazy = it }

        private val useSavePoints = outerTransaction != null && db.useNestedTransactions

        private var savepoint: ExposedSavepoint? = null

        override suspend fun commit() {
            if (connectionLazy.isInitialized()) {
                if (!useSavePoints) {
                    connection().commit()
                } else {
                    // do nothing in nested. close() will commit everything and release savepoint.
                }
            }
        }

        override suspend fun rollback() {
            if (connectionLazy.isInitialized() && !connection().isClosed()) {
                if (useSavePoints && savepoint != null) {
                    connection().rollback(savepoint!!)
                    savepoint = connection().setSavepoint(savepointName)
                } else {
                    connection().rollback()
                }
            }
        }

        override suspend fun close() {
            if (!useSavePoints) {
                if (connectionLazy.isInitialized()) connection().close()
            } else {
                savepoint?.let {
                    connection().releaseSavepoint(it)
                    savepoint = null
                }
            }
        }

        private fun R2dbcExposedConnection<*>?.isInitialized(): Boolean = this != null

        private val savepointName: String
            get() {
                var nestedLevel = 0
                var currentTransaction = outerTransaction
                while (currentTransaction?.outerTransaction != null) {
                    nestedLevel++
                    currentTransaction = currentTransaction.outerTransaction
                }
                return "Exposed_savepoint_$nestedLevel"
            }
    }
}
