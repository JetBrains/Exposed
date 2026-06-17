package org.jetbrains.exposed.r2dbc.dao

import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.IdTable

abstract class ULongR2dbcEntity(id: EntityID<ULong>) : R2dbcEntity<ULong>(id)

abstract class ULongR2dbcEntityClass<out E : ULongR2dbcEntity>(
    table: IdTable<ULong>,
    entityType: Class<E>? = null,
    entityCtor: ((EntityID<ULong>) -> E)? = null
) : R2dbcEntityClass<ULong, E>(table, entityType, entityCtor)
