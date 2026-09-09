package org.jetbrains.exposed.v1.dao.r2dbc

import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.IdTable

/**
 * Base class for an [Entity] with a `UInt` id, mapping a table declared as a `UIntIdTable`.
 * See [Entity] for how the entity and its properties are declared.
 */
@ExperimentalR2dbcDaoApi
abstract class UIntEntity(id: EntityID<UInt>) : Entity<UInt>(id)

/**
 * Base class for the companion object of a [UIntEntity]: the entry point for creating, finding, and
 * deleting the rows of [table].
 *
 * ```kotlin
 * class Film(id: EntityID<UInt>) : UIntEntity(id) {
 *     companion object : UIntEntityClass<Film>(Films)
 * }
 * ```
 *
 * @param [table] The table whose rows are mapped to entities of this class.
 * @param [entityType] The [UIntEntity] class to map. Defaults to the class that encloses this companion object.
 * @param [entityCtor] Called to instantiate an entity for a row. Defaults to the entity's primary constructor,
 * looked up by reflection on first access; pass a reference such as `::Film` to skip that lookup.
 */
@ExperimentalR2dbcDaoApi
abstract class UIntEntityClass<out E : UIntEntity>(
    table: IdTable<UInt>,
    entityType: Class<E>? = null,
    entityCtor: ((EntityID<UInt>) -> E)? = null
) : EntityClass<UInt, E>(table, entityType, entityCtor)
