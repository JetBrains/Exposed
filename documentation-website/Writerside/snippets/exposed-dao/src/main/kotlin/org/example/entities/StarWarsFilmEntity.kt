package org.example.entities

import org.example.tables.StarWarsFilmsTable
import org.jetbrains.exposed.v1.dao.IntEntity
import org.jetbrains.exposed.v1.dao.IntEntityClass
import org.jetbrains.exposed.v1.dao.id.EntityID

class StarWarsFilmEntity(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<StarWarsFilmEntity>(StarWarsFilmsTable)

    var sequelId by StarWarsFilmsTable.sequelId
    var name by StarWarsFilmsTable.name
    var director by StarWarsFilmsTable.director
}
