@file:Suppress("PackageName", "InvalidPackageDeclaration")

package org.jetbrains.exposed.v1.`database-client`

import kotlinx.coroutines.test.runTest
import org.jetbrains.exposed.v1.r2dbc.SchemaUtils
import org.jetbrains.exposed.v1.r2dbc.transactions.suspendTransaction
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.RepeatedTest
import org.junit.jupiter.api.TestMethodOrder
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.event.annotation.BeforeTestClass

@SpringBootApplication
open class DatabaseClientApplication

// This test class aligns with the JdbcTemplateTests in exposed-spring-boot4-starter
@SpringBootTest(
    classes = [DatabaseClientApplication::class],
    properties = [
        "spring.r2dbc.url=r2dbc:h2:mem:///test;DB_CLOSE_DELAY=-1;",
        "spring.exposed.generate-ddl=true",
        "spring.exposed.database-driver=h2",
    ]
)
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class DatabaseClientTests {

    @BeforeTestClass
    fun beforeTests() = runTest {
        suspendTransaction {
            SchemaUtils.create(AuthorTable, BookTable)
        }
    }

    @Autowired
    lateinit var bookService: BookService

    @Order(1)
    @RepeatedTest(15, name = "Without spring transaction: {currentRepetition}/{totalRepetitions}")
    fun testWithoutSpringTransaction() {
        bookService.testWithoutSpringTransaction()
    }

    @Order(2)
    @RepeatedTest(15, name = "With spring transaction: {currentRepetition}/{totalRepetitions}")
    fun testWithSpringTransaction() {
        bookService.testWithSpringTransaction()
    }

    @Order(3)
    @RepeatedTest(15, name = "With exposed transaction: {currentRepetition}/{totalRepetitions}")
    fun testWithExposedTransaction() {
        bookService.testWithExposedTransaction()
    }

    @Order(4)
    @RepeatedTest(15, name = "With spring and exposed transactions: {currentRepetition}/{totalRepetitions}")
    fun testWithSpringAndExposedTransactions() {
        bookService.testWithSpringAndExposedTransactions()
    }
}
