package org.jetbrains.exposed.dao

import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IdTable

@Suppress("UnnecessaryAbstractClass")
abstract class LongEntity(id: EntityID<Long>) : Entity<Long>(id)

@Suppress("UnnecessaryAbstractClass")
abstract class LongEntityClass<out E : LongEntity>(
    table: IdTable<Long>,
    entityType: Class<E>? = null,
    entityCtor: ((EntityID<Long>) -> E)? = null
) : EntityClass<Long, E>(table, entityType, entityCtor)
