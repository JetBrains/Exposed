package org.example.tables

import kotlinx.datetime.LocalDateTime
import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.dao.id.IntIdTable
import org.jetbrains.exposed.v1.kotlin.datetime.CurrentDateTime
import org.jetbrains.exposed.v1.kotlin.datetime.datetime

abstract class BaseTable(name: String = "") : IntIdTable(name) {
    val created: Column<LocalDateTime> = datetime("created")
        .defaultExpression(CurrentDateTime)
    val modified: Column<LocalDateTime?> = datetime("updated").nullable()
}
