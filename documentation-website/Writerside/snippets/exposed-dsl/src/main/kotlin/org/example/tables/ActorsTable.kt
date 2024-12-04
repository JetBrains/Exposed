package org.example.tables

import org.jetbrains.exposed.sql.Table

const val MAX_ACTOR_NAME_LENGTH = 50

object ActorsTable : Table() {
    val id = integer("id").autoIncrement()
    val name = varchar("name", MAX_ACTOR_NAME_LENGTH)
    val sequelId = integer("sequel_id").uniqueIndex()
}
