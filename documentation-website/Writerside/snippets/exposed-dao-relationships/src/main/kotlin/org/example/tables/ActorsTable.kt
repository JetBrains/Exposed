package org.example.tables

import org.jetbrains.exposed.v1.dao.id.IntIdTable
import org.jetbrains.exposed.v1.sql.Table

const val MAX_NAME_LENGTH = 50

object ActorsTable : IntIdTable() {
    val firstname = varchar("firstname", MAX_NAME_LENGTH)
    val lastname = varchar("lastname", MAX_NAME_LENGTH)
}

object StarWarsFilmActorsTable : Table() {
    val starWarsFilm = reference("starWarsFilm", StarWarsFilmsTable)
    val actor = reference("actor", ActorsTable)
    override val primaryKey = PrimaryKey(starWarsFilm, actor, name = "PK_StarWarsFilmActors_swf_act") // PK_StarWarsFilmActors_swf_act is optional here
}

object ActorToActors : Table() {
    val parent = reference("parent_node_id", ActorsTable)
    val child = reference("child_user_id", ActorsTable)
}
