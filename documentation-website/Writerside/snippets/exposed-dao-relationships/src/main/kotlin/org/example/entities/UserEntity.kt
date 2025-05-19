package org.example.entities

import org.example.entities.UserRatingEntity.Companion.backReferencedOn
import org.example.tables.UserRatingsTable
import org.example.tables.UsersTable
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.dao.IntEntity
import org.jetbrains.exposed.v1.dao.IntEntityClass

/*
    Important: This file is referenced by line number in `DAO-Relationships.topic`.
    If you add, remove, or modify any lines, ensure you update the corresponding
    line numbers in the `code-block` element of the referenced file.
*/

class UserEntity(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<UserEntity>(UsersTable)

    var name by UsersTable.name
    val ratings by UserRatingEntity referrersOn UserRatingsTable.user orderBy UserRatingsTable.value
}

class UserOrderedEntity(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<UserOrderedEntity>(UsersTable)

    var name by UsersTable.name

    /*
        Without infix notation:

    val ratings by UserRating.referrersOn(UserRatings.user).orderBy(
        UserRatings.value to SortOrder.DESC,
        UserRatings.id to SortOrder.ASC
    )
     */
    val ratings by UserRatingEntity referrersOn UserRatingsTable.user orderBy listOf(
        UserRatingsTable.value to SortOrder.DESC,
        UserRatingsTable.id to SortOrder.ASC
    )
}

class UserWithSingleRatingEntity(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<UserWithSingleRatingEntity>(UsersTable)

    var name by UsersTable.name
    val rating by UserRatingEntity backReferencedOn UserRatingsTable.user // make sure to use val and backReferencedOn
}
