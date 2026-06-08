package org.jetbrains.exposed.r2dbc.dao.relationships

import kotlinx.coroutines.flow.singleOrNull
import org.jetbrains.exposed.r2dbc.dao.Entity
import org.jetbrains.exposed.r2dbc.dao.EntityClass
import org.jetbrains.exposed.r2dbc.dao.ExperimentalR2dbcDaoApi
import org.jetbrains.exposed.r2dbc.dao.entityCache
import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.EntityIDColumnType
import org.jetbrains.exposed.v1.core.dao.id.CompositeID
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.IdTable
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.r2dbc.transactions.TransactionManager
import kotlin.reflect.KProperty

/**
 * R2DBC accessor returned for non-nullable many-to-one references created via `referencedOn`.
 *
 * This is an R2DBC-specific shape that has no exact JDBC counterpart: in JDBC, the property delegate's
 * `getValue` directly returns the parent entity, whereas R2DBC needs a suspending lookup. The accessor
 * is exposed as a `val` and supports three usage patterns:
 *
 * - `entity.ref()` — async get via [invoke].
 * - `entity.ref.set(parent)` — write via method call.
 * - `entity.ref(parent)` — write via [invoke] with an argument.
 *
 * This sidesteps Kotlin's delegation protocol constraint that `getValue` and `setValue` must agree on
 * the property type — which would require `setValue` to itself be a `suspend operator`, which Kotlin
 * does not support.
 */
@ExperimentalR2dbcDaoApi
class Accessor<ID : Any, Parent : Entity<ID>, REF : Any>(
    internal val reference: Column<REF>,
    internal val factory: EntityClass<ID, @UnsafeVariance Parent>,
    internal val entity: Entity<*>,
    /**
     * Composite-FK child→parent column map. `null` for single-column references — in that case
     * [set] uses `reference.referee` directly.
     */
    internal val references: Map<Column<*>, Column<*>>? = null
) {
    /**
     * getValue operator - returns this accessor which has invoke() and set operations.
     *
     * This enables these patterns:
     * - `person.city()` - Async get via invoke()
     * - `person.city.set(cityEntity)` - Set via method call
     * - `person.city(cityEntity)` - Set via invoke with argument
     */
    operator fun getValue(thisRef: Any?, property: KProperty<*>): Accessor<ID, Parent, REF> {
        return this
    }

    /** Writes the link to [value] into the underlying [reference] column(s) and pins it in the entity's reference cache. */
    fun set(value: Parent) {
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
                    value.resolveColumnValue(refereeColumn) as REF
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
                entityCache.scheduleUpdate(entity.klass as EntityClass<Any, Entity<Any>>, entity as Entity<Any>)
            }
        }

        // Store in reference cache
        entity.storeReferenceInCache(reference, value)
    }

    /** Invoke-with-argument form of [set] — `entity.ref(parent)`. */
    operator fun invoke(value: Parent) = set(value)

    /**
     * Suspending read of the referenced parent entity. Looks up the entity from the cache, or otherwise
     * resolves the parent by querying the database via [factory].
     *
     * @throws IllegalStateException if the reference value cannot be resolved to a parent entity.
     */
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

        @Suppress("UNCHECKED_CAST")
        val refValue: REF = entity.resolveColumnValue(reference) as? REF
            ?: error("Reference column ${reference.name} has no value for entity ${entity.id}")

        val parentEntity = lookupParentEntity(factory, reference, refValue)
            ?: error("Referenced entity not found for column ${reference.name} with value $refValue")

        entity.storeReferenceInCache(reference, parentEntity)

        return parentEntity
    }
}

/**
 * R2DBC accessor returned for nullable many-to-one references created via `optionalReferencedOn`.
 *
 * Mirrors [Accessor] but allows `null` reads and writes. Uses the same val + invoke()/set() pattern
 * because `setValue` cannot be made `suspend` (Kotlin does not allow suspend property delegates).
 */
@ExperimentalR2dbcDaoApi
class OptionalAccessor<ID : Any, Parent : Entity<ID>, REF : Any>(
    internal val reference: Column<REF?>,
    internal val factory: EntityClass<ID, @UnsafeVariance Parent>,
    internal val entity: Entity<*>,
    /** Composite-FK child→parent column map. `null` for single-column references. */
    internal val references: Map<Column<*>, Column<*>>? = null
) {
    /** Property delegate operator — returns this accessor itself, used to expose [invoke] / [set]. */
    operator fun getValue(thisRef: Any?, property: KProperty<*>): OptionalAccessor<ID, Parent, REF> {
        return this
    }

    /** Writes the link to [value] (or clears it when `null`) into the underlying [reference] column(s). */
    fun set(value: Parent?) {
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
                        value.resolveColumnValue(refereeColumn) as REF
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
                entityCache.scheduleUpdate(entity.klass as EntityClass<Any, Entity<Any>>, entity as Entity<Any>)
            }
        }

        entity.storeReferenceInCache(reference, value)
    }

    /** Invoke-with-argument form of [set] — `entity.ref(parent)` or `entity.ref(null)`. */
    operator fun invoke(value: Parent?) = set(value)

    /**
     * Suspending read of the optionally referenced parent entity. Returns `null` when the underlying
     * reference column(s) are unset.
     */
    suspend operator fun invoke(): Parent? {
        if (entity.hasInReferenceCache(reference)) {
            return entity.getReferenceFromCache(reference)
        }

        if (references != null) {
            // Composite-FK: if ANY of the FK columns is null on the child, the optional reference
            // is considered absent (mirrors JDBC's CompositeID/null branch).
            val anyNull = references.keys.any { childColumn ->
                entity.resolveColumnValue(childColumn) == null
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
        val refValue: REF? = entity.resolveColumnValue(reference) as? REF

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
    child: Entity<*>,
    parent: Entity<*>,
    references: Map<Column<*>, Column<*>>
) {
    references.forEach { (childColumn, parentColumn) ->
        val parentRaw: Any? = parent.resolveColumnValue(parentColumn)
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
 * Composite-FK lookup used by [Accessor.invoke] / [OptionalAccessor.invoke] when the
 * accessor was built from an `IdTable<*>`-shaped DSL entry point. Constructs a [CompositeID] by
 * mapping each child column to its referee parent column, then delegates to `factory.findById`.
 *
 * Mirrors the `CompositeID` branch of JDBC's `Reference.getValue` (References.kt:157–161).
 */
@Suppress("UNCHECKED_CAST")
private suspend fun <ID : Any, Parent : Entity<ID>> lookupCompositeParent(
    factory: EntityClass<ID, @UnsafeVariance Parent>,
    child: Entity<*>,
    references: Map<Column<*>, Column<*>>
): Parent? {
    val parentIdValue = CompositeID { id ->
        references.forEach { (childColumn, parentColumn) ->
            val rawChild = child.resolveColumnValue(childColumn)
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
 * Shared lookup used by [Accessor.invoke] and [OptionalAccessor.invoke].
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
internal suspend fun <ID : Any, Parent : Entity<ID>> lookupParentEntity(
    factory: EntityClass<ID, @UnsafeVariance Parent>,
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
