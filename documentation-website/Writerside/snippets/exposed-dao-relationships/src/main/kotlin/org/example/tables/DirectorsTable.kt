package org.example.tables

import org.jetbrains.exposed.v1.core.dao.id.CompositeIdTable
import org.jetbrains.exposed.v1.core.dao.id.IntIdTable

enum class Genre { HORROR, DRAMA, THRILLER, SCI_FI }
const val NAME_LENGTH = 50

object DirectorsTable : IntIdTable("directors") {
    val name = varchar("name", NAME_LENGTH)
    val genre = enumeration<Genre>("genre")
}

object DirectorsCompositeIdTable : CompositeIdTable() {
    val name = varchar("name", NAME_LENGTH).entityId()
    val guildId = uuid("guild_id").autoGenerate().entityId()
    val genre = enumeration<Genre>("genre")

    override val primaryKey = PrimaryKey(name, guildId)
}
