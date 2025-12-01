@file:Suppress("InvalidPackageDeclaration")

package org.jetbrains.exposed.samples.r2dbc.domain.issue

import kotlinx.serialization.Serializable
import org.jetbrains.exposed.samples.r2dbc.domain.comment.Comment
import org.jetbrains.exposed.samples.r2dbc.domain.project.ProjectId
import org.jetbrains.exposed.samples.r2dbc.domain.user.UserId
import kotlin.time.Instant

@Serializable
data class Issue(
    val number: Long? = null,
    val projectId: ProjectId,
    val code: String? = null,
    val authorId: UserId,
    val assigneeId: UserId? = null,
    val title: String,
    val description: String = "",
    val priority: IssuePriority,
    val state: IssueState,
    val watchers: List<Int>,
    val upvotes: List<Int>,
    val createdAt: Instant? = null,
    val modifiedAt: Instant? = null,
    val commentCount: Int? = null,
    val comments: List<Comment> = emptyList()
)

enum class IssuePriority {
    NORMAL,
    MINOR,
    MAJOR
}

enum class IssueState {
    SUBMITTED,
    IN_PROGRESS,
    COMPLETED
}
