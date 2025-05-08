package org.example.tables

import org.jetbrains.exposed.v1.sql.Table

const val MAX_CITY_NAME_LENGTH = 50

object CitiesTable : Table() {
    val id = integer("id").autoIncrement()
    val name = varchar("name", MAX_CITY_NAME_LENGTH)
}
