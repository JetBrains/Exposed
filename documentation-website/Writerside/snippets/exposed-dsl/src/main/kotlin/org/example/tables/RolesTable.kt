package org.example.tables

import org.jetbrains.exposed.v1.core.dao.id.IntIdTable

const val MAX_CHARACTER_NAME_LENGTH = 50

object RolesTable : IntIdTable() {
    val sequelId = integer("sequel_id")
    val actorId = reference("actor_id", ActorsIntIdTable)
    val characterName = varchar("name", MAX_CHARACTER_NAME_LENGTH)
}
