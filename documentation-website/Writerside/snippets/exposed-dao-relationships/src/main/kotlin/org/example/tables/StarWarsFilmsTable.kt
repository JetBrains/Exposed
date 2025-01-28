package org.example.tables

import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.Table

const val MAX_VARCHAR_LENGTH = 50

object StarWarsFilmsTable : IntIdTable() {
    val sequelId = integer("sequel_id").uniqueIndex()
    val name = varchar("name", MAX_VARCHAR_LENGTH)
    val director = varchar("director", MAX_VARCHAR_LENGTH)
}

object StarWarsFilmsWithDirectorTable: IntIdTable() {
    val name = varchar("name", MAX_VARCHAR_LENGTH)
    val director = reference("director", DirectorsTable)
}

object StarWarsFilmRelationsTable : Table() {
    val parentFilm = reference("parent_film_id", StarWarsFilmsWithDirectorTable)
    val childFilm = reference("child_film_id", StarWarsFilmsWithDirectorTable)
    override val primaryKey = PrimaryKey(parentFilm, childFilm, name = "PK_FilmRelations")
}

object StarWarsFilmsWithCompositeRefTable : IntIdTable() {
    val sequelId = integer("sequel_id").uniqueIndex()
    val name = varchar("name", 50)
    val directorName = varchar("director_name", 50)
    val directorGuildId = uuid("director_guild_id")

    init {
        foreignKey(directorName, directorGuildId, target = DirectorsCompositeIdTable.primaryKey)
    }
}
