package org.example.tables

import org.jetbrains.exposed.v1.core.dao.id.CompositeIdTable

const val DIRECTOR_NAME_LENGTH = 50

object DirectorsWithGuildRefTable : CompositeIdTable() {
    val name = varchar("name", DIRECTOR_NAME_LENGTH).entityId()
    val guildId = reference("guild_id", GuildsTable)
    val genre = enumeration<Genre>("genre")

    init {
        addIdColumn(guildId)
    }

    override val primaryKey = PrimaryKey(name, guildId)
}
