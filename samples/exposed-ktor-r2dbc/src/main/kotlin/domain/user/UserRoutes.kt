@file:Suppress("InvalidPackageDeclaration")

package org.jetbrains.exposed.samples.r2dbc.domain.user

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Application.userRoutes(
    repository: UserRepository
) {
    val userService = UserService(repository)

    routing {
        post("/users") {
            val user = call.receive<User>()
            val createdUser = userService.createUser(user)
            call.respond(HttpStatusCode.Created, createdUser)
        }

        get("/users") {
            val allUsers = userService.getUsers()
            call.respond(HttpStatusCode.OK, allUsers)
        }

        get("/users/{id}") {
            val id = call.parameters["id"]?.toInt()
                ?: return@get call.respondText("Missing or invalid user ID value", status = HttpStatusCode.BadRequest)

            val user = userService.getUser(id)
            if (user == null) {
                call.respondText("User with ID = $id not found", status = HttpStatusCode.NotFound)
                return@get
            }

            call.respond(HttpStatusCode.OK, user)
        }

        put("/users/{id}") {
            val id = call.parameters["id"]?.toInt()
                ?: return@put call.respondText("Missing or invalid user ID value", status = HttpStatusCode.BadRequest)

            val updatedSettings = call.receive<UserSettings>()
            val success = userService.editUserSettings(id, updatedSettings)
            if (success) {
                call.respond(HttpStatusCode.OK)
            } else {
                call.respondText("User with ID = $id not found", status = HttpStatusCode.NotFound)
            }
        }
    }
}
