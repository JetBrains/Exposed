package org.example.tables

import org.jetbrains.exposed.v1.core.Table

const val MAX_USER_NAME_LENGTH = 50

object UsersTable : Table() {
    val id = integer("id").autoIncrement()
    val name = varchar("name", MAX_USER_NAME_LENGTH)
}
