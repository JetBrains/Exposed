package org.example.entities

import org.example.tables.UserRatingsTable
import org.example.tables.UserRatingsWithOptionalUserTable
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.dao.r2dbc.IntEntity
import org.jetbrains.exposed.v1.dao.r2dbc.IntEntityClass

/*
    Important: This file is referenced by line number in `dao-relationships.md`.
    If you add, remove, or modify any lines, ensure you update the corresponding
    line numbers in the `code-block` element of the referenced file.
*/

class UserRatingEntity(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<UserRatingEntity>(UserRatingsTable)

    var value by UserRatingsTable.value
    val film by StarWarsFilmEntity referencedOn UserRatingsTable.film // use referencedOn for normal references
    val user by UserEntity referencedOn UserRatingsTable.user
}

class UserRatingWithOptionalUserEntity(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<UserRatingWithOptionalUserEntity>(UserRatingsWithOptionalUserTable)

    var value by UserRatingsWithOptionalUserTable.value
    val film by StarWarsFilmEntity referencedOn UserRatingsWithOptionalUserTable.film // use referencedOn for normal references
    val user by UserEntity optionalReferencedOn UserRatingsWithOptionalUserTable.user
}
