package org.jetbrains.exposed.r2dbc.dao

import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.IdTable

abstract class R2dbcIntEntity(id: EntityID<Int>) : R2dbcEntity<Int>(id)

abstract class R2dbcIntEntityClass<out E : R2dbcIntEntity>(
    table: IdTable<Int>,
    entityType: Class<E>? = null,
    entityCtor: ((EntityID<Int>) -> E)? = null
) : R2dbcEntityClass<Int, E>(table, entityType, entityCtor)
