package org.example.tables

import org.jetbrains.exposed.v1.Rank
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.dao.id.IntIdTable

const val MAX_NAME_LENGTH = 32

object StarWarsFilmsWithRankTable : IntIdTable() {
    val sequelId = integer("sequel_id").uniqueIndex()
    val name = varchar("name", MAX_NAME_LENGTH)
    val rating = double("rating")

    val rank = Rank().over().orderBy(rating, SortOrder.DESC)
}
