package org.example.tables

import org.jetbrains.exposed.v1.core.dao.id.IntIdTable

const val MAX_CITY_NAME_LENGTH = 50

object CitiesTable : IntIdTable() {
    val name = varchar("name", MAX_CITY_NAME_LENGTH)
}
