package org.jetbrains.exposed.spring

import org.jetbrains.exposed.exceptions.ExposedSQLException
import org.jetbrains.exposed.spring.tables.TestTable
import org.jetbrains.exposed.spring.tables.ignore.IgnoreTable
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.ApplicationContext

@SpringBootTest(
    classes = [Application::class],
    properties = ["spring.autoconfigure.exclude=org.jetbrains.exposed.spring.autoconfigure.ExposedAutoConfiguration"]
)
open class DatabaseInitializerTest {

    @Autowired
    private lateinit var applicationContext: ApplicationContext

    @Test
    fun `should create schema for TestTable and not for IgnoreTable`() {
        Assertions.assertThrows(ExposedSQLException::class.java) {
            Database.connect("jdbc:h2:mem:test-spring", user = "sa", driver = "org.h2.Driver")
            transaction {
                DatabaseInitializer(applicationContext, listOf("org.jetbrains.exposed.spring.tables.ignore")).run(null)
                assertEquals(0L, TestTable.selectAll().count())
                IgnoreTable.selectAll().count()
            }
        }
    }

    @Test
    fun `ignore non object Table`() {
        Database.connect("jdbc:h2:mem:test-spring", user = "sa", driver = "org.h2.Driver")
        val tables = discoverExposedTables(applicationContext, listOf())
        assertEquals(2, tables.size)
        // assertArrayEquals checks for order equality, which seems flaky?
        assert(TestTable in tables && IgnoreTable in tables)
    }
}
