@file:Suppress("InvalidPackageDeclaration")

package org.jetbrains.exposed.samples.r2dbc.domain.comment

import kotlinx.coroutines.flow.singleOrNull
import org.jetbrains.exposed.samples.r2dbc.domain.BaseRepository
import org.jetbrains.exposed.samples.r2dbc.domain.issue.Issues
import org.jetbrains.exposed.v1.core.JoinType
import org.jetbrains.exposed.v1.datetime.CurrentTimestamp
import org.jetbrains.exposed.v1.r2dbc.deleteReturning
import org.jetbrains.exposed.v1.r2dbc.insertReturning
import org.jetbrains.exposed.v1.r2dbc.update
import org.jetbrains.exposed.v1.r2dbc.updateReturning

interface CommentRepository : BaseRepository {
    suspend fun save(comment: Comment): Comment

    suspend fun update(comment: Comment): Comment

    suspend fun delete(id: CommentId): Boolean
}

class DSLCommentRepository : CommentRepository {
    override suspend fun save(comment: Comment): Comment = dbQuery {
        val dbGeneratedColumns = listOf(Comments.id, Comments.createdAt, Comments.modifiedAt)
        val returnStmt = Comments
            .insertReturning(
                returning = dbGeneratedColumns
            ) {
                it[authorId] = comment.authorId.value
                it[content] = comment.content
                it[issueNumber] = comment.issueNumber
                it[projectId] = comment.projectId.value
            }

        val createdComment = returnStmt
            .singleOrNull()
            ?.let { returnRowToComment(it, comment) }
            ?: error("Database-generated values not returned on comment creation")

        require(createdComment.id != null) { "Missing or invalid comment ID value" }

        // sync Issues table to reflect modified TS in Comments table

        Issues.join(
            otherTable = Comments,
            joinType = JoinType.INNER,
            onColumn = Issues.projectId,
            otherColumn = Comments.projectId,
            additionalConstraint = { Issues.number eq Comments.issueNumber }
        )
            .update(
                where = { Comments.id eq createdComment.id.value }
            ) {
                it[Issues.modifiedAt] = Comments.createdAt
            }

        createdComment
    }

    override suspend fun update(comment: Comment): Comment = dbQuery {
        require(comment.id != null) { "Missing or invalid comment ID value" }

        val returnStmt = Comments
            .updateReturning(
                returning = listOf(Comments.modifiedAt, Comments.createdAt),
                where = { Comments.id eq comment.id.value }
            ) {
                it[content] = comment.content
                it[modifiedAt] = CurrentTimestamp
            }

        returnStmt
            .singleOrNull()
            ?.let { returnRowToComment(it, comment) }
            ?: error("Database-generated values not returned on comment update")
    }

    override suspend fun delete(id: CommentId): Boolean = dbQuery {
        val returnStmt = Comments
            .deleteReturning(
                returning = listOf(Comments.projectId, Comments.issueNumber),
                where = { Comments.id eq id.value }
            )

        val (parentProjectId, parentIssueNumber) = returnStmt
            .singleOrNull()
            ?.let { it[Comments.projectId] to it[Comments.issueNumber] }
            ?: error("Database-generated values not returned on comment delete")

        // sync Issues table

        val parentRowsUpdated = Issues.update(
            where = { issueKeysMatch(parentProjectId, parentIssueNumber) }
        ) {
            it[modifiedAt] = CurrentTimestamp
        }

        parentRowsUpdated == 1
    }
}
