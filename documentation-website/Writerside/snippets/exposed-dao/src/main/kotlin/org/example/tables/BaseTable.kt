package org.example.tables

import kotlinx.datetime.LocalDateTime
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.kotlin.datetime.CurrentDateTime
import org.jetbrains.exposed.sql.kotlin.datetime.datetime

abstract class BaseTable(name: String = "") : IntIdTable(name) {
    val created: Column<LocalDateTime> = datetime("created")
        .defaultExpression(CurrentDateTime)
    val modified: Column<LocalDateTime?> = datetime("updated").nullable()
}
