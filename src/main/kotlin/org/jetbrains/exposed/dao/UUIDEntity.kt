package org.jetbrains.exposed.dao

import org.jetbrains.exposed.sql.Column
import java.util.UUID

open class UUIDTable(name: String = "", columName: String = "id") : IdTable<String>(name) {
  override val id: Column<EntityID<String>> = varchar(columName, 36).primaryKey().clientDefault { UUID.randomUUID().toString().toUpperCase() }.entityId()
}

abstract class UUIDEntity(id: EntityID<String>) : Entity<String>(id)

abstract class UUIDEntityClass<out E: UUIDEntity>(table: IdTable<String>, entityType: Class<E>? = null) : EntityClass<String, E> (table)
