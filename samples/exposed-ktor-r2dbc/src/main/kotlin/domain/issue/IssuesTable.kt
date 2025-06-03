@file:Suppress("InvalidPackageDeclaration", "MatchingDeclarationName", "MagicNumber")

package org.jetbrains.exposed.samples.r2dbc.domain.issue

import org.jetbrains.exposed.samples.r2dbc.domain.comment.rowToComment
import org.jetbrains.exposed.samples.r2dbc.domain.project.ProjectId
import org.jetbrains.exposed.samples.r2dbc.domain.project.Projects
import org.jetbrains.exposed.samples.r2dbc.domain.user.UserId
import org.jetbrains.exposed.samples.r2dbc.domain.user.Users
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.core.Function
import org.jetbrains.exposed.v1.datetime.CurrentTimestamp
import org.jetbrains.exposed.v1.datetime.timestamp

object Issues : Table("issues") {
    val number = long("id")
        .autoIncrement(Sequence("custom_seq", startWith = 100))
    val projectId = reference(
        name = "project_id",
        refColumn = Projects.id,
        onDelete = ReferenceOption.CASCADE
    )

    val projectCode = varchar("project_code", 7)

    // Column for storing database-generated strings of format XXXX-###, based on other column values
    val code = varchar("code", 32)
        .withDefinition("GENERATED ALWAYS AS (", ConcatOp("-", projectCode, number), ") STORED")
        .databaseGenerated()

    val authorId = reference("author_id", Users.id)
    val assigneeId = optReference("assignee_id", Users.id)

    val title = text("title")
    val description = text("description")

    val priority = enumerationByName<IssuePriority>("priority", 16)
    val state = enumerationByName<IssueState>("state", 16)

    val watchers = array<Int>("watchers")
    val upvotes = array<Int>("upvotes").default(emptyList())

    val createdAt = timestamp("created_at").defaultExpression(CurrentTimestamp)
    val modifiedAt = timestamp("modified_at").defaultExpression(CurrentTimestamp)

    override val primaryKey = PrimaryKey(number, projectId)
}

private class ConcatOp(
    val separator: String,
    vararg val expr: Expression<*>
) : Function<String>(TextColumnType()) {
    override fun toQueryBuilder(queryBuilder: QueryBuilder) {
        expr.toList().appendTo(queryBuilder, separator = " || '$separator' || ") {
            if (it is Column<*>) {
                append(it.nameInDatabaseCase())
            } else {
                append(it)
            }
        }
    }
}

fun rowToIssueSummarized(
    result: ResultRow,
    countExpression: ExpressionWithColumnTypeAlias<Long>
): Issue {
    val issue = rowToIssue(result, isSummarized = true)
    return issue.copy(commentCount = result[countExpression].toInt())
}

fun rowsToIssueDetailed(result: List<ResultRow>): Issue {
    val issue = rowToIssue(result.first(), isSummarized = false)
    val comments = result.mapNotNull(::rowToComment)
    return issue.copy(
        commentCount = comments.size,
        comments = comments
    )
}

fun returnRowToIssue(
    result: ResultRow,
    original: Issue,
): Issue {
    val dbNumber = result.getOrNull(Issues.number) ?: original.number
    val dbCode = result.getOrNull(Issues.code) ?: original.code
    val dbWatchers = result.getOrNull(Issues.watchers) ?: original.watchers
    val dbUpvotes = result.getOrNull(Issues.upvotes) ?: original.upvotes
    val dbCreatedAt = result.getOrNull(Issues.createdAt) ?: original.createdAt
    val dbModifiedAt = result.getOrNull(Issues.modifiedAt) ?: original.modifiedAt

    return original.copy(
        number = dbNumber,
        code = dbCode,
        watchers = dbWatchers,
        upvotes = dbUpvotes,
        createdAt = dbCreatedAt,
        modifiedAt = dbModifiedAt
    )
}

private fun rowToIssue(
    result: ResultRow,
    isSummarized: Boolean
): Issue = Issue(
    number = result[Issues.number],
    projectId = ProjectId(result[Issues.projectId]),
    code = result[Issues.code],
    authorId = UserId(result[Issues.authorId]),
    assigneeId = result[Issues.assigneeId]?.let { UserId(it) },
    title = result[Issues.title],
    description = if (isSummarized) "" else result[Issues.description],
    priority = result[Issues.priority],
    state = result[Issues.state],
    watchers = result[Issues.watchers],
    upvotes = result[Issues.upvotes],
    createdAt = result[Issues.createdAt],
    modifiedAt = result[Issues.modifiedAt]
)
