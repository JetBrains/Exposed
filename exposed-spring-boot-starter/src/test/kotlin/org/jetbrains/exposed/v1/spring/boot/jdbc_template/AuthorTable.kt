@file:Suppress("PackageName", "InvalidPackageDeclaration")

package org.jetbrains.exposed.v1.`jdbc-template`

import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.UuidTable
import org.jetbrains.exposed.v1.dao.UuidEntity
import org.jetbrains.exposed.v1.dao.UuidEntityClass
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

object AuthorTable : UuidTable("authors") {
    val description = text("description")
}

object BookTable : UuidTable("books") {
    val description = text("description")
}

@OptIn(ExperimentalUuidApi::class)
class Book(id: EntityID<Uuid>) : UuidEntity(id) {
    companion object : UuidEntityClass<Book>(AuthorTable)
    var description by AuthorTable.description
}
