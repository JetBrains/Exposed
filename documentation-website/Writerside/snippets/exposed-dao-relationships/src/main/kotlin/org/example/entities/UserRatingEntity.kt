package org.example.entities

import org.example.tables.UserRatingsTable
import org.example.tables.UserRatingsWithOptionalUserTable
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.id.EntityID

/*
    Important: This file is referenced by line number in `DAO-Relationships.topic`.
    If you add, remove, or modify any lines, ensure you update the corresponding
    line numbers in the `code-block` element of the referenced file.
*/

class UserRatingEntity(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<UserRatingEntity>(UserRatingsTable)

    var value by UserRatingsTable.value
    var film by StarWarsFilmEntity referencedOn UserRatingsTable.film // use referencedOn for normal references
    var user by UserEntity referencedOn UserRatingsTable.user
    val rating by UserRatingEntity backReferencedOn UserRatingsTable.user // make sure to use val and backReferencedOn
}

class UserRatingWithOptionalUserEntity(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<UserRatingWithOptionalUserEntity>(UserRatingsWithOptionalUserTable)

    var value by UserRatingsWithOptionalUserTable.value
    var film by StarWarsFilmEntity referencedOn UserRatingsWithOptionalUserTable.film // use referencedOn for normal references
    var user by UserEntity optionalReferencedOn UserRatingsWithOptionalUserTable.user
}
