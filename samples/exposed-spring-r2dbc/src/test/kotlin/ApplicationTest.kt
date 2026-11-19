@file:Suppress("InvalidPackageDeclaration")

package org.jetbrains.exposed.samples.spring

import org.jetbrains.exposed.samples.spring.controller.IssueController.IssueResponse
import org.jetbrains.exposed.samples.spring.controller.UserController.UserCreateRequestForm
import org.jetbrains.exposed.samples.spring.controller.UserController.UserCreateResponse
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.test.web.reactive.server.WebTestClient
import org.springframework.test.web.reactive.server.expectBody
import org.springframework.test.web.reactive.server.expectBodyList
import org.testcontainers.postgresql.PostgreSQLContainer

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@Import(ApplicationTest.TestcontainersConfiguration::class)
internal abstract class ApplicationTest {

    @Autowired
    protected lateinit var webTestClient: WebTestClient

    class TestcontainersConfiguration {
        // @ServiceConnection makes the application connect to this database, instead of the url in application.yml
        @Bean
        @ServiceConnection
        fun postgres(): PostgreSQLContainer = PostgreSQLContainer("postgres:18")
    }

    protected fun createUser(name: String, age: Int = 30, issueTitles: List<String>? = null): Long {
        return webTestClient.post().uri(USERS_PATH)
            .bodyValue(UserCreateRequestForm(name = name, age = age, issueTitles = issueTitles))
            .exchange()
            .expectStatus().isOk
            .expectBody<UserCreateResponse>()
            .returnResult()
            .responseBody!!
            .id
    }

    protected fun findIssues(assigneeId: Long): List<IssueResponse> {
        return webTestClient.get().uri("$ISSUES_PATH?assigneeId={id}", assigneeId)
            .exchange()
            .expectStatus().isOk
            .expectBodyList<IssueResponse>()
            .returnResult()
            .responseBody
            .orEmpty()
    }

    companion object {
        const val USERS_PATH = "/api/v1/users"
        const val ISSUES_PATH = "/api/v1/issues"
        const val UNKNOWN_ID = Long.MAX_VALUE
    }
}
