package org.jetbrains.exposed.v1.dao

import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.IdTable
import java.util.*
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/** Base class for an [Entity] instance identified by an [id] comprised of a wrapped `Uuid` value. */
@OptIn(ExperimentalUuidApi::class)
abstract class UuidKtEntity(id: EntityID<Uuid>) : Entity<Uuid>(id)

/**
 * Base class representing the [EntityClass] that manages [UuidEntity] instances and
 * maintains their relation to the provided [table].
 *
 * @param [table] The [IdTable] object that stores rows mapped to entities of this class.
 * @param [entityType] The expected [UuidEntity] type. This can be left `null` if it is the class of type
 * argument [E] provided to this [UuidEntityClass] instance. If this `UuidEntityClass` is defined as a companion
 * object of a custom `UuidEntity` class, the parameter will be set to this immediately enclosing class by default.
 * @sample org.jetbrains.exposed.v1.sql.tests.shared.DDLTests.testDropTableFlushesCache
 * @param [entityCtor] The function invoked to instantiate a [UuidEntity] using a provided [EntityID] value.
 * If a reference to a specific constructor or a custom function is not passed as an argument, reflection will
 * be used to determine the primary constructor of the associated entity class on first access. If this `UuidEntityClass`
 * is defined as a companion object of a custom `UuidEntity` class, the constructor will be set to that of the
 * immediately enclosing class by default.
 * @sample org.jetbrains.exposed.v1.sql.tests.shared.entities.EntityTests.testExplicitEntityConstructor
 */

@OptIn(ExperimentalUuidApi::class)
abstract class UuidKtEntityClass<out E : UuidKtEntity>(
    table: IdTable<Uuid>,
    entityType: Class<E>? = null,
    entityCtor: ((EntityID<Uuid>) -> E)? = null
) : EntityClass<Uuid, E>(table, entityType, entityCtor)
