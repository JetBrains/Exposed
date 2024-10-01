package org.example.entities

import org.example.tables.UserRatingsTable
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.id.EntityID

class UserRatingEntity(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<UserRatingEntity>(UserRatingsTable)

    var value by UserRatingsTable.value
    var film by StarWarsFilmEntity referencedOn UserRatingsTable.film // use referencedOn for normal references
    var user by UserEntity referencedOn UserRatingsTable.user
}
