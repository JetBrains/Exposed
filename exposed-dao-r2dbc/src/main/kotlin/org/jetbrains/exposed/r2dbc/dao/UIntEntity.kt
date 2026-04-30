package org.jetbrains.exposed.r2dbc.dao

import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.IdTable

abstract class UIntR2dbcEntity(id: EntityID<UInt>) : R2dbcEntity<UInt>(id)

abstract class UIntR2dbcEntityClass<out E : UIntR2dbcEntity>(
    table: IdTable<UInt>,
    entityType: Class<E>? = null,
    entityCtor: ((EntityID<UInt>) -> E)? = null
) : R2dbcEntityClass<UInt, E>(table, entityType, entityCtor)
