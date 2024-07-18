package org.jetbrains.exposed.dao

import org.jetbrains.exposed.dao.id.CompositeID
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IdTable

/** Base class for an [Entity] instance identified by an [id] comprised of multiple wrapped values. */
abstract class CompositeEntity(id: EntityID<CompositeID>) : Entity<CompositeID>(id)

/**
 * Base class representing the [EntityClass] that manages [CompositeEntity] instances and
 * maintains their relation to the provided [table].
 *
 * @param [table] The [IdTable] object that stores rows mapped to entities of this class.
 * @param [entityType] The expected [CompositeEntity] type. This can be left `null` if it is the class of type argument
 * [E] provided to this [CompositeEntityClass] instance. If this `CompositeEntityClass` is defined as a companion object
 * of a custom `CompositeEntity` class, the parameter will be set to this immediately enclosing class by default.
 * @sample org.jetbrains.exposed.sql.tests.shared.DDLTests.testDropTableFlushesCache
 * @param [entityCtor] The function invoked to instantiate a [CompositeEntity] using a provided [EntityID] value.
 * If a reference to a specific constructor or a custom function is not passed as an argument, reflection will be used
 * to determine the primary constructor of the associated entity class on first access. If this `CompositeEntityClass`
 * is defined as a companion object of a custom `CompositeEntity` class, the constructor will be set to that of the
 * immediately enclosing class by default.
 * @sample org.jetbrains.exposed.sql.tests.shared.entities.EntityTests.testExplicitEntityConstructor
 */
abstract class CompositeEntityClass<out E : CompositeEntity>(
    table: IdTable<CompositeID>,
    entityType: Class<E>? = null,
    entityCtor: ((EntityID<CompositeID>) -> E)? = null
) : EntityClass<CompositeID, E>(table, entityType, entityCtor)
