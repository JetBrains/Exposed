package org.example.entities

import org.example.tables.ActorsTable
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.id.EntityID

class ActorEntity(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<ActorEntity>(ActorsTable)

    var firstname by ActorsTable.firstname
    var lastname by ActorsTable.lastname
}
