@file:Suppress("InvalidPackageDeclaration")

package org.jetbrains.exposed.samples.r2dbc.plugins

import io.ktor.server.application.*
import io.ktor.server.plugins.di.*
import org.jetbrains.exposed.samples.r2dbc.domain.comment.DSLCommentRepository
import org.jetbrains.exposed.samples.r2dbc.domain.comment.commentRoutes
import org.jetbrains.exposed.samples.r2dbc.domain.issue.DSLIssueRepository
import org.jetbrains.exposed.samples.r2dbc.domain.issue.issueRoutes
import org.jetbrains.exposed.samples.r2dbc.domain.project.DSLProjectRepository
import org.jetbrains.exposed.samples.r2dbc.domain.project.projectRoutes
import org.jetbrains.exposed.samples.r2dbc.domain.user.DSLUserRepository
import org.jetbrains.exposed.samples.r2dbc.domain.user.userRoutes

suspend fun Application.configureRouting() {
    dependencies {
        provide(DSLProjectRepository::class)
        provide(DSLUserRepository::class)
        provide(DSLIssueRepository::class)
        provide(DSLCommentRepository::class)
    }

    projectRoutes()
    userRoutes()
    issueRoutes()
    commentRoutes()
}
