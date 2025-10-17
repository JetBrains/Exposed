package org.jetbrains.exposed.v1.core.transactions

import org.jetbrains.exposed.v1.core.DatabaseApi
import org.jetbrains.exposed.v1.core.InternalApi
import java.util.concurrent.ConcurrentLinkedDeque
import java.util.concurrent.atomic.AtomicReference

/**
 * Manager interface for database instances of type [DB].
 */
@InternalApi
interface DatabasesManager<DB : DatabaseApi> {
    /** Returns the database that has been set as the default for all transactions. */
    fun getDefaultDatabase(): DB?

    /** Sets the provided [db] as the default database for all transactions. */
    fun setDefaultDatabase(db: DB?)

    /** Returns the current database, which is either the default database or the last instance created. */
    fun getCurrentDatabase(): DB?

    /** Adds the provided [db] to the list of registered databases. */
    fun addDatabase(db: DB)

    /** Removes the provided [db] from the list of registered databases. */
    fun removeDatabase(db: DB)
}

@InternalApi
abstract class DatabasesManagerImpl<DB : DatabaseApi> : DatabasesManager<DB> {
    private val databases = ConcurrentLinkedDeque<DB>()

    private val currentDefaultDatabase = AtomicReference<DB>()

    /** Returns the database that has been set as the default for all transactions. */
    override fun getDefaultDatabase(): DB? = currentDefaultDatabase.get()

    /** Sets the provided [db] as the default database for all transactions. */
    override fun setDefaultDatabase(db: DB?) {
        currentDefaultDatabase.set(db)
    }

    /**
     * Returns the database that has been set as the default for all transactions, or, if none was set,
     * the last instance created.
     */
    override fun getCurrentDatabase(): DB? = getDefaultDatabase() ?: databases.firstOrNull()

    /** Adds the provided [db] to the list of registered databases if it is not already present. */
    override fun addDatabase(db: DB) {
        if (!databases.contains(db)) {
            databases.push(db)
        }
    }

    /** Removes the provided [db] from the list of registered databases and clears it as default if it was set as such. */
    override fun removeDatabase(db: DB) {
        databases.remove(db)

        currentDefaultDatabase.compareAndSet(db, null)
    }
}
