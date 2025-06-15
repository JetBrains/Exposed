@file:Suppress("InvalidPackageDeclaration")

package org.jetbrains.exposed.samples.r2dbc.domain.comment

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.di.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

suspend fun Application.commentRoutes() {
    val commentService = CommentService(dependencies.resolve<DSLCommentRepository>())

    routing {
        route("projects/{id}/issues/{code}/comments") {
            post {
                val comment = call.receive<Comment>()
                val createdComment = commentService.createComment(comment)
                call.respond(HttpStatusCode.Created, createdComment)
            }

            put("{commentId}") {
                val updated = call.receive<Comment>()
                val dbUpdated = commentService.editComment(updated)
                call.respond(HttpStatusCode.OK, dbUpdated)
            }

            delete("{commentId}") {
                val id = call.parameters["commentId"]?.toLong()
                    ?: return@delete call.respondText("Missing or invalid comment ID value", status = HttpStatusCode.BadRequest)

                val success = commentService.removeComment(id)
                if (success) {
                    call.respond(HttpStatusCode.OK)
                } else {
                    call.respondText("Comment with ID = $id not found", status = HttpStatusCode.NotFound)
                }
            }
        }
    }
}
