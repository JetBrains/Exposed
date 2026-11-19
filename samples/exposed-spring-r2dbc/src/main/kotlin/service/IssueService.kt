@file:Suppress("InvalidPackageDeclaration")

package org.jetbrains.exposed.samples.spring.service

import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import org.jetbrains.exposed.samples.spring.domain.Issue
import org.jetbrains.exposed.samples.spring.domain.IssueId
import org.jetbrains.exposed.samples.spring.domain.IssuesTable
import org.jetbrains.exposed.samples.spring.domain.UserId
import org.jetbrains.exposed.samples.spring.domain.UsersTable
import org.jetbrains.exposed.samples.spring.domain.toIssue
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.r2dbc.deleteWhere
import org.jetbrains.exposed.v1.r2dbc.insertAndGetId
import org.jetbrains.exposed.v1.r2dbc.selectAll
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
@Transactional
internal class IssueService {

    // read all issues assigned to a user
    suspend fun findIssuesByAssignee(assigneeId: UserId): List<Issue> {
        return (IssuesTable innerJoin UsersTable)
            .selectAll()
            .where { IssuesTable.assigneeId eq assigneeId.value }
            .orderBy(IssuesTable.id)
            .map { it.toIssue() }
            .toList()
    }

    // create issue, returning null if the assignee does not exist
    suspend fun create(request: IssueCreateRequest): IssueId? {
        // Both statements are executed in the same transaction
        UsersTable
            .selectAll()
            .where { UsersTable.id eq request.assigneeId.value }
            .firstOrNull()
            ?: return null

        val id = IssuesTable.insertAndGetId {
            it[title] = request.title
            it[assigneeId] = request.assigneeId.value
        }

        return IssueId(id.value)
    }

    // delete issue, returning whether a matching issue was found
    suspend fun delete(id: IssueId): Boolean {
        val deletedRows = IssuesTable.deleteWhere { IssuesTable.id eq id.value }

        return deletedRows > 0
    }
}

internal data class IssueCreateRequest(
    val title: String,
    val assigneeId: UserId,
)
