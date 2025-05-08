package org.example.entities

import org.example.tables.StarWarsFilmsWithRankTable
import org.jetbrains.exposed.v1.dao.IntEntity
import org.jetbrains.exposed.v1.dao.IntEntityClass
import org.jetbrains.exposed.v1.dao.id.EntityID
import org.jetbrains.exposed.v1.sql.Op
import org.jetbrains.exposed.v1.sql.Query

class StarWarsFilmWithRankEntity(id: EntityID<Int>) : IntEntity(id) {
    var sequelId by StarWarsFilmsWithRankTable.sequelId
    var name by StarWarsFilmsWithRankTable.name
    var rating by StarWarsFilmsWithRankTable.rating

    val rank: Long
        get() = readValues[StarWarsFilmsWithRankTable.rank]

    companion object : IntEntityClass<StarWarsFilmWithRankEntity>(StarWarsFilmsWithRankTable) {
        override fun searchQuery(op: Op<Boolean>): Query {
            return super.searchQuery(op).adjustSelect {
                select(columns + StarWarsFilmsWithRankTable.rank)
            }
        }
    }
}
