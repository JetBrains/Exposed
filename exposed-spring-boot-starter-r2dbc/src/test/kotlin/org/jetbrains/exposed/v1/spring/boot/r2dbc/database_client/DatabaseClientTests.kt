@file:Suppress("PackageName", "InvalidPackageDeclaration")

package org.jetbrains.exposed.v1.`database-client`

import kotlinx.coroutines.test.runTest
import org.jetbrains.exposed.v1.core.InternalApi
import org.jetbrains.exposed.v1.core.transactions.ThreadLocalTransactionsStack
import org.jetbrains.exposed.v1.r2dbc.SchemaUtils
import org.jetbrains.exposed.v1.r2dbc.transactions.suspendTransaction
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.RepeatedTest
import org.junit.jupiter.api.TestMethodOrder
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.event.annotation.AfterTestMethod
import org.springframework.test.context.event.annotation.BeforeTestClass
import org.springframework.test.context.event.annotation.BeforeTestMethod

@SpringBootApplication
open class DatabaseClientApplication

@SpringBootTest(
    classes = [DatabaseClientApplication::class],
    properties = [
        "spring.r2dbc.url=r2dbc:h2:mem:///testdb;DB_CLOSE_DELAY=-1;",
        "spring.exposed.generate-ddl=true"
    ]
)
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class DatabaseClientTests {

    @OptIn(InternalApi::class)
    @BeforeTestClass
    fun beforeTests() = runTest {
        suspendTransaction {
            SchemaUtils.create(AuthorTable)
        }
    }

    @OptIn(InternalApi::class)
    @BeforeTestMethod
    fun beforeTest() {
        // TODO - this should not be done, but transactions are not being popped on original thread after coroutine switches thread
        ThreadLocalTransactionsStack.threadTransactions()
            ?.joinToString(separator = "\n", prefix = "\n!!! ORPHAN transactions:\n") { "--> $it" }
            ?.ifEmpty { "NO transactions to clear up :)" }
            ?.also { println(it) }
        ThreadLocalTransactionsStack.threadTransactions()?.clear()
    }

    @OptIn(InternalApi::class)
    @AfterTestMethod
    fun afterTest() {
        // TODO - this should not be done, but transactions are not being popped on original thread after coroutine switches thread
        ThreadLocalTransactionsStack.threadTransactions()
            ?.joinToString(separator = "\n", prefix = "\n!!! ORPHAN transactions:\n") { "--> $it" }
            ?.ifEmpty { "NO transactions to clear up :)" }
            ?.also { println(it) }
        ThreadLocalTransactionsStack.threadTransactions()?.clear()
    }

    @Autowired
    lateinit var authorService: AuthorService

    @Order(1)
    @RepeatedTest(15, name = "Without spring transaction: {currentRepetition}/{totalRepetitions}")
    fun testWithoutSpringTransaction() = runTest {
        authorService.testWithoutSpringTransaction()
    }

    @Order(2)
    @RepeatedTest(15, name = "With spring transaction: {currentRepetition}/{totalRepetitions}")
    fun testWithSpringTransaction() = runTest {
        authorService.testWithSpringTransaction()
    }

    @Order(3)
    @RepeatedTest(15, name = "With exposed transaction: {currentRepetition}/{totalRepetitions}")
    fun testWithExposedTransaction() = runTest {
        authorService.testWithExposedTransaction()
    }

    @Order(4)
    @RepeatedTest(15, name = "With spring and exposed transactions: {currentRepetition}/{totalRepetitions}")
    fun testWithSpringAndExposedTransactions() = runTest {
        authorService.testWithSpringAndExposedTransactions()
    }
}
