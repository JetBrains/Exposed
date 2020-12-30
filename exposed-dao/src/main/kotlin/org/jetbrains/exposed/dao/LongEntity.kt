package org.jetbrains.exposed.dao

import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IdTableInterface

abstract class LongEntity(id: EntityID<Long>) : Entity<Long>(id)

abstract class LongEntityClass<out E: LongEntity>(table: IdTableInterface<Long>, entityType: Class<E>? = null) : EntityClass<Long, E>(table, entityType)

