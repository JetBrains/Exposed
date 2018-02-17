package org.jetbrains.exposed.dao

import org.jetbrains.exposed.sql.Column
import java.util.*


open class UUIDTable(name: String = "", columnName: String = "id") : IdTable<UUID>(name) {
    override val id: Column<EntityID<UUID>> = uuid(columnName).primaryKey()
            .clientDefault { UUID.randomUUID() }
            .entityId()
}

abstract class UUIDEntity(id: EntityID<UUID>) : Entity<UUID>(id)

abstract class UUIDEntityClass<out E: UUIDEntity>(table: IdTable<UUID>, entityType: Class<E>? = null) : EntityClass<UUID, E> (table, entityType)

