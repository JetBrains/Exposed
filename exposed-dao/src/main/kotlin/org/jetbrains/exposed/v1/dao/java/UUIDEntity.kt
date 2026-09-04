package org.jetbrains.exposed.v1.dao.java

import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.IdTable
import org.jetbrains.exposed.v1.dao.Entity
import org.jetbrains.exposed.v1.dao.EntityClass
import java.util.UUID

/**
 * Base class for an [Entity] with a client-generated [java.util.UUID] id, mapping a table declared as a `UUIDTable`.
 * See [Entity] for how the entity and its properties are declared.
 *
 * [org.jetbrains.exposed.v1.dao.UuidEntity] is the equivalent for tables declared with Kotlin's
 * `Uuid`-based `UuidTable`.
 */
abstract class UUIDEntity(id: EntityID<UUID>) : Entity<UUID>(id)

/**
 * Base class for the companion object of a [UUIDEntity]: the entry point for creating, finding, and
 * deleting the rows of [table].
 *
 * ```kotlin
 * class Film(id: EntityID<UUID>) : UUIDEntity(id) {
 *     companion object : UUIDEntityClass<Film>(Films)
 * }
 * ```
 *
 * @param [table] The table whose rows are mapped to entities of this class.
 * @param [entityType] The [UUIDEntity] class to map. Defaults to the class that encloses this companion object.
 * @param [entityCtor] Called to instantiate an entity for a row. Defaults to the entity's primary constructor,
 * looked up by reflection on first access; pass a reference such as `::Film` to skip that lookup.
 */
abstract class UUIDEntityClass<out E : UUIDEntity>(
    table: IdTable<UUID>,
    entityType: Class<E>? = null,
    entityCtor: ((EntityID<UUID>) -> E)? = null
) : EntityClass<UUID, E>(table, entityType, entityCtor)
