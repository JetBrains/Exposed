package org.jetbrains.exposed.r2dbc.dao.relationships

import kotlinx.coroutines.flow.singleOrNull
import org.jetbrains.exposed.r2dbc.dao.R2dbcEntity
import org.jetbrains.exposed.r2dbc.dao.R2dbcEntityClass
import org.jetbrains.exposed.r2dbc.dao.entityCache
import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.EntityIDColumnType
import org.jetbrains.exposed.v1.core.dao.id.CompositeID
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.IdTable
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.r2dbc.transactions.TransactionManager
import kotlin.collections.get
import kotlin.reflect.KProperty

class SuspendAccessor<ID : Any, Parent : R2dbcEntity<ID>, REF : Any>(
    internal val reference: Column<REF>,
    internal val factory: R2dbcEntityClass<ID, @UnsafeVariance Parent>,
    internal val entity: R2dbcEntity<*>,
    /**
     * Composite-FK child→parent column map. `null` for single-column references — in that case
     * [set] uses `reference.referee` directly.
     */
    internal val references: Map<Column<*>, Column<*>>? = null
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

        if (references != null) {
            copyCompositeFkValues(entity, value, references)
        } else {
            // Single-column reference (original logic).
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

        // Store in reference cache
        entity.storeReferenceInCache(reference, value)
    }

    suspend operator fun invoke(): Parent {
        if (entity.hasInReferenceCache(reference)) {
            return entity.getReferenceFromCache(reference)
        }

        if (references != null) {
            // Composite-FK lookup — build a CompositeID from the child's columns mapped to the
            // parent's referee columns, then `findById`. Mirrors JDBC's `Reference.getValue`
            // composite branch (References.kt:152–161).
            val parentEntity = lookupCompositeParent(factory, entity, references)
                ?: error("Referenced entity not found for composite FK from ${reference.name}")
            entity.storeReferenceInCache(reference, parentEntity)
            return parentEntity
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
    internal val entity: R2dbcEntity<*>,
    /** Composite-FK child→parent column map. `null` for single-column references. */
    internal val references: Map<Column<*>, Column<*>>? = null
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

            if (references != null) {
                copyCompositeFkValues(entity, value, references)
            } else {
                @Suppress("UNCHECKED_CAST")
                val refValue = when {
                    reference.referee == factory.table.id -> value.id as REF
                    reference.referee?.table == factory.table -> {
                        val refereeColumn = reference.referee!!
                        (value.writeValues[refereeColumn as Column<Any?>] ?: value._readValues?.get(refereeColumn)) as REF
                    }
                    else -> error("Reference column ${reference.name} does not point to any column in ${factory.table.tableName}")
                }
                entity.writeValues[reference as Column<Any?>] = refValue
            }
        } else {
            if (references != null) {
                // Clear every child column when clearing a composite reference.
                references.keys.forEach { childColumn ->
                    @Suppress("UNCHECKED_CAST")
                    entity.writeValues[childColumn as Column<Any?>] = null
                }
            } else {
                // Clear the (single) reference
                entity.writeValues[reference as Column<Any?>] = null
            }
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

        if (references != null) {
            // Composite-FK: if ANY of the FK columns is null on the child, the optional reference
            // is considered absent (mirrors JDBC's CompositeID/null branch).
            val anyNull = references.keys.any { childColumn ->
                val v = entity.writeValues[childColumn as Column<Any?>] ?: entity._readValues?.getOrNull(childColumn)
                v == null
            }
            if (anyNull) {
                entity.storeReferenceInCache(reference, null)
                return null
            }
            val parentEntity = lookupCompositeParent(factory, entity, references)
            entity.storeReferenceInCache(reference, parentEntity)
            return parentEntity
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


private fun copyCompositeFkValues(
    child: R2dbcEntity<*>,
    parent: R2dbcEntity<*>,
    references: Map<Column<*>, Column<*>>
) {
    references.forEach { (childColumn, parentColumn) ->
        @Suppress("UNCHECKED_CAST")
        val parentRaw: Any? = parent.writeValues[parentColumn as Column<Any?>]
            ?: parent._readValues?.getOrNull(parentColumn)
        // Unwrap `EntityID` when the child column stores a raw value
        val value = if (parentRaw is EntityID<*> && childColumn.columnType !is EntityIDColumnType<*>) {
            parentRaw._value
        } else {
            parentRaw
        }
        @Suppress("UNCHECKED_CAST")
        child.writeValues[childColumn as Column<Any?>] = value
    }
}

/**
 * Composite-FK lookup used by [SuspendAccessor.invoke] / [OptionalSuspendAccessor.invoke] when the
 * accessor was built from an `IdTable<*>`-shaped DSL entry point. Constructs a [CompositeID] by
 * mapping each child column to its referee parent column, then delegates to `factory.findById`.
 *
 * Mirrors the `CompositeID` branch of JDBC's `Reference.getValue` (References.kt:157–161).
 */
@Suppress("UNCHECKED_CAST")
private suspend fun <ID : Any, Parent : R2dbcEntity<ID>> lookupCompositeParent(
    factory: R2dbcEntityClass<ID, @UnsafeVariance Parent>,
    child: R2dbcEntity<*>,
    references: Map<Column<*>, Column<*>>
): Parent? {
    val parentIdValue = CompositeID { id ->
        references.forEach { (childColumn, parentColumn) ->
            val rawChild = child.writeValues[childColumn as Column<Any?>]
                ?: child._readValues?.getOrNull(childColumn)
                ?: error("Composite-FK child column ${childColumn.name} has no value on ${child.id}")
            // `parentColumn` is an EntityID column on the parent's id table; wrap the raw child
            // value into an `EntityID<*>` so `CompositeID` accepts it.
            val parentIdColumn = parentColumn as Column<EntityID<Any>>
            val parentValueRaw = (rawChild as? EntityID<*>)?.value ?: rawChild
            id[parentIdColumn] = parentValueRaw
        }
    }
    return factory.findById(parentIdValue as ID)
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
