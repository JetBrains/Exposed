package org.jetbrains.exposed.v1.dao

import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.IdTable

/** Base class for an [Entity] instance identified by an [id] comprised of a wrapped `ULong` value. */
abstract class ULongEntity(id: EntityID<ULong>) : Entity<ULong>(id)

/**
 * Base class representing the [EntityClass] that manages [ULongEntity] instances and
 * maintains their relation to the provided [table].
 *
 * @param [table] The [IdTable] object that stores rows mapped to entities of this class.
 * @param [entityType] The expected [ULongEntity] type. This can be left `null` if it is the class of type
 * argument [E] provided to this [ULongEntityClass] instance. If this `ULongEntityClass` is defined as a companion
 * object of a custom `ULongEntity` class, the parameter will be set to this immediately enclosing class by default.
 * @sample org.jetbrains.exposed.v1.sql.tests.shared.DDLTests.testDropTableFlushesCache
 * @param [entityCtor] The function invoked to instantiate a [ULongEntity] using a provided [EntityID] value.
 * If a reference to a specific constructor or a custom function is not passed as an argument, reflection will
 * be used to determine the primary constructor of the associated entity class on first access. If this `ULongEntityClass`
 * is defined as a companion object of a custom `ULongEntity` class, the constructor will be set to that of the
 * immediately enclosing class by default.
 * @sample org.jetbrains.exposed.v1.sql.tests.shared.entities.EntityTests.testExplicitEntityConstructor
 */
abstract class ULongEntityClass<out E : ULongEntity>(
    table: IdTable<ULong>,
    entityType: Class<E>? = null,
    entityCtor: ((EntityID<ULong>) -> E)? = null
) : EntityClass<ULong, E>(table, entityType, entityCtor)
