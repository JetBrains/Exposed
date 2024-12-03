package org.example.entities

import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IntIdTable
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

object TableWithText : IntIdTable() {
    val text = varchar("text", length = 2048)
}

class EntityWithBase64(id: EntityID<Int>) : IntEntity(id) {
    @OptIn(ExperimentalEncodingApi::class)
    var base64: String by TableWithText.text
        .memoizedTransform(
            wrap = { Base64.encode(it.toByteArray()) },
            unwrap = { Base64.decode(it).toString() }
        )

    companion object :
        IntEntityClass<EntityWithUInt>(TableWithText)
}
