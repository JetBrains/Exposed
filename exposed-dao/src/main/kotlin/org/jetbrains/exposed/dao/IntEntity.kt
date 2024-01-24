package org.jetbrains.exposed.dao

import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IdTable

@Suppress("UnnecessaryAbstractClass")
abstract class IntEntity(id: EntityID<Int>) : Entity<Int>(id)

@Suppress("UnnecessaryAbstractClass")
abstract class IntEntityClass<out E : IntEntity> constructor(
    table: IdTable<Int>,
    entityType: Class<E>? = null,
    entityCtor: ((EntityID<Int>) -> E)? = null
) : EntityClass<Int, E>(table, entityType, entityCtor)
