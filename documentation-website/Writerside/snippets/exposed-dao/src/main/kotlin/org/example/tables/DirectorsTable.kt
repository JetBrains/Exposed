package org.example.tables

import org.jetbrains.exposed.dao.id.CompositeIdTable

const val MAX_VARCHAR_LENGTH = 32
enum class Genre { HORROR, DRAMA, THRILLER, SCI_FI }

object DirectorsTable : CompositeIdTable("directors") {
    val name = varchar("name", MAX_VARCHAR_LENGTH).entityId()
    val guildId = uuid("guild_id").autoGenerate().entityId()
    val genre = enumeration<Genre>("genre")

    override val primaryKey = PrimaryKey(name, guildId)
}
