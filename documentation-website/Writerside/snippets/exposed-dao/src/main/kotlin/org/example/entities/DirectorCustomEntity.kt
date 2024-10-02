package org.example.entities

import org.example.tables.DirectorsCustomTable
import org.jetbrains.exposed.dao.Entity
import org.jetbrains.exposed.dao.EntityClass
import org.jetbrains.exposed.dao.id.EntityID

class DirectorCustomEntity(id: EntityID<String>) : Entity<String>(id) {
    companion object : EntityClass<String, DirectorCustomEntity>(DirectorsCustomTable)

    var name by DirectorsCustomTable.name
}
