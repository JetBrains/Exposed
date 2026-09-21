package org.jetbrains.exposed.v1.dao.r2dbc

import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.IdTable

/**
 * Base class for an [Entity] with an `Int` id, mapping a table declared as an `IntIdTable`.
 * See [Entity] for how the entity and its properties are declared.
 */
@ExperimentalR2dbcDaoApi
abstract class IntEntity(id: EntityID<Int>) : Entity<Int>(id)

/**
 * Base class for the companion object of an [IntEntity]: the entry point for creating, finding, and
 * deleting the rows of [table].
 *
 * ```kotlin
 * class Film(id: EntityID<Int>) : IntEntity(id) {
 *     companion object : IntEntityClass<Film>(Films)
 * }
 * ```
 *
 * @param [table] The table whose rows are mapped to entities of this class.
 * @param [entityType] The [IntEntity] class to map. Defaults to the class that encloses this companion object.
 * @param [entityCtor] Called to instantiate an entity for a row. Defaults to the entity's primary constructor,
 * looked up by reflection on first access; pass a reference such as `::Film` to skip that lookup.
 */
@ExperimentalR2dbcDaoApi
abstract class IntEntityClass<out E : IntEntity>(
    table: IdTable<Int>,
    entityType: Class<E>? = null,
    entityCtor: ((EntityID<Int>) -> E)? = null
) : EntityClass<Int, E>(table, entityType, entityCtor)
