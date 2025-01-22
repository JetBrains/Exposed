package org.example.entities

import kotlinx.datetime.LocalDateTime
import org.example.tables.BaseTable
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IntIdTable

abstract class BaseEntity(id: EntityID<Int>, table: BaseTable) : IntEntity(id) {
    val created: LocalDateTime by table.created
    var modified: LocalDateTime? by table.modified
}
