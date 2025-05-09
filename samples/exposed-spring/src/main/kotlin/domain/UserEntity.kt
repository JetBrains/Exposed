@file:Suppress("InvalidPackageDeclaration")

package org.jetbrains.exposed.samples.spring.domain

import org.jetbrains.exposed.v1.dao.id.LongIdTable

object UserEntity : LongIdTable() {
    val name = varchar("name", length = 50)
    val age = integer("age")
}
