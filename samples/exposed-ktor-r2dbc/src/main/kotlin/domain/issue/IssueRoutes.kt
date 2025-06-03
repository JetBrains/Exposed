@file:Suppress("InvalidPackageDeclaration", "MagicNumber")

package org.jetbrains.exposed.samples.r2dbc.domain.issue

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.jetbrains.exposed.samples.r2dbc.domain.project.ProjectId

fun Application.issueRoutes(
    repository: IssueRepository
) {
    val issueService = IssueService(repository)

    routing {
        post("projects/{id}/issues") {
            val issue = call.receive<Issue>()
            val createdIssue = issueService.createIssue(issue)
            call.respond(HttpStatusCode.Created, createdIssue)
        }

        get("projects/{id}/issues/count") {
            val id = call.parameters["id"]?.toInt()
                ?: return@get call.respondText("Missing or invalid ID value for parent Project", status = HttpStatusCode.BadRequest)

            val issueCount = issueService.countIssuesInProject(ProjectId(id))
            call.respond(HttpStatusCode.OK, issueCount)
        }

        get("projects/{id}/issues") {
            val parentId = call.parameters["id"]?.toInt()
                ?: return@get call.respondText("Missing or invalid ID value for parent Project", status = HttpStatusCode.BadRequest)

            val qp = call.queryParameters
            val limit = qp["limit"]?.toInt() ?: 50
            val offset = qp["offset"]?.toInt() ?: 0

            val allIssues = issueService.getIssuesInProject(ProjectId(parentId), limit, offset)
            call.respond(HttpStatusCode.OK, allIssues)
        }

        get("projects/{id}/issues/{code}") {
            val code = call.parameters["code"]
                ?: return@get call.respondText("Missing or invalid issue code", status = HttpStatusCode.BadRequest)

            val issue = issueService.getIssue(code)
            if (issue == null) {
                call.respondText("Issue $code not found", status = HttpStatusCode.NotFound)
                return@get
            }

            call.respond(HttpStatusCode.OK, issue)
        }

        put("projects/{id}/issues/{code}") {
            val updated = call.receive<Issue>()
            val dbUpdated = issueService.editIssue(updated)
            call.respond(HttpStatusCode.OK, dbUpdated)
        }

        delete("projects/{id}/issues/{code}") {
            val code = call.parameters["code"]
                ?: return@delete call.respondText("Missing or invalid issue code", status = HttpStatusCode.BadRequest)

            val success = issueService.removeIssue(code)
            if (success) {
                call.respond(HttpStatusCode.OK)
            } else {
                call.respondText("Issue $code not found", status = HttpStatusCode.NotFound)
            }
        }
    }
}
