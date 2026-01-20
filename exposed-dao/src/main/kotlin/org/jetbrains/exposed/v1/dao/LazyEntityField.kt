package org.jetbrains.exposed.v1.dao

import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.dao.exceptions.EntityNotFoundException
import org.jetbrains.exposed.v1.dao.exceptions.LazyColumnAccessOutsideTransactionException
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager
import kotlin.reflect.KProperty

/**
 * Class responsible for enabling lazy loading of [Entity] fields.
 *
 * Lazy fields are excluded from the default entity query and loaded on-demand when first accessed.
 * This is useful for large BLOB/TEXT columns or rarely accessed data to improve performance.
 *
 * @param column The column that will be lazily loaded
 * @sample org.jetbrains.exposed.v1.tests.shared.entities.LazyEntityFieldTests.testLazyFieldNotLoadedInitially
 */
class LazyEntityField<T>(
    /** The column that will be lazily loaded */
    val column: Column<T>
) {
    companion object {
        /**
         * Registry of lazy columns organized by table.
         * Maps each table to a set of its lazy columns.
         * This allows EntityClass to efficiently filter out lazy columns for its specific table.
         */
        internal val lazyColumns = mutableMapOf<Table, MutableSet<Column<*>>>()
    }

    /**
     * Gets the value of this lazy field for the given entity.
     *
     * On first access:
     * 1. Checks for active transaction
     * 2. Flushes entity if it has pending writes
     * 3. Loads this lazy column value from the database
     * 4. Caches the lazy value for subsequent accesses
     *
     * @throws LazyColumnAccessOutsideTransactionException if accessed outside a transaction
     * @throws EntityNotFoundException if the entity no longer exists in the database
     */
    operator fun <ID : Any> getValue(entity: Entity<ID>, property: KProperty<*>): T {
        @Suppress("UNCHECKED_CAST")
        @OptIn(InternalApi::class)
        if ((column as Column<Any?>) in entity.writeValues) {
            return entity.writeValues[column as Column<Any?>] as T
        }

        @OptIn(InternalApi::class)
        if (entity.isLazyFieldCached(column)) {
            @Suppress("UNCHECKED_CAST")
            return entity.getLazyFieldValue(column) as T
        }

        // Check transaction - lazy fields always require a transaction
        if (TransactionManager.currentOrNull() == null) {
            throw LazyColumnAccessOutsideTransactionException(column)
        }

        // Flush entity if it has pending writes, to ensure it exists in database
        if (entity.writeValues.isNotEmpty()) {
            entity.flush()
        }

        // Load the column value from database
        val query = entity.klass.table.select(column).where { entity.klass.table.id eq entity.id }
        val row = query.firstOrNull() ?: throw EntityNotFoundException(entity.id, entity.klass)
        val value: T = row[column]

        @OptIn(InternalApi::class)
        entity.storeLazyFieldValue(column, value)

        return value
    }

    /**
     * Sets the value of this lazy field for the given entity.
     *
     * The value is stored in the entity's write values and will be persisted on flush.
     */
    operator fun <ID : Any> setValue(entity: Entity<ID>, property: KProperty<*>, value: T) {
        with(entity) {
            column.setValue(entity, property, value)
        }
    }
}
