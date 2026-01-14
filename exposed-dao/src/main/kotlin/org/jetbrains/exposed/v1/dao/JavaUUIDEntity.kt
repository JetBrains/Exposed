package org.jetbrains.exposed.v1.dao

import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.IdTable
import java.util.UUID as JavaUUID

/** Base class for an [Entity] instance identified by an [id] comprised of a wrapped [java.util.UUID] value. */
abstract class JavaUUIDEntity(id: EntityID<JavaUUID>) : Entity<JavaUUID>(id)

/**
 * Base class representing the [EntityClass] that manages [JavaUUIDEntity] instances and
 * maintains their relation to the provided [table].
 *
 * @param [table] The [IdTable] object that stores rows mapped to entities of this class.
 * @param [entityType] The expected [JavaUUIDEntity] type. This can be left `null` if it is the class of type
 * argument [E] provided to this [JavaUUIDEntityClass] instance. If this `UUIDEntityClass` is defined as a companion
 * object of a custom `UUIDEntity` class, the parameter will be set to this immediately enclosing class by default.
 * @param [entityCtor] The function invoked to instantiate a [JavaUUIDEntity] using a provided [EntityID] value.
 * If a reference to a specific constructor or a custom function is not passed as an argument, reflection will
 * be used to determine the primary constructor of the associated entity class on first access. If this `UUIDEntityClass`
 * is defined as a companion object of a custom `UUIDEntity` class, the constructor will be set to that of the
 * immediately enclosing class by default.
 */
abstract class JavaUUIDEntityClass<out E : JavaUUIDEntity>(
    table: IdTable<JavaUUID>,
    entityType: Class<E>? = null,
    entityCtor: ((EntityID<JavaUUID>) -> E)? = null
) : EntityClass<JavaUUID, E>(table, entityType, entityCtor)
