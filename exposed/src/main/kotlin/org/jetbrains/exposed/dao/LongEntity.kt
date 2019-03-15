package org.jetbrains.exposed.dao

import org.jetbrains.exposed.sql.Column


open class LongIdTable(name: String = "", columnName: String = "id") : IdTable<Long>(name) {
    override val id: Column<EntityID<Long>> = long(columnName).autoIncrement().primaryKey().entityId()
}

abstract class LongEntity(id: EntityID<Long>) : Entity<Long>(id)

abstract class LongEntityClass<out E:LongEntity>(table: IdTable<Long>, entityType: Class<E>? = null) : EntityClass<Long, E> (table, entityType)

