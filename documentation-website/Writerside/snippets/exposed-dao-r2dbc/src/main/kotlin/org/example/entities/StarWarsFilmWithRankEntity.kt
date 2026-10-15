package org.example.entities

import org.example.tables.StarWarsFilmsWithRankTable
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.dao.r2dbc.IntEntity
import org.jetbrains.exposed.v1.dao.r2dbc.IntEntityClass
import org.jetbrains.exposed.v1.r2dbc.Query
import org.jetbrains.exposed.v1.r2dbc.select

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
