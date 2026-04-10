package org.jetbrains.exposed.r2dbc.dao

import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.IdTable

abstract class LongR2dbcEntity(id: EntityID<Long>) : R2dbcEntity<Long>(id)

abstract class LongR2dbcEntityClass<out E : LongR2dbcEntity>(
    table: IdTable<Long>,
    entityType: Class<E>? = null,
    entityCtor: ((EntityID<Long>) -> E)? = null
) : R2dbcEntityClass<Long, E>(table, entityType, entityCtor)
