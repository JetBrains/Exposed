package org.example.entities

import org.jetbrains.exposed.v1.dao.IntEntity
import org.jetbrains.exposed.v1.dao.IntEntityClass
import org.jetbrains.exposed.v1.dao.id.EntityID
import org.jetbrains.exposed.v1.dao.id.IntIdTable
import java.util.Base64

object TableWithText : IntIdTable() {
    val text = varchar("text", length = 2048)
}

class EntityWithBase64(id: EntityID<Int>) : IntEntity(id) {
    var base64: String by TableWithText.text
        .memoizedTransform(
            wrap = { Base64.getEncoder().encodeToString(it.toByteArray()) },
            unwrap = { Base64.getDecoder().decode(it).toString() }
        )

    companion object :
        IntEntityClass<EntityWithBase64>(TableWithText)
}
