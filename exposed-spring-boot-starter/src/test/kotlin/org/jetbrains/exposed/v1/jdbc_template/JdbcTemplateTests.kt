@file:Suppress("PackageName", "InvalidPackageDeclaration")

package org.jetbrains.exposed.v1.`jdbc-template`

import org.jetbrains.exposed.v1.sql.SchemaUtils
import org.jetbrains.exposed.v1.sql.transactions.transaction
import org.junit.jupiter.api.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.event.annotation.BeforeTestClass

@SpringBootApplication
open class JdbcTemplateApplication

@SpringBootTest(
    classes = [JdbcTemplateApplication::class],
    properties = ["spring.datasource.url=jdbc:h2:mem:test", "spring.datasource.driver-class-name=org.h2.Driver", "spring.exposed.generate-ddl=true"]
)
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class JdbcTemplateTests {

    @BeforeTestClass
    fun beforeTests() {
        transaction {
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
