package org.jetbrains.exposed.v1.dao

import org.jetbrains.exposed.v1.dao.id.EntityID
import org.jetbrains.exposed.v1.dao.id.IdTable
import java.util.*

/** Base class for an [Entity] instance identified by an [id] comprised of a wrapped `UUID` value. */
abstract class UUIDEntity(id: EntityID<UUID>) : Entity<UUID>(id)

/**
 * Base class representing the [EntityClass] that manages [UUIDEntity] instances and
 * maintains their relation to the provided [table].
 *
 * @param [table] The [IdTable] object that stores rows mapped to entities of this class.
 * @param [entityType] The expected [UUIDEntity] type. This can be left `null` if it is the class of type
 * argument [E] provided to this [UUIDEntityClass] instance. If this `UUIDEntityClass` is defined as a companion
 * object of a custom `UUIDEntity` class, the parameter will be set to this immediately enclosing class by default.
 * @sample org.jetbrains.exposed.v1.sql.tests.shared.DDLTests.testDropTableFlushesCache
 * @param [entityCtor] The function invoked to instantiate a [UUIDEntity] using a provided [EntityID] value.
 * If a reference to a specific constructor or a custom function is not passed as an argument, reflection will
 * be used to determine the primary constructor of the associated entity class on first access. If this `UUIDEntityClass`
 * is defined as a companion object of a custom `UUIDEntity` class, the constructor will be set to that of the
 * immediately enclosing class by default.
 * @sample org.jetbrains.exposed.v1.sql.tests.shared.entities.EntityTests.testExplicitEntityConstructor
 */
abstract class UUIDEntityClass<out E : UUIDEntity>(
    table: IdTable<UUID>,
    entityType: Class<E>? = null,
    entityCtor: ((EntityID<UUID>) -> E)? = null
) : EntityClass<UUID, E>(table, entityType, entityCtor)
