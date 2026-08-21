package org.jetbrains.exposed.v1.dao

import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.IdTable

/**
 * Base class for an [Entity] with an `Int` id, mapping a table declared as an `IntIdTable`.
 * See [Entity] for how the entity and its properties are declared.
 */
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
 * @sample org.jetbrains.exposed.v1.tests.shared.DDLTests.testDropTableFlushesCache
 * @param [entityCtor] Called to instantiate an entity for a row. Defaults to the entity's primary constructor,
 * looked up by reflection on first access; pass a reference such as `::Film` to skip that lookup.
 * @sample org.jetbrains.exposed.v1.tests.shared.entities.EntityTests.testExplicitEntityConstructor
 */
abstract class IntEntityClass<out E : IntEntity>(
    table: IdTable<Int>,
    entityType: Class<E>? = null,
    entityCtor: ((EntityID<Int>) -> E)? = null
) : EntityClass<Int, E>(table, entityType, entityCtor)
