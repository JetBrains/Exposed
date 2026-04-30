package org.jetbrains.exposed.r2dbc.dao

import org.jetbrains.exposed.v1.core.dao.id.CompositeID
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.IdTable

abstract class CompositeR2dbcEntity(id: EntityID<CompositeID>) : R2dbcEntity<CompositeID>(id)

abstract class CompositeR2dbcEntityClass<out E : CompositeR2dbcEntity>(
    table: IdTable<CompositeID>,
    entityType: Class<E>? = null,
    entityCtor: ((EntityID<CompositeID>) -> E)? = null
) : R2dbcEntityClass<CompositeID, E>(table, entityType, entityCtor)
