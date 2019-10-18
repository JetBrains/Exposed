package org.jetbrains.exposed.dao


abstract class IntEntity(id: EntityID<Int>) : Entity<Int>(id)

abstract class IntEntityClass<out E: IntEntity>(table: IdTable<Int>, entityType: Class<E>? = null) : EntityClass<Int, E>(table, entityType)
