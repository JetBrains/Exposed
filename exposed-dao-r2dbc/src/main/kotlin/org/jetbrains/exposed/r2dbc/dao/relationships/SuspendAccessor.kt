package org.jetbrains.exposed.r2dbc.dao.relationships

import kotlinx.coroutines.flow.singleOrNull
import org.jetbrains.exposed.r2dbc.dao.R2dbcEntity
import org.jetbrains.exposed.r2dbc.dao.R2dbcEntityClass
import org.jetbrains.exposed.r2dbc.dao.entityCache
import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.EntityIDColumnType
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.IdTable
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.r2dbc.transactions.TransactionManager
import kotlin.collections.get
import kotlin.reflect.KProperty

class SuspendAccessor<ID : Any, Parent : R2dbcEntity<ID>, REF : Any>(
    internal val reference: Column<REF>,
    internal val factory: R2dbcEntityClass<ID, @UnsafeVariance Parent>,
    internal val entity: R2dbcEntity<*>
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
        if (entity.hasInReferenceCache(reference)) {
            return entity.getReferenceFromCache(reference)
        }

        // TODO incapsulate this logic inside entity to avoid checking for different fields outside.
        @Suppress("UNCHECKED_CAST")
        val refValue: REF = (entity.writeValues[reference as Column<Any?>] as? REF)
            ?: (entity._readValues?.let { row -> row[reference] } as? REF)
            ?: error("Reference column ${reference.name} has no value for entity ${entity.id}")

        val parentEntity = lookupParentEntity(factory, reference, refValue)
            ?: error("Referenced entity not found for column ${reference.name} with value $refValue")

        entity.storeReferenceInCache(reference, parentEntity)

        return parentEntity
    }
}

class OptionalSuspendAccessor<ID : Any, Parent : R2dbcEntity<ID>, REF : Any>(
    internal val reference: Column<REF?>,
    internal val factory: R2dbcEntityClass<ID, @UnsafeVariance Parent>,
    internal val entity: R2dbcEntity<*>
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

        val parentEntity = lookupParentEntity(factory, reference, refValue)

        entity.storeReferenceInCache(reference, parentEntity)

        return parentEntity
    }
}

/**
 * Shared lookup used by [SuspendAccessor.invoke] and [OptionalSuspendAccessor.invoke].
 *
 * Mirrors JDBC's `Reference.getValue` / `OptionalReference.getValue` logic from `Entity.kt`:
 *
 *   - When the child column already stores an `EntityID` AND the referee is the parent's id,
 *     hit the cache-friendly `findById` path.
 *   - Otherwise the child column stores a raw value (e.g. `Column<Long>` referencing
 *     `Cities.id : Column<EntityID<Long>>`, or a column referencing a non-id unique column).
 *     Unwrap the referee's column type — if it's `EntityIDColumnType<T>` we need to compare
 *     against the inner `idColumn` (a raw `Column<T>`) so `eq refValue` type-checks.
 */
@Suppress("UNCHECKED_CAST")
internal suspend fun <ID : Any, Parent : R2dbcEntity<ID>> lookupParentEntity(
    factory: R2dbcEntityClass<ID, @UnsafeVariance Parent>,
    reference: Column<*>,
    refValue: Any
): Parent? {
    val referee = reference.referee
        ?: error("Reference column ${reference.name} does not point to any column in ${factory.table.tableName}")

    return when {
        refValue is EntityID<*> && referee == factory.table.id ->
            factory.findById(refValue as EntityID<ID>)
        else -> {
            val baseReferee = (referee.columnType as? EntityIDColumnType<Any>)?.idColumn ?: referee
            factory.find { (baseReferee as Column<Any?>) eq refValue }.singleOrNull()
        }
    }
}
