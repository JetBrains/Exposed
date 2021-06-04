package org.jetbrains.exposed.`jdbc-template`

import org.jetbrains.exposed.dao.UUIDEntity
import org.jetbrains.exposed.dao.UUIDEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.UUIDTable
import java.util.UUID

object AuthorTable : UUIDTable("authors") {
    val description = text("description")
}

object BookTable : UUIDTable("books") {
    val description = text("description")
}

class Book(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : UUIDEntityClass<Book>(AuthorTable)
    var description by AuthorTable.description
}
