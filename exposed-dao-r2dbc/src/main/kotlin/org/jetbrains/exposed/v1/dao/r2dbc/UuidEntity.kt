package org.jetbrains.exposed.v1.dao.r2dbc

import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.IdTable
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Base class for an [Entity] with a client-generated [kotlin.uuid.Uuid] id, mapping a table declared as a `UuidTable`.
 * See [Entity] for how the entity and its properties are declared.
 */
@OptIn(ExperimentalUuidApi::class)
@ExperimentalR2dbcDaoApi
abstract class UuidEntity(id: EntityID<Uuid>) : Entity<Uuid>(id)

/**
 * Base class for the companion object of a [UuidEntity]: the entry point for creating, finding, and
 * deleting the rows of [table].
 *
 * ```kotlin
 * class Film(id: EntityID<Uuid>) : UuidEntity(id) {
 *     companion object : UuidEntityClass<Film>(Films)
 * }
 * ```
 *
 * @param [table] The table whose rows are mapped to entities of this class.
 * @param [entityType] The [UuidEntity] class to map. Defaults to the class that encloses this companion object.
 * @param [entityCtor] Called to instantiate an entity for a row. Defaults to the entity's primary constructor,
 * looked up by reflection on first access; pass a reference such as `::Film` to skip that lookup.
 */
@OptIn(ExperimentalUuidApi::class)
@ExperimentalR2dbcDaoApi
abstract class UuidEntityClass<out E : UuidEntity>(
    table: IdTable<Uuid>,
    entityType: Class<E>? = null,
    entityCtor: ((EntityID<Uuid>) -> E)? = null
) : EntityClass<Uuid, E>(table, entityType, entityCtor)
