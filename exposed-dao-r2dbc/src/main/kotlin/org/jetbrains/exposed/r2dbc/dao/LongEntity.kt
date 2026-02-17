package org.jetbrains.exposed.r2dbc.dao

import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.IdTable

abstract class R2dbcLongEntity(id: EntityID<Long>) : R2dbcEntity<Long>(id)

abstract class R2dbcLongEntityClass<out E : R2dbcLongEntity>(
    table: IdTable<Long>,
    entityType: Class<E>? = null,
    entityCtor: ((EntityID<Long>) -> E)? = null
) : R2dbcEntityClass<Long, E>(table, entityType, entityCtor)
