package org.jetbrains.exposed.v1.core.transactions

import org.jetbrains.exposed.v1.core.DatabaseApi
import org.jetbrains.exposed.v1.core.InternalApi
import org.jetbrains.exposed.v1.core.Transaction
import java.util.concurrent.ConcurrentHashMap

/**
 * Container interface for managing transaction managers associated with database instances.
 */
internal interface TransactionManagersContainer<DB : DatabaseApi> {
    /** Returns the transaction manager for the specified [db], or null if not registered. */
    fun getTransactionManager(db: DB): TransactionManagerApi?

    /** Returns the current transaction manager. Throws an exception if no database is registered. */
    fun getCurrentTransactionManager(): TransactionManagerApi

    /** Returns the current transaction manager, or null if no database is registered. */
    fun getCurrentTransactionManagerOrNull(): TransactionManagerApi?

    /** Registers the provided [manager] for the specified [db]. */
    fun registerDatabaseManager(db: DB, manager: TransactionManagerApi)

    /** Closes and unregisters the specified [db] and its transaction manager. */
    fun closeAndUnregisterDatabase(db: DB)
}

/**
 * Base implementation of [TransactionManagersContainer] that manages transaction managers for database instances.
 *
 * @property databases The database manager used to track registered databases.
 * @suppress
 */
@InternalApi
abstract class TransactionManagersContainerImpl<DB : DatabaseApi>(
    val databases: DatabasesManager<DB>
) : TransactionManagersContainer<DB> {
    private val registeredDatabases = ConcurrentHashMap<DatabaseApi, TransactionManagerApi>()

    /**
     * Returns the current transaction manager, which is derived from either:
     * 1. The transaction manager of the active transaction (if any exists), or
     * 2. The transaction manager of the current database (if registered)
     *
     * @return The current transaction manager
     * @throws IllegalStateException If no transaction manager is available (no active transaction and no registered database)
     */
    override fun getCurrentTransactionManager(): TransactionManagerApi {
        return getCurrentTransactionManagerOrNull()
            ?: throw IllegalStateException(
                "No transaction manager available: no transaction is active and no database has been registered. " +
                    "Please call Database.connect() or R2dbcDatabase.connect() first."
            )
    }

    /**
     * This method should return the class of the transaction that this manager supports.
     *
     * This guarantees that the correct transaction manager will be used for the current transaction.
     */
    protected abstract fun transactionClass(): Class<out Transaction>

    /**
     * Returns the current transaction manager if available, or null otherwise.
     *
     * Resolution order:
     * 1. If there's an active transaction, returns its transaction manager
     * 2. If there's a current database, returns its registered transaction manager
     * 3. Otherwise returns null
     *
     * @return The current transaction manager, or null if none is available
     */
    @OptIn(InternalApi::class)
    override fun getCurrentTransactionManagerOrNull(): TransactionManagerApi? {
        return ThreadLocalTransactionsStack.getTransactionIsInstance(transactionClass())?.transactionManager
            ?: databases.getPrimaryDatabase()?.let { getTransactionManager(it) }
    }

    /**
     * Returns the transaction manager instance that is associated with the provided database key,
     * or `null` if a manager has not been registered for the database.
     */
    override fun getTransactionManager(db: DB): TransactionManagerApi? = registeredDatabases[db]

    /**
     * Registers the provided [manager] for the specified [db], adding the database to the managed collection
     * and associating it with the transaction manager.
     */
    override fun registerDatabaseManager(db: DB, manager: TransactionManagerApi) {
        databases.addDatabase(db)

        registeredDatabases[db] = manager
    }

    /**
     * Clears any association between the provided database instance and its transaction manager,
     * and completely removes the database instance from the internal storage.
     */
    override fun closeAndUnregisterDatabase(db: DB) {
        registeredDatabases.remove(db)
        databases.removeDatabase(db)
    }
}
