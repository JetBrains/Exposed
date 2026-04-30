package org.jetbrains.exposed.r2dbc.dao

import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.IdTable
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
abstract class UuidR2dbcEntity(id: EntityID<Uuid>) : R2dbcEntity<Uuid>(id)

@OptIn(ExperimentalUuidApi::class)
abstract class UuidR2dbcEntityClass<out E : UuidR2dbcEntity>(
    table: IdTable<Uuid>,
    entityType: Class<E>? = null,
    entityCtor: ((EntityID<Uuid>) -> E)? = null
) : R2dbcEntityClass<Uuid, E>(table, entityType, entityCtor)
