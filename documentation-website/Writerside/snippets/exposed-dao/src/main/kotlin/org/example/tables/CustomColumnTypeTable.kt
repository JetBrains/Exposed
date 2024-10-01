package org.example

import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IdTable
import org.jetbrains.exposed.sql.Column

object DirectorsCustomTable : IdTable<String>("directors") {
    override val id: Column<EntityID<String>> = varchar("id", 32).entityId()
    val name = varchar("name", 50)

    override val primaryKey = PrimaryKey(id)
}
