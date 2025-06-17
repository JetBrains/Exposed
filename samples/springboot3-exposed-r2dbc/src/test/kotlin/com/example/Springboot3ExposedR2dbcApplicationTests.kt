package com.example

import com.example.bean.User
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.context.TestConstructor
import org.springframework.test.web.reactive.server.WebTestClient
import org.springframework.test.web.reactive.server.expectBody
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.DurationUnit

@SpringBootTest
@AutoConfigureWebTestClient
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
internal final class Springboot3ExposedR2dbcApplicationTests(
    private val webTestClient: WebTestClient,
){
    private final val logger: Logger = LoggerFactory.getLogger(this.javaClass)
    @Test
    internal final fun contextLoads() = runTest {
        webTestClient.get()
            .uri("/hello/hello")
            .exchange()
            .expectStatus().isOk
            .expectBody<String>()
            .isEqualTo("Hello World!")
    }


    @Test
    internal final fun `find all`() = runTest(timeout = 5.seconds) {
        webTestClient.get()
            .uri("/user/findAll")
            .exchange()
            .expectStatus().isOk
            .expectBody<List<User>>()
            .consumeWith { response ->
                val users: List<User>? = response.responseBody
                assert(!users.isNullOrEmpty())
                logger.info("Found users: $users")
            }
    }

    @Test
    internal final fun `add user`() = runTest(timeout = 5.seconds) {
        val testUser = User(
            id = 0,
            account = "hello",
            password = "world",
            nickname = "test",
        )
        webTestClient.post()
            .uri("/user/insert")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(testUser)
            .exchange()
            .expectStatus().isOk
            .expectBody<Boolean>()
            .isEqualTo(true)
    }

    @Test
    internal final fun `update user`() = runTest {
        val testUser = User(
            id = 1,
            account = "hello1",
            password = "world1",
            nickname = "test1",
        )
        webTestClient.put()
            .uri("/user/update")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(testUser)
            .exchange()
            .expectStatus().isOk
            .expectBody<Boolean>()
            .isEqualTo(true)
    }

    @Test
    internal final fun `find user`() = runTest {
        // When/Then
        webTestClient.get()
            .uri("/user/findUserById?id=1")
            .exchange()
            .expectStatus().isOk
            .expectBody<User>()
            .consumeWith { response ->
                val user: User? = response.responseBody
                assert(user != null)
                logger.info("Found user: $user")
            }
    }


    @Test
    internal final fun `delete user`() = runTest {
        webTestClient.get()
            .uri("/user/deleteById?id=1")
            .exchange()
            .expectStatus().isOk
            .expectBody<Boolean>()
            .isEqualTo(true)
    }

}
