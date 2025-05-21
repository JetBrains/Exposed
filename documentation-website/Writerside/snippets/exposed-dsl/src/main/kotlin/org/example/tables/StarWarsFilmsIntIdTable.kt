package org.example.tables

import org.jetbrains.exposed.v1.core.dao.id.IntIdTable

object StarWarsFilmsIntIdTable : IntIdTable("star_wars_films_table") {
    val sequelId = integer("sequel_id").uniqueIndex()
    val name = varchar("name", MAX_VARCHAR_LENGTH)
    val director = varchar("director", MAX_VARCHAR_LENGTH)
}
