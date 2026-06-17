package org.jetbrains.exposed.r2dbc.dao

import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.IdTable

abstract class IntR2dbcEntity(id: EntityID<Int>) : R2dbcEntity<Int>(id)

abstract class IntR2dbcEntityClass<out E : IntR2dbcEntity>(
    table: IdTable<Int>,
    entityType: Class<E>? = null,
    entityCtor: ((EntityID<Int>) -> E)? = null
) : R2dbcEntityClass<Int, E>(table, entityType, entityCtor)
