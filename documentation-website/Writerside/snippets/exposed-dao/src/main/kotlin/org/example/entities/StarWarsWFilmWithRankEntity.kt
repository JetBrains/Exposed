package org.example.entities

import org.example.tables.StarWarsWFilmsWithRankTable
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.Op
import org.jetbrains.exposed.sql.Query

class StarWarsWFilmWithRankEntity(id: EntityID<Int>) : IntEntity(id) {
    var sequelId by StarWarsWFilmsWithRankTable.sequelId
    var name by StarWarsWFilmsWithRankTable.name
    var rating by StarWarsWFilmsWithRankTable.rating

    val rank: Long
        get() = readValues[StarWarsWFilmsWithRankTable.rank]

    companion object : IntEntityClass<StarWarsWFilmWithRankEntity>(StarWarsWFilmsWithRankTable) {
        override fun searchQuery(op: Op<Boolean>): Query {
            return super.searchQuery(op).adjustSelect {
                select(columns + StarWarsWFilmsWithRankTable.rank)
            }
        }
    }
}
