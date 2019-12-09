package org.jetbrains.exposed.dao

import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IdTable
import java.util.*

abstract class UUIDEntity(id: EntityID<UUID>) : Entity<UUID>(id)

abstract class UUIDEntityClass<out E: UUIDEntity>(table: IdTable<UUID>, entityType: Class<E>? = null) : EntityClass<UUID, E>(table, entityType)

