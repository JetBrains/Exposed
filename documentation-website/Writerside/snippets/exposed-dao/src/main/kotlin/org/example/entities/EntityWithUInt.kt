package org.example.entities

import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.IntIdTable
import org.jetbrains.exposed.v1.dao.IntEntity
import org.jetbrains.exposed.v1.dao.IntEntityClass

object TableWithUnsignedInteger : IntIdTable() {
    val uint = integer("uint")
}

class EntityWithUInt(id: EntityID<Int>) : IntEntity(id) {
    var uint: UInt by TableWithUnsignedInteger.uint.transform({ it.toInt() }, { it.toUInt() })

    companion object : IntEntityClass<EntityWithUInt>(TableWithUnsignedInteger)
}
