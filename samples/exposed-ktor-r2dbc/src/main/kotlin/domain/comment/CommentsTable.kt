@file:Suppress("InvalidPackageDeclaration", "MatchingDeclarationName")

package org.jetbrains.exposed.samples.r2dbc.domain.comment

import org.jetbrains.exposed.samples.r2dbc.domain.issue.Issues
import org.jetbrains.exposed.samples.r2dbc.domain.project.ProjectId
import org.jetbrains.exposed.samples.r2dbc.domain.user.UserId
import org.jetbrains.exposed.samples.r2dbc.domain.user.Users
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.datetime.CurrentTimestamp
import org.jetbrains.exposed.v1.datetime.timestamp

object Comments : Table("comments") {
    val id = long("id").autoIncrement()

    val issueNumber = long("issue_number")
    val projectId = integer("project_id")

    val authorId = reference("author_id", Users.id)

    val content = text("content")

    val createdAt = timestamp("created_at").defaultExpression(CurrentTimestamp)
    val modifiedAt = timestamp("modified_at").defaultExpression(CurrentTimestamp)

    override val primaryKey = PrimaryKey(id)

    init {
        foreignKey(
            issueNumber,
            projectId,
            target = Issues.primaryKey,
            onDelete = ReferenceOption.CASCADE,
        )
    }
}

fun rowToComment(result: ResultRow): Comment? {
    if (result[Comments.id] == null) return null

    return Comment(
        id = CommentId(result[Comments.id]),
        issueNumber = result[Comments.issueNumber],
        projectId = ProjectId(result[Comments.projectId]),
        authorId = UserId(result[Comments.authorId]),
        content = result[Comments.content],
        createdAt = result[Comments.createdAt],
        modifiedAt = result[Comments.modifiedAt]
    )
}

fun returnRowToComment(
    result: ResultRow,
    original: Comment,
): Comment {
    val dbId = result.getOrNull(Comments.id)?.let { CommentId(it) } ?: original.id
    val dbCreatedAt = result.getOrNull(Comments.createdAt) ?: original.createdAt
    val dbModifiedAt = result.getOrNull(Comments.modifiedAt) ?: original.modifiedAt

    return original.copy(
        id = dbId,
        createdAt = dbCreatedAt,
        modifiedAt = dbModifiedAt
    )
}
