package org.jetbrains.exposed.dao

import org.jetbrains.exposed.sql.Column

open class IntIdTable(name: String = "", columnName: String = "id") : IdTable<Int>(name) {
    override val id: Column<EntityID<Int>> = integer(columnName).autoIncrement().primaryKey().entityId()
}

abstract class IntEntity(id: EntityID<Int>) : Entity<Int>(id)

abstract class IntEntityClass<out E:IntEntity>(table: IdTable<Int>, entityType: Class<E>? = null) : EntityClass<Int, E> (table, entityType)
