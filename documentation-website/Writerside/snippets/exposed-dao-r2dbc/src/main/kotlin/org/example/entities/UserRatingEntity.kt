package org.example.entities

import org.example.tables.UserRatingsTable
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.dao.r2dbc.IntEntity
import org.jetbrains.exposed.v1.dao.r2dbc.IntEntityClass

class UserRatingEntity(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<UserRatingEntity>(UserRatingsTable)

    var value by UserRatingsTable.value
    val film by StarWarsFilmEntity referencedOn UserRatingsTable.film // use referencedOn for normal references
    val user by UserEntity referencedOn UserRatingsTable.user
}
