package org.example.entities

import org.example.tables.DirectorsCustomTable
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.dao.Entity
import org.jetbrains.exposed.v1.dao.EntityClass

class DirectorCustomEntity(id: EntityID<String>) : Entity<String>(id) {
    companion object : EntityClass<String, DirectorCustomEntity>(DirectorsCustomTable)

    var name by DirectorsCustomTable.name
}
