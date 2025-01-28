package org.example.entities

import org.example.tables.UsersTable
import org.example.tables.UserRatingsTable
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.SortOrder

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


