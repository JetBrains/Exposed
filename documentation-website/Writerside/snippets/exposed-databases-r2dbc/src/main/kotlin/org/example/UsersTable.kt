@file:Suppress("InvalidPackageDeclaration", "MatchingDeclarationName", "MagicNumber")

package org.example

import org.jetbrains.exposed.v1.core.Table

object UsersTable : Table("users") {
    val id = integer("id").autoIncrement()
    val firstName = varchar("first_name", 128)
}
