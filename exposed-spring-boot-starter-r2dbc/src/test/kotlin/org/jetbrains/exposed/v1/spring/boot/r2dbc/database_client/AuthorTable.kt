@file:Suppress("PackageName", "InvalidPackageDeclaration")

package org.jetbrains.exposed.v1.`database-client`

import org.jetbrains.exposed.v1.core.dao.id.UUIDTable

object AuthorTable : UUIDTable("authors") {
    val description = text("description")
}
