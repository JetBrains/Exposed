@file:Suppress("InvalidPackageDeclaration")

package org.jetbrains.exposed.samples.spring.domain

data class User(
    val id: UserId,
    val name: String,
    val age: Int,
)

@JvmInline
value class UserId(val value: Long)
