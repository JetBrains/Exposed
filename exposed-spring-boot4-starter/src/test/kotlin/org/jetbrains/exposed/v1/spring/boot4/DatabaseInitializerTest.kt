package org.jetbrains.exposed.v1.spring.boot4

import org.jetbrains.exposed.v1.exceptions.ExposedSQLException
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.spring.boot4.tables.TestTable
import org.jetbrains.exposed.v1.spring.boot4.tables.ignore.IgnoreTable
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.DefaultApplicationArguments
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.ApplicationContext

@SpringBootTest(
    classes = [Application::class],
    properties = ["spring.autoconfigure.exclude=org.jetbrains.exposed.v1.spring.boot4.autoconfigure.ExposedAutoConfiguration"]
)
open class DatabaseInitializerTest {

    @Autowired
    private lateinit var applicationContext: ApplicationContext

    @Test
    fun `should create schema for TestTable and not for IgnoreTable`() {
        Assertions.assertThrows(ExposedSQLException::class.java) {
            Database.connect("jdbc:h2:mem:test-spring", user = "sa", driver = "org.h2.Driver")
            transaction {
                val noArgs = DefaultApplicationArguments()
                DatabaseInitializer(
                    applicationContext,
                    listOf("org.jetbrains.exposed.v1.spring.boot4.tables.ignore")
                )
                    .run(noArgs)
                Assertions.assertEquals(0L, TestTable.selectAll().count())
                IgnoreTable.selectAll().count()
            }
        }
    }

    @Test
    fun `ignore non object Table`() {
        Database.connect("jdbc:h2:mem:test-spring", user = "sa", driver = "org.h2.Driver")
        val tables = discoverExposedTables(applicationContext, listOf())
        Assertions.assertEquals(2, tables.size)
        // assertArrayEquals checks for order equality, which seems flaky?
        assert(TestTable in tables && IgnoreTable in tables)
    }
}
