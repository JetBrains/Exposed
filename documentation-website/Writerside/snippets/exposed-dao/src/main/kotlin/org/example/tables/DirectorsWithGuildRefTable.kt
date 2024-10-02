package org.example.tables

import org.jetbrains.exposed.dao.id.CompositeIdTable

object DirectorsWithGuildRefTable : CompositeIdTable() {
    val name = varchar("name", 50).entityId()
    val guildId = reference("guild_id", GuildsTable)
    val genre = enumeration<Genre>("genre")

    init {
        addIdColumn(guildId)
    }

    override val primaryKey = PrimaryKey(name, guildId)
}
