package org.jetbrains.exposed.dao

import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IdTableInterface

abstract class IntEntity(id: EntityID<Int>) : Entity<Int>(id)

abstract class IntEntityClass<out E: IntEntity>(table: IdTableInterface<Int>, entityType: Class<E>? = null) : EntityClass<Int, E>(table, entityType)
