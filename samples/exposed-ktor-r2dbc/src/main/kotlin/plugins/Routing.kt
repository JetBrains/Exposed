@file:Suppress("InvalidPackageDeclaration")

package org.jetbrains.exposed.samples.r2dbc.plugins

import io.ktor.server.application.*
import org.jetbrains.exposed.samples.r2dbc.domain.comment.DSLCommentRepository
import org.jetbrains.exposed.samples.r2dbc.domain.comment.commentRoutes
import org.jetbrains.exposed.samples.r2dbc.domain.issue.DSLIssueRepository
import org.jetbrains.exposed.samples.r2dbc.domain.issue.issueRoutes
import org.jetbrains.exposed.samples.r2dbc.domain.project.DSLProjectRepository
import org.jetbrains.exposed.samples.r2dbc.domain.project.projectRoutes
import org.jetbrains.exposed.samples.r2dbc.domain.user.DSLUserRepository
import org.jetbrains.exposed.samples.r2dbc.domain.user.userRoutes

fun Application.configureRouting() {
    projectRoutes(DSLProjectRepository())
    userRoutes(DSLUserRepository())
    issueRoutes(DSLIssueRepository())
    commentRoutes(DSLCommentRepository())
}
