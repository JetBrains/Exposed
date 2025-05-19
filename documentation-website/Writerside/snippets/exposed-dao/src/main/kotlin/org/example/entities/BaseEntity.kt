package org.example.entities

import kotlinx.datetime.LocalDateTime
import org.example.tables.BaseTable
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.dao.IntEntity

abstract class BaseEntity(id: EntityID<Int>, table: BaseTable) : IntEntity(id) {
    val created: LocalDateTime by table.created
    var modified: LocalDateTime? by table.modified
}
