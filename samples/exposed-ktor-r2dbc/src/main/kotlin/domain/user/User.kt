@file:Suppress("InvalidPackageDeclaration")

package org.jetbrains.exposed.samples.r2dbc.domain.user

import kotlinx.serialization.Serializable
import org.jetbrains.exposed.samples.r2dbc.domain.project.Project

@JvmInline
@Serializable
value class UserId(val value: Int)

@Serializable
data class User(
    val id: UserId? = null,
    val fullName: String,
    val username: String,
    val settings: UserSettings
)

@Serializable
data class UserSettings(
    val defaultProject: Project,
    val defaultSort: UserSort,
)

enum class UserSort {
    RELEVANCE,
    UPDATED
}
