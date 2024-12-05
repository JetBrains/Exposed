package org.example.tables

import org.jetbrains.exposed.dao.id.IntIdTable

const val MAX_NAME_LENGTH = 50

object ActorsIntIdTable : IntIdTable("actors") {
    val sequelId = integer("sequel_id").uniqueIndex()
    val name = varchar("name", MAX_NAME_LENGTH)
}
