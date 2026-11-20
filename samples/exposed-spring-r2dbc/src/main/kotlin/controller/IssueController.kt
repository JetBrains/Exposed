@file:Suppress("InvalidPackageDeclaration")

package org.jetbrains.exposed.samples.spring.controller

import org.jetbrains.exposed.samples.spring.domain.IssueId
import org.jetbrains.exposed.samples.spring.domain.UserId
import org.jetbrains.exposed.samples.spring.service.IssueCreateRequest
import org.jetbrains.exposed.samples.spring.service.IssueService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/issues")
internal class IssueController(
    private val issueService: IssueService,
) {
    // Read All Issues assigned to a User
    @GetMapping
    suspend fun findIssuesByAssignee(
        @RequestParam assigneeId: Long
    ): ResponseEntity<List<IssueResponse>> {
        val issues = issueService.findIssuesByAssignee(UserId(assigneeId))
        val response = issues.map { issue ->
            IssueResponse(
                id = issue.id.value,
                title = issue.title,
                assigneeId = issue.assignee.id.value,
                assigneeName = issue.assignee.name,
            )
        }

        return if (response.isNotEmpty()) {
            ResponseEntity.ok(response)
        } else {
            ResponseEntity.noContent().build()
        }
    }

    data class IssueResponse(
        val id: Long,
        val title: String,
        val assigneeId: Long,
        val assigneeName: String,
    )

    // Create Issue
    @PostMapping
    suspend fun create(
        @RequestBody form: IssueCreateRequestForm
    ): ResponseEntity<IssueCreateResponse> {
        val issueId = issueService.create(
            IssueCreateRequest(
                title = form.title,
                assigneeId = UserId(form.assigneeId),
            )
        )

        return if (issueId != null) {
            ResponseEntity.ok(
                IssueCreateResponse(
                    id = issueId.value,
                )
            )
        } else {
            // the requested assignee does not exist
            ResponseEntity.badRequest().build()
        }
    }

    data class IssueCreateRequestForm(
        val title: String,
        val assigneeId: Long,
    )

    data class IssueCreateResponse(val id: Long)

    // Delete Issue
    @DeleteMapping("/{id}")
    suspend fun delete(
        @PathVariable id: Long
    ): ResponseEntity<Unit> {
        val deleted = issueService.delete(IssueId(id))

        return if (deleted) {
            ResponseEntity.noContent().build()
        } else {
            ResponseEntity.notFound().build()
        }
    }
}
