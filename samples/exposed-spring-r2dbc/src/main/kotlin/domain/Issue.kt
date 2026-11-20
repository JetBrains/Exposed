@file:Suppress("InvalidPackageDeclaration")

package org.jetbrains.exposed.samples.spring.domain

import org.jetbrains.exposed.v1.core.ResultRow

internal data class Issue(
    val id: IssueId,
    val title: String,
    val assignee: User,
)

internal data class IssueId(val value: Long)

internal fun ResultRow.toIssue() = Issue(
    id = IssueId(this[IssuesTable.id].value),
    title = this[IssuesTable.title],
    assignee = toUser(),
)
