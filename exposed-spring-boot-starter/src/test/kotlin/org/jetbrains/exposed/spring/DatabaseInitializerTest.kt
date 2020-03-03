package org.jetbrains.exposed.spring


import org.jetbrains.exposed.exceptions.ExposedSQLException
import org.jetbrains.exposed.spring.tables.TestTable
import org.jetbrains.exposed.spring.tables.ignore.IgnoreTable
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.ApplicationContext
import org.springframework.test.context.junit4.SpringRunner

@RunWith(SpringRunner::class)
@SpringBootTest(classes = [org.jetbrains.exposed.spring.Application::class],
        properties = ["spring.autoconfigure.exclude=org.jetbrains.exposed.spring.autoconfigure.ExposedAutoConfiguration"])
open class DatabaseInitializerTest {

    @Autowired
    private lateinit var applicationContext: ApplicationContext

    @Test(expected = ExposedSQLException::class)
    fun `should create schema for TestTable and not for IgnoreTable`() {
        Database.connect("jdbc:h2:mem:test-spring", user = "sa", driver =  "org.h2.Driver")
        transaction {
            DatabaseInitializer(applicationContext, listOf("org.jetbrains.exposed.spring.tables.ignore")).run(null)
            Assert.assertEquals(0L, TestTable.selectAll().count())
            IgnoreTable.selectAll().count()
        }
    }
}