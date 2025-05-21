package org.example.tables

import org.jetbrains.exposed.v1.core.Table

const val EMAIL_LIMIT = 320

object UsersTable : Table("Users") {
    val id = uuid("id")
    val email = varchar("email", EMAIL_LIMIT)

    override val primaryKey = PrimaryKey(id)
}
