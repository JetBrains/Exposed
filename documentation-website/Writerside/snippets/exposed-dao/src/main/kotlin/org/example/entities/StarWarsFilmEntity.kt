package org.example

import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.id.EntityID

class StarWarsFilmEntity(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<StarWarsFilmEntity>(StarWarsFilmsTable)

    var sequelId by StarWarsFilmsTable.sequelId
    var name by StarWarsFilmsTable.name
    var director by StarWarsFilmsTable.director
}
