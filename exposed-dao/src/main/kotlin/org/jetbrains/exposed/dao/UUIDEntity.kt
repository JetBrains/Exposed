package org.jetbrains.exposed.dao

import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IdTable
import java.util.*

@Suppress("UnnecessaryAbstractClass")
abstract class UUIDEntity(id: EntityID<UUID>) : Entity<UUID>(id)

@Suppress("UnnecessaryAbstractClass")
abstract class UUIDEntityClass<out E : UUIDEntity>(
    table: IdTable<UUID>,
    entityType: Class<E>? = null,
    entityCtor: ((EntityID<UUID>) -> E)? = null
) : EntityClass<UUID, E>(table, entityType, entityCtor)
