package org.example.tables

import org.jetbrains.exposed.v1.core.Table

const val MAX_TITLE_LENGTH = 150

object FilmsTable : Table() {
    val id = integer("id").autoIncrement()
    val title = varchar("title", MAX_TITLE_LENGTH)
    val rating = double("rating")
    val nominated = bool("nominated")

    override val primaryKey = PrimaryKey(id)
}
