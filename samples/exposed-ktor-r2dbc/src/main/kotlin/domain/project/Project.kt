@file:Suppress("InvalidPackageDeclaration")

package org.jetbrains.exposed.samples.r2dbc.domain.project

import kotlinx.serialization.Serializable

@JvmInline
@Serializable
value class ProjectId(val value: Int)

@Serializable
data class Project(
    val id: ProjectId? = null,
    val name: String,
    val code: String
)
