package org.jetbrains.exposed.v1.dao.r2dbc

import org.jetbrains.exposed.v1.core.dao.id.CompositeID
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.IdTable

/**
 * Base class for an [Entity] with an id made of several columns ([CompositeID]), mapping a table declared as a `CompositeIdTable`.
 * See [Entity] for how the entity and its properties are declared.
 */
@ExperimentalR2dbcDaoApi
abstract class CompositeEntity(id: EntityID<CompositeID>) : Entity<CompositeID>(id)

/**
 * Base class for the companion object of a [CompositeEntity]: the entry point for creating, finding, and
 * deleting the rows of [table].
 *
 * ```kotlin
 * class Film(id: EntityID<CompositeID>) : CompositeEntity(id) {
 *     companion object : CompositeEntityClass<Film>(Films)
 * }
 * ```
 *
 * @param [table] The table whose rows are mapped to entities of this class.
 * @param [entityType] The [CompositeEntity] class to map. Defaults to the class that encloses this companion object.
 * @param [entityCtor] Called to instantiate an entity for a row. Defaults to the entity's primary constructor,
 * looked up by reflection on first access; pass a reference such as `::Film` to skip that lookup.
 */
@ExperimentalR2dbcDaoApi
abstract class CompositeEntityClass<out E : CompositeEntity>(
    table: IdTable<CompositeID>,
    entityType: Class<E>? = null,
    entityCtor: ((EntityID<CompositeID>) -> E)? = null
) : EntityClass<CompositeID, E>(table, entityType, entityCtor)
