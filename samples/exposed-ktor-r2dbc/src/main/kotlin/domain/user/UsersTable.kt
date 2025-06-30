@file:Suppress("InvalidPackageDeclaration", "MatchingDeclarationName", "MagicNumber")

package org.jetbrains.exposed.samples.r2dbc.domain.user

import kotlinx.serialization.json.Json
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.json.json

object Users : Table("users") {
    val id = integer("id").autoIncrement()

    val fullName = varchar("full_name", 128)
    val username = varchar("username", 128)

    val settings = json<UserSettings>("settings", Json.Default)

    override val primaryKey = PrimaryKey(id)
}

fun rowToUser(result: ResultRow): User = User(
    id = UserId(result[Users.id]),
    fullName = result[Users.fullName],
    username = result[Users.username],
    settings = result[Users.settings]
)
