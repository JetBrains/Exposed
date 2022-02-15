package org.jetbrains.exposed.postgresql.sql

import org.jetbrains.exposed.dao.id.LongIdTable
import org.jetbrains.exposed.sql.ResultRow

object AnimeCharacterPostgresTable : LongIdTable("anime_character") {
    val fullName = varchar("full_name", 50)
    val anime = varchar("anime", 50).nullable()
}

data class AnimeCharacterEntity(
    val id: Long,
    val fullName: String,
    val anime: String?
)

fun ResultRow.toEntity(): AnimeCharacterEntity {
    return AnimeCharacterEntity(
        id = this[AnimeCharacterPostgresTable.id].value,
        fullName = this[AnimeCharacterPostgresTable.fullName],
        anime = this[AnimeCharacterPostgresTable.anime]
    )
}