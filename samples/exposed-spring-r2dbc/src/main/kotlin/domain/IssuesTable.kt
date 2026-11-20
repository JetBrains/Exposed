@file:Suppress("InvalidPackageDeclaration")

package org.jetbrains.exposed.samples.spring.domain

import org.jetbrains.exposed.v1.core.dao.id.LongIdTable

internal object IssuesTable : LongIdTable(name = "issues") {
    val title = varchar("title", length = 100)
    val assigneeId = reference("assignee_id", UsersTable)

    init {
        // a user cannot be assigned two issues with the same title
        uniqueIndex(assigneeId, title)
    }
}
