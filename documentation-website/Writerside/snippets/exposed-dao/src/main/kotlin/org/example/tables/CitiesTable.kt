package org.example

import org.jetbrains.exposed.dao.id.IntIdTable

object CitiesTable : IntIdTable() {
    val name = varchar("name", 50)
}
