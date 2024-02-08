package org.jetbrains.exposed.dao

import org.jetbrains.exposed.dao.id.CompositeID
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IdTable

/** Base class for an [Entity] instance identified by an [id] comprised of multiple table column values. */
abstract class CompositeEntity(id: EntityID<CompositeID>) : Entity<CompositeID>(id)

/**
 * Base class representing the [EntityClass] that manages [CompositeEntity] instances and
 * maintains their relation to the provided [table].
 */
abstract class CompositeEntityClass<out E : CompositeEntity>(
    table: IdTable<CompositeID>,
    entityType: Class<E>? = null,
    entityCtor: ((EntityID<CompositeID>) -> E)? = null
) : EntityClass<CompositeID, E>(table, entityType, entityCtor)
