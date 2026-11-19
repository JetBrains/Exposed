@file:Suppress("InvalidPackageDeclaration")

package org.jetbrains.exposed.samples.spring.domain

import org.jetbrains.exposed.v1.core.dao.id.LongIdTable

internal object UsersTable : LongIdTable(name = "users") {
    val name = varchar("name", length = 50)
    val age = integer("age")
}
