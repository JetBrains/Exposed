package org.jetbrains.exposed.r2dbc.dao

import org.jetbrains.exposed.v1.r2dbc.transactions.TransactionManager

/**
 * Wrapper around a newly created [Entity] that has not yet been flushed to the database.
 *
 * After [EntityClass.new], the entity's auto-generated ID is not yet available.
 * This wrapper makes the initializedEntity state visible in the type system and provides
 * [flush] to persist the entity and obtain its ID.
 *
 * Usage:
 * ```kotlin
 * val broker = Broker.new { name = "Alpha" }.flush()
 * broker.id.value  // works
 * ```
 */
@ExperimentalR2dbcDaoApi
class NewEntity<ID : Any, out T : Entity<ID>>(val initializedEntity: T) {

    /**
     * Flushes the entity to the database, ensuring its auto-generated ID is populated.
     * Returns the underlying entity with all database-generated values available.
     */
    suspend fun flush(): T {
        TransactionManager.current().entityCache.flush()
        return initializedEntity
    }
}
