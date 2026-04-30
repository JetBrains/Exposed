package org.jetbrains.exposed.r2dbc.dao.java

import org.jetbrains.exposed.r2dbc.dao.R2dbcEntity
import org.jetbrains.exposed.r2dbc.dao.R2dbcEntityClass
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.IdTable
import java.util.UUID

abstract class UUIDR2dbcEntity(id: EntityID<UUID>) : R2dbcEntity<UUID>(id)

abstract class UUIDR2dbcEntityClass<out E : UUIDR2dbcEntity>(
    table: IdTable<UUID>,
    entityType: Class<E>? = null,
    entityCtor: ((EntityID<UUID>) -> E)? = null
) : R2dbcEntityClass<UUID, E>(table, entityType, entityCtor)
