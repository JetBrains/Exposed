package org.example.tables

import org.jetbrains.exposed.v1.core.dao.id.IntIdTable

const val MAX_USER_NAME_LENGTH = 50

object UsersTable : IntIdTable() {
    val name = varchar("name", MAX_USER_NAME_LENGTH)
    val cityId = reference("cityId", CitiesTable.id)
}
