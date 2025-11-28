@file:Suppress("InvalidPackageDeclaration")

package org.jetbrains.exposed.samples.spring.controller

import org.jetbrains.exposed.samples.spring.domain.UserId
import org.jetbrains.exposed.samples.spring.service.UserCreateRequest
import org.jetbrains.exposed.samples.spring.service.UserService
import org.jetbrains.exposed.samples.spring.service.UserUpdateRequest
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/users")
class UserController(
    private val userService: UserService,
) {
    // Read User
    @GetMapping("/{id}")
    fun findUserById(
        @PathVariable id: Long
    ): ResponseEntity<UserResponse> {
        val user = userService.findUserById(UserId(id))

        return if (user != null) {
            ResponseEntity.ok(
                UserResponse(
                    id = user.id.value,
                    name = user.name,
                    age = user.age,
                )
            )
        } else {
            ResponseEntity.notFound().build()
        }
    }

    data class UserResponse(
        val id: Long,
        val name: String,
        val age: Int,
    )

    // Read All Users
    @GetMapping("/")
    fun findAllUsers(): ResponseEntity<List<UserResponse>> {
        val users = userService.findAllUsers()
        val response = users.map { user ->
            UserResponse(
                id = user.id.value,
                name = user.name,
                age = user.age,
            )
        }

        return if (response.isNotEmpty()) {
            ResponseEntity.ok(response)
        } else {
            ResponseEntity.noContent().build()
        }
    }

    // Create User
    @PostMapping
    fun create(
        @RequestBody form: UserCreateRequestForm
    ): ResponseEntity<UserCreateResponse> {
        val userId = userService.create(
            UserCreateRequest(
                name = form.name,
                age = form.age,
            )
        )

        return ResponseEntity.ok(
            UserCreateResponse(
                id = userId.value,
            )
        )
    }

    data class UserCreateRequestForm(
        val name: String,
        val age: Int,
    )

    data class UserCreateResponse(val id: Long)

    // Update User
    @PutMapping("/{id}")
    fun update(
        @PathVariable id: Long,
        @RequestBody form: UserUpdateRequestForm
    ): ResponseEntity<Unit> {
        userService.update(
            id = id,
            request = UserUpdateRequest(
                name = form.name,
                age = form.age,
            )
        )

        return ResponseEntity.ok().build()
    }

    data class UserUpdateRequestForm(
        val name: String? = null,
        val age: Int? = null,
    )

    // Delete User
    @DeleteMapping("/{id}")
    fun delete(
        @PathVariable id: Long
    ): ResponseEntity<Unit> {
        userService.delete(UserId(id))

        return ResponseEntity.noContent().build()
    }
}
