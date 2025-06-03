@file:Suppress("InvalidPackageDeclaration")

package org.jetbrains.exposed.samples.r2dbc.domain.comment

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.samples.r2dbc.domain.project.ProjectId
import org.jetbrains.exposed.samples.r2dbc.domain.user.UserId

@JvmInline
@Serializable
value class CommentId(val value: Long)

@Serializable
data class Comment(
    val id: CommentId? = null,
    val issueNumber: Long,
    val projectId: ProjectId,
    val authorId: UserId,
    val content: String,
    val createdAt: Instant? = null,
    val modifiedAt: Instant? = null
)
