package org.example.tables

import org.jetbrains.exposed.dao.id.IntIdTable

const val MAX_VARCHAR_LENGTH = 50

object UsersTable : IntIdTable() {
    val name = varchar("name", MAX_VARCHAR_LENGTH)
    val cityId = reference("cityId", CitiesTable.id)
}
