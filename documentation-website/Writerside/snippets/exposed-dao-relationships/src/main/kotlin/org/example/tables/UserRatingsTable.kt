package org.example.tables

import org.jetbrains.exposed.dao.id.IntIdTable

object UserRatingsTable : IntIdTable() {
    val value = long("value")
    val film = reference("film", StarWarsFilmsTable)
    val user = reference("user", UsersTable)
}

object UserRatingsWithOptionalUserTable : IntIdTable() {
    val value = long("value")
    val film = reference("film", StarWarsFilmsTable)
    val user = optReference("user", UsersTable)
}
