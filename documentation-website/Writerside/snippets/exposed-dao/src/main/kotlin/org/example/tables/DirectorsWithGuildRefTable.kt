package org.example.tables

import org.jetbrains.exposed.dao.id.CompositeIdTable

const val MAX_VARCHAR_LENGTH = 50

object DirectorsWithGuildRefTable : CompositeIdTable() {
    val name = varchar("name", MAX_VARCHAR_LENGTH).entityId()
    val guildId = reference("guild_id", GuildsTable)
    val genre = enumeration<Genre>("genre")

    init {
        addIdColumn(guildId)
    }

    override val primaryKey = PrimaryKey(name, guildId)
}
