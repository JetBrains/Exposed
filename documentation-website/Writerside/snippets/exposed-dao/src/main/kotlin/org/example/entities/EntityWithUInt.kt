package org.example.entities

import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IntIdTable

object TableWithUnsignedInteger : IntIdTable() {
    val uint = integer("uint")
}

class EntityWithUInt(id: EntityID<Int>) : IntEntity(id) {
    var uint: UInt by TableWithUnsignedInteger.uint.transform({ it.toInt() }, { it.toUInt() })

    companion object : IntEntityClass<EntityWithUInt>(TableWithUnsignedInteger)
}
