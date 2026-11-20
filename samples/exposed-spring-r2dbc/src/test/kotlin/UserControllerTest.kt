@file:Suppress("InvalidPackageDeclaration")

package org.jetbrains.exposed.samples.spring

import org.jetbrains.exposed.samples.spring.controller.UserController.UserCreateRequestForm
import org.jetbrains.exposed.samples.spring.controller.UserController.UserResponse
import org.jetbrains.exposed.samples.spring.controller.UserController.UserUpdateRequestForm
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.test.web.reactive.server.expectBody
import org.springframework.test.web.reactive.server.expectBodyList

internal class UserControllerTest : ApplicationTest() {

    @Test
    fun `create returns id of new user`() {
        val firstId = createUser(name = "Alice")
        val secondId = createUser(name = "Bob")

        assertTrue(firstId > 0)
        assertTrue(secondId > firstId)
    }

    @Test
    fun `create with issues stores user and their issues`() {
        val id = createUser(name = "Carol", age = 25, issueTitles = listOf("First issue", "Second issue"))

        assertEquals(UserResponse(id = id, name = "Carol", age = 25), findUser(id))
        assertEquals(listOf("First issue", "Second issue"), findIssues(assigneeId = id).map { it.title })
    }

    @Test
    fun `create with duplicate issue titles is rolled back and stores nothing`() {
        // The user and the first issue are successfully inserted, before the second issue violates the unique index.
        // Because all 3 statements are executed in the same transaction, the first 2 inserts must be rolled back.
        val nameOfRolledBackUser = "Karen"

        webTestClient.post().uri(USERS_PATH)
            .bodyValue(
                UserCreateRequestForm(
                    name = nameOfRolledBackUser,
                    age = 25,
                    issueTitles = listOf("Same issue", "Same issue"),
                )
            )
            .exchange()
            .expectStatus().isEqualTo(HttpStatus.CONFLICT)

        assertTrue(findAllUsers().none { it.name == nameOfRolledBackUser })
    }

    @Test
    fun `find user by id returns user or not found`() {
        val id = createUser(name = "Alice", age = 30)

        assertEquals(UserResponse(id = id, name = "Alice", age = 30), findUser(id))

        webTestClient.get().uri("$USERS_PATH/{id}", UNKNOWN_ID)
            .exchange()
            .expectStatus().isNotFound
    }

    @Test
    fun `find all users returns created users`() {
        val firstId = createUser(name = "Alice", age = 30)
        val secondId = createUser(name = "Bob", age = 41)

        val users = findAllUsers()

        assertTrue(UserResponse(id = firstId, name = "Alice", age = 30) in users)
        assertTrue(UserResponse(id = secondId, name = "Bob", age = 41) in users)
    }

    @Test
    fun `update changes only provided fields or returns not found`() {
        val id = createUser(name = "Alice", age = 30)

        webTestClient.put().uri("$USERS_PATH/{id}", id)
            .bodyValue(UserUpdateRequestForm(age = 31))
            .exchange()
            .expectStatus().isOk

        assertEquals(UserResponse(id = id, name = "Alice", age = 31), findUser(id))

        webTestClient.put().uri("$USERS_PATH/{id}", UNKNOWN_ID)
            .bodyValue(UserUpdateRequestForm(age = 31))
            .exchange()
            .expectStatus().isNotFound
    }

    @Test
    fun `delete removes user or returns not found`() {
        val id = createUser(name = "Alice")

        webTestClient.delete().uri("$USERS_PATH/{id}", id)
            .exchange()
            .expectStatus().isNoContent

        webTestClient.get().uri("$USERS_PATH/{id}", id)
            .exchange()
            .expectStatus().isNotFound

        webTestClient.delete().uri("$USERS_PATH/{id}", id)
            .exchange()
            .expectStatus().isNotFound
    }

    private fun findUser(id: Long): UserResponse {
        return webTestClient.get().uri("$USERS_PATH/{id}", id)
            .exchange()
            .expectStatus().isOk
            .expectBody<UserResponse>()
            .returnResult()
            .responseBody!!
    }

    // the status is not checked, because no content is returned if there are no users at all
    private fun findAllUsers(): List<UserResponse> {
        return webTestClient.get().uri("$USERS_PATH/")
            .exchange()
            .expectBodyList<UserResponse>()
            .returnResult()
            .responseBody
            .orEmpty()
    }
}
