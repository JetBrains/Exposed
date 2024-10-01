package org.example.tables

import org.jetbrains.exposed.dao.id.IntIdTable

object UsersTable : IntIdTable() {
    val name = varchar("name", 50)
    val cityId = reference("cityId", CitiesTable.id)
}
