package org.example.tables

import org.example.tables.UsersTable
import org.example.tables.StarWarsFilmsTable
import org.jetbrains.exposed.dao.id.IntIdTable

object UserRatingsTable : IntIdTable() {
    val value = long("value")
    val film = reference("film", StarWarsFilmsTable)
    val user = reference("user", UsersTable)
}

object UserRatingsWithOptionalUserTable : IntIdTable() {
    val value = long("value")
    val film = reference("film", StarWarsFilmsTable)
    val user = reference("user", UsersTable).nullable()
}

