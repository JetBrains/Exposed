@file:Suppress("InvalidPackageDeclaration")

package org.jetbrains.exposed.samples.r2dbc.domain.issue

import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.singleOrNull
import kotlinx.coroutines.flow.toList
import org.jetbrains.exposed.samples.r2dbc.domain.BaseRepository
import org.jetbrains.exposed.samples.r2dbc.domain.comment.Comments
import org.jetbrains.exposed.samples.r2dbc.domain.project.ProjectId
import org.jetbrains.exposed.samples.r2dbc.domain.project.Projects
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.core.SqlExpressionBuilder.eq
import org.jetbrains.exposed.v1.datetime.CurrentTimestamp
import org.jetbrains.exposed.v1.r2dbc.deleteWhere
import org.jetbrains.exposed.v1.r2dbc.insertReturning
import org.jetbrains.exposed.v1.r2dbc.select
import org.jetbrains.exposed.v1.r2dbc.updateReturning

interface IssueRepository : BaseRepository {
    suspend fun save(issue: Issue): Issue

    suspend fun count(projectId: ProjectId): Int

    suspend fun findAll(projectId: ProjectId, limit: Int, offset: Int): List<Issue>

    suspend fun findByCode(code: String): Issue?

    suspend fun update(issue: Issue): Issue

    suspend fun delete(code: String): Boolean
}

class DSLIssueRepository : IssueRepository {
    override suspend fun save(issue: Issue): Issue = dbQuery {
        val dbGeneratedColumns = listOf(Issues.number, Issues.code, Issues.createdAt, Issues.modifiedAt)
        val returnStmt = Issues
            .insertReturning(
                returning = dbGeneratedColumns
            ) {
                it[projectId] = issue.projectId.value
                it[projectCode] = issue.code ?: ""
                it[authorId] = issue.authorId.value
                it[title] = issue.title
                it[description] = issue.description
                it[priority] = issue.priority
                it[state] = issue.state
                it[assigneeId] = issue.assigneeId?.value
                it[watchers] = issue.watchers
            }

        returnStmt
            .singleOrNull()
            ?.let { returnRowToIssue(it, issue) }
            ?: error("Database-generated values not returned on issue creation")
    }

    override suspend fun count(projectId: ProjectId): Int = dbQuery {
        Issues
            .select(Issues.number, Issues.projectId)
            .where { issueProjectMatches(projectId.value) }
            .count()
            .toInt()
    }

    override suspend fun findAll(projectId: ProjectId, limit: Int, offset: Int): List<Issue> = dbQuery {
        val commentCount = Comments.id.count().alias("comment_count")

        val doubleJoinQuery = (Issues leftJoin Projects)
            .joinQuery(
                on = { queryAlias ->
                    Issues.projectId eq queryAlias[Comments.projectId] and
                        (Issues.number eq queryAlias[Comments.issueNumber])
                },
                joinType = JoinType.LEFT,
            ) {
                Comments
                    .select(Comments.issueNumber, Comments.projectId, commentCount)
                    .groupBy(Comments.issueNumber, Comments.projectId)
            }

        val subQueryCount = doubleJoinQuery.lastQueryAlias?.let {
            Coalesce(it[commentCount], longLiteral(0)).alias("sqc")
        } ?: error("Comments query not joined properly to issues query")

        val summaryColumns = Issues.columns - Issues.description

        doubleJoinQuery
            .select(summaryColumns + subQueryCount)
            .where { issueProjectMatches(projectId.value) }
            .orderBy(Issues.modifiedAt, SortOrder.DESC)
            .limit(limit)
            .offset(offset.toLong())
            .map {
                rowToIssueSummarized(it, subQueryCount)
            }
            .toList()
    }

    override suspend fun findByCode(code: String): Issue? = dbQuery {
        (Projects rightJoin Issues)
            .join(
                otherTable = Comments,
                joinType = JoinType.LEFT,
                onColumn = Issues.projectId,
                otherColumn = Comments.projectId,
                additionalConstraint = { Issues.number eq Comments.issueNumber }
            )
            .select(Issues.fields + Comments.columns)
            .where { Issues.code eq code }
            .toList()
            .takeIf { it.isNotEmpty() }
            ?.let(::rowsToIssueDetailed)
    }

    override suspend fun update(issue: Issue): Issue = dbQuery {
        require(issue.number != null) { "Missing or invalid issue ID value" }

        val returnStmt = Issues
            .updateReturning(
                returning = listOf(Issues.modifiedAt, Issues.createdAt),
                where = { issueKeysMatch(issue.projectId.value, issue.number) }
            ) {
                it[title] = issue.title
                it[description] = issue.description
                it[priority] = issue.priority
                it[state] = issue.state
                it[assigneeId] = issue.assigneeId?.value
                it[watchers] = issue.watchers
                it[upvotes] = issue.upvotes
                it[modifiedAt] = CurrentTimestamp
            }

        returnStmt
            .singleOrNull()
            ?.let { returnRowToIssue(it, issue) }
            ?: error("Database-generated value not returned on issue update")
    }

    override suspend fun delete(code: String): Boolean = dbQuery {
        val rowsDeleted = Issues.deleteWhere { Issues.code eq code }

        rowsDeleted == 1
    }
}
