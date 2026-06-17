package org.example.tables

import org.jetbrains.exposed.v1.core.Table

const val EMAIL_LIMIT = 320
const val AVATAR_LIMIT = 256

object UsersTable : Table("Users") {
    val id = uuid("id")
    val email = varchar("email", EMAIL_LIMIT).uniqueIndex()
    val avatar = varchar("avatar", AVATAR_LIMIT).nullable()
    val age = integer("age").nullable()

    override val primaryKey = PrimaryKey(id)
}
