package org.example.tables

import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.Rank
import org.jetbrains.exposed.sql.SortOrder

const val MAX_NAME_LENGTH = 32

object StarWarsWFilmsWithRankTable : IntIdTable() {
    val sequelId = integer("sequel_id").uniqueIndex()
    val name = varchar("name", MAX_NAME_LENGTH)
    val rating = double("rating")

    val rank = Rank().over().orderBy(rating, SortOrder.DESC)
}
