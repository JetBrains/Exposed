@file:Suppress("InvalidPackageDeclaration")

package org.jetbrains.exposed.samples.spring

import org.jetbrains.exposed.samples.spring.controller.IssueController.IssueCreateRequestForm
import org.jetbrains.exposed.samples.spring.controller.IssueController.IssueCreateResponse
import org.jetbrains.exposed.samples.spring.controller.IssueController.IssueResponse
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.test.web.reactive.server.expectBody

internal class IssueControllerTest : ApplicationTest() {

    @Test
    fun `create returns id of new issue`() {
        val userId = createUser(name = "Alice")

        val firstId = createIssue(title = "First issue", assigneeId = userId)
        val secondId = createIssue(title = "Second issue", assigneeId = userId)

        assertTrue(firstId > 0)
        assertTrue(secondId > firstId)
    }

    @Test
    fun `create returns conflict for duplicate title of same assignee`() {
        val aliceId = createUser(name = "Alice")
        val bobId = createUser(name = "Bob")
        createIssue(title = "First issue", assigneeId = aliceId)

        webTestClient.post().uri(ISSUES_PATH)
            .bodyValue(IssueCreateRequestForm(title = "First issue", assigneeId = aliceId))
            .exchange()
            .expectStatus().isEqualTo(HttpStatus.CONFLICT)

        // the same title can still be used for an issue that is assigned to another user
        createIssue(title = "First issue", assigneeId = bobId)
    }

    @Test
    fun `create returns bad request for unknown assignee`() {
        webTestClient.post().uri(ISSUES_PATH)
            .bodyValue(IssueCreateRequestForm(title = "Unassignable issue", assigneeId = UNKNOWN_ID))
            .exchange()
            .expectStatus().isBadRequest
    }

    @Test
    fun `find issues by assignee returns only issues of that user`() {
        val aliceId = createUser(name = "Alice")
        val bobId = createUser(name = "Bob")
        val firstId = createIssue(title = "First issue", assigneeId = aliceId)
        val secondId = createIssue(title = "Second issue", assigneeId = aliceId)
        createIssue(title = "Third issue", assigneeId = bobId)

        val expected = listOf(
            IssueResponse(id = firstId, title = "First issue", assigneeId = aliceId, assigneeName = "Alice"),
            IssueResponse(id = secondId, title = "Second issue", assigneeId = aliceId, assigneeName = "Alice"),
        )
        assertEquals(expected, findIssues(assigneeId = aliceId))
    }

    @Test
    fun `find issues by assignee returns no content for user without issues`() {
        val userId = createUser(name = "Alice")

        webTestClient.get().uri("$ISSUES_PATH?assigneeId={id}", userId)
            .exchange()
            .expectStatus().isNoContent
    }

    @Test
    fun `delete removes issue or returns not found`() {
        val userId = createUser(name = "Alice")
        val firstId = createIssue(title = "First issue", assigneeId = userId)
        val secondId = createIssue(title = "Second issue", assigneeId = userId)

        webTestClient.delete().uri("$ISSUES_PATH/{id}", firstId)
            .exchange()
            .expectStatus().isNoContent

        assertEquals(listOf(secondId), findIssues(assigneeId = userId).map { it.id })

        webTestClient.delete().uri("$ISSUES_PATH/{id}", firstId)
            .exchange()
            .expectStatus().isNotFound
    }

    @Test
    fun `deleting a user also deletes their issues`() {
        val aliceId = createUser(name = "Alice")
        val bobId = createUser(name = "Bob")
        createIssue(title = "First issue", assigneeId = aliceId)
        val remainingId = createIssue(title = "Second issue", assigneeId = bobId)

        webTestClient.delete().uri("$USERS_PATH/{id}", aliceId)
            .exchange()
            .expectStatus().isNoContent

        webTestClient.get().uri("$ISSUES_PATH?assigneeId={id}", aliceId)
            .exchange()
            .expectStatus().isNoContent

        assertEquals(listOf(remainingId), findIssues(assigneeId = bobId).map { it.id })
    }

    private fun createIssue(title: String, assigneeId: Long): Long {
        return webTestClient.post().uri(ISSUES_PATH)
            .bodyValue(IssueCreateRequestForm(title = title, assigneeId = assigneeId))
            .exchange()
            .expectStatus().isOk
            .expectBody<IssueCreateResponse>()
            .returnResult()
            .responseBody!!
            .id
    }
}
