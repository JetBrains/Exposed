package org.jetbrains.exposed.dao

import org.jetbrains.exposed.dao.id.CompositeID
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IdTable

abstract class CompositeEntity(id: EntityID<CompositeID>) : Entity<CompositeID>(id)

abstract class CompositeEntityClass<out E : CompositeEntity> constructor(
    table: IdTable<CompositeID>,
    entityType: Class<E>? = null,
    entityCtor: ((EntityID<CompositeID>) -> E)? = null
) : EntityClass<CompositeID, E>(table, entityType, entityCtor)
