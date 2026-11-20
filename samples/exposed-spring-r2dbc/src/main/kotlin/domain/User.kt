@file:Suppress("InvalidPackageDeclaration")

package org.jetbrains.exposed.samples.spring.domain

import org.jetbrains.exposed.v1.core.ResultRow

internal data class User(
    val id: UserId,
    val name: String,
    val age: Int,
)

internal data class UserId(val value: Long)

internal fun ResultRow.toUser() = User(
    id = UserId(this[UsersTable.id].value),
    name = this[UsersTable.name],
    age = this[UsersTable.age],
)
