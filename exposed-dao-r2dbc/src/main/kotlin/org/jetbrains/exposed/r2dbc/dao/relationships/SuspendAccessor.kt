package org.jetbrains.exposed.r2dbc.dao.relationships

import org.jetbrains.exposed.r2dbc.dao.R2dbcEntity
import org.jetbrains.exposed.r2dbc.dao.R2dbcEntityClass
import org.jetbrains.exposed.r2dbc.dao.entityCache
import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.IdTable
import org.jetbrains.exposed.v1.r2dbc.transactions.TransactionManager
import kotlin.collections.get
import kotlin.reflect.KProperty

class SuspendAccessor<ID : Any, Parent : R2dbcEntity<ID>, REF : Any>(
    private val reference: Column<REF>,
    private val factory: R2dbcEntityClass<ID, @UnsafeVariance Parent>,
    private val entity: R2dbcEntity<*>
) {
    /**
     * getValue operator - returns this accessor which has invoke() and set operations.
     *
     * This enables both patterns:
     * - `person.city()` - Async get via invoke()
     * - `person.city set cityEntity` - Sync set via infix operator
     */
    operator fun getValue(thisRef: Any?, property: KProperty<*>): SuspendAccessor<ID, Parent, REF> {
        return this
    }

    /**
     * Infix operator for setting the relationship.
     *
     * Usage: `person.city set newCity`
     */
    infix fun set(value: Parent) {
        // Validate entities are from same database
        if (entity.db != value.db) {
            error("Cannot link entities from different databases")
        }

        // Store the reference value - extract from the referenced column
        @Suppress("UNCHECKED_CAST")
        val refValue = when {
            reference.referee == factory.table.id -> {
                // Reference points to the primary key - use the entity's ID
                value.id as REF
            }
            reference.referee?.table == factory.table -> {
                // Reference points to another column in the entity's table
                val refereeColumn = reference.referee!!
                // Try to get value from writeValues first, then readValues
                (value.writeValues[refereeColumn as Column<Any?>] ?: value._readValues?.get(refereeColumn)) as REF
            }
            else -> error("Reference column ${reference.name} does not point to any column in ${factory.table.tableName}")
        }

        entity.writeValues[reference as Column<Any?>] = refValue

        // Schedule update if entity has been flushed
        if (entity.id._value != null) {
            val entityCache = TransactionManager.current().entityCache

            @Suppress("UNCHECKED_CAST")
            val entityTable = reference.table as? IdTable<Any> ?: entity.klass.table as IdTable<Any>
            val contains = entityCache.data[entityTable].orEmpty().contains(entity.id._value)
            if (contains) {
                @Suppress("UNCHECKED_CAST")
                entityCache.scheduleUpdate(entity.klass as R2dbcEntityClass<Any, R2dbcEntity<Any>>, entity as R2dbcEntity<Any>)
            }
        }

        // Store in reference cache
        entity.storeReferenceInCache(reference, value)
    }

    suspend operator fun invoke(): Parent {
        @Suppress("ForbiddenComment")
        // TODO: Implement reference loading similar to OptionalSuspendAccessor
        TODO("Not yet implemented")
    }
}

class OptionalSuspendAccessor<ID : Any, Parent : R2dbcEntity<ID>, REF : Any>(
    private val reference: Column<REF?>,
    private val factory: R2dbcEntityClass<ID, @UnsafeVariance Parent>,
    private val entity: R2dbcEntity<*>
) {
    operator fun getValue(thisRef: Any?, property: KProperty<*>): OptionalSuspendAccessor<ID, Parent, REF> {
        return this
    }

    infix fun set(value: Parent?) {
        if (value != null) {
            // Validate entities are from same database
            if (entity.db != value.db) {
                error("Cannot link entities from different databases")
            }

            // Store the reference value - extract from the referenced column
            @Suppress("UNCHECKED_CAST")
            val refValue = when {
                reference.referee == factory.table.id -> {
                    // Reference points to the primary key - use the entity's ID
                    value.id as REF
                }
                reference.referee?.table == factory.table -> {
                    // Reference points to another column in the entity's table
                    val refereeColumn = reference.referee!!
                    // Try to get value from writeValues first, then readValues
                    (value.writeValues[refereeColumn as Column<Any?>] ?: value._readValues?.get(refereeColumn)) as REF
                }
                else -> error("Reference column ${reference.name} does not point to any column in ${factory.table.tableName}")
            }

            entity.writeValues[reference as Column<Any?>] = refValue
        } else {
            // Clear the reference
            entity.writeValues[reference as Column<Any?>] = null
        }

        // Schedule update if entity has been flushed
        if (entity.id._value != null) {
            val entityCache = TransactionManager.current().entityCache

            @Suppress("UNCHECKED_CAST")
            val entityTable = reference.table as? IdTable<Any> ?: entity.klass.table as IdTable<Any>
            val contains = entityCache.data[entityTable].orEmpty().contains(entity.id._value)
            if (contains) {
                @Suppress("UNCHECKED_CAST")
                entityCache.scheduleUpdate(entity.klass as R2dbcEntityClass<Any, R2dbcEntity<Any>>, entity as R2dbcEntity<Any>)
            }
        }

        entity.storeReferenceInCache(reference, value)
    }

    suspend operator fun invoke(): Parent? {
        if (entity.hasInReferenceCache(reference)) {
            return entity.getReferenceFromCache(reference)
        }

        @Suppress("UNCHECKED_CAST")
        val refValue: REF? = (entity.writeValues[reference as Column<Any?>] as? REF)
            ?: (entity._readValues?.let { row -> row[reference] } as? REF)

        if (refValue == null) {
            entity.storeReferenceInCache(reference, null)
            return null
        }

        @Suppress("UNCHECKED_CAST")
        val parentId = when {
            refValue is EntityID<*> && reference.referee == factory.table.id -> refValue as EntityID<ID>
            else -> error("Reference column ${reference.name} does not point to ${factory.table.id}")
        }

        val parentEntity = factory.findById(parentId)
        entity.storeReferenceInCache(reference, parentEntity)

        return parentEntity
    }
}
