package org.example.tables

import org.jetbrains.exposed.v1.core.dao.id.IntIdTable

object UserRatingsTable : IntIdTable() {
    val value = long("value")
    val film = reference("film", StarWarsFilmsTable)
    val user = reference("user", UsersTable)
}
