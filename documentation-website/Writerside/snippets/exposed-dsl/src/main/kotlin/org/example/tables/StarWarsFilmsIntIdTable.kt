package org.example.tables

import org.jetbrains.exposed.dao.id.IntIdTable

object StarWarsFilmsIntIdTable : IntIdTable("star_wars_films_table") {
    val sequelId = integer("sequel_id").uniqueIndex()
    val name = varchar("name", 50)
    val director = varchar("director", MAX_VARCHAR_LENGTH)
}
