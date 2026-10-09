@file:Suppress("PackageName", "InvalidPackageDeclaration")

package org.jetbrains.exposed.v1.`database-client`

import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.java.UUIDTable
import org.jetbrains.exposed.v1.dao.r2dbc.ExperimentalR2dbcDaoApi
import org.jetbrains.exposed.v1.dao.r2dbc.java.UUIDEntity
import org.jetbrains.exposed.v1.dao.r2dbc.java.UUIDEntityClass
import java.util.UUID

object AuthorTable : UUIDTable("authors") {
    val description = text("description")
}

object BookTable : UUIDTable("books") {
    val description = text("description")
}

@OptIn(ExperimentalR2dbcDaoApi::class)
class Book(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : UUIDEntityClass<Book>(AuthorTable)
    var description by AuthorTable.description
}
