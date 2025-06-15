@file:Suppress("InvalidPackageDeclaration")

package org.jetbrains.exposed.samples.r2dbc.domain.issue

import org.jetbrains.exposed.samples.r2dbc.domain.project.ProjectId

class IssueService(
    private val issueRepository: IssueRepository
) {
    suspend fun createIssue(issue: Issue): Issue {
        return issueRepository.save(issue)
    }

    suspend fun countIssuesInProject(projectId: ProjectId): Int {
        return issueRepository.count(projectId)
    }

    suspend fun getIssuesInProject(projectId: ProjectId, limit: Int, offset: Int): List<Issue> {
        return issueRepository.findAll(projectId, limit, offset)
    }

    suspend fun getIssue(code: String): Issue? {
        return issueRepository.findByCode(code)
    }

    suspend fun editIssue(issue: Issue): Issue {
        return issueRepository.update(issue)
    }

    suspend fun removeIssue(code: String): Boolean {
        return issueRepository.delete(code)
    }
}
