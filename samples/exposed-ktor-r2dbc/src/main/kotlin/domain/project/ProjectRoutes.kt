@file:Suppress("InvalidPackageDeclaration")

package org.jetbrains.exposed.samples.r2dbc.domain.project

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.plugins.di.*
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing

suspend fun Application.projectRoutes() {
    val projectService = ProjectService(dependencies.resolve<DSLProjectRepository>())

    routing {
        route("/projects") {
            post {
                val project = call.receive<Project>()
                val createdProject = projectService.createProject(project)
                call.respond(HttpStatusCode.Created, createdProject)
            }

            get {
                val allProjects = projectService.getProjects()
                call.respond(HttpStatusCode.OK, allProjects)
            }

            get("{id}") {
                val id = call.parameters["id"]?.toInt()
                    ?: return@get call.respondText("Missing or invalid project ID value", status = HttpStatusCode.BadRequest)

                val project = projectService.getProject(id)
                if (project == null) {
                    call.respondText("Project with ID = $id not found", status = HttpStatusCode.NotFound)
                    return@get
                }

                call.respond(HttpStatusCode.OK, project)
            }

            delete("{id}") {
                val id = call.parameters["id"]?.toInt()
                    ?: return@delete call.respondText("Missing or invalid project ID value", status = HttpStatusCode.BadRequest)

                val success = projectService.removeProject(id)
                if (success) {
                    call.respond(HttpStatusCode.OK)
                } else {
                    call.respondText("Project with ID = $id not found", status = HttpStatusCode.NotFound)
                }
            }
        }
    }
}
