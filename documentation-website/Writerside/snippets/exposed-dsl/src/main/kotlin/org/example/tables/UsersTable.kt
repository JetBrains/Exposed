package org.example.tables

import org.jetbrains.exposed.sql.Table

const val MAX_USER_NAME_LENGTH = 50

object UsersTable : Table() {
    val name = varchar("name", MAX_USER_NAME_LENGTH)
}
