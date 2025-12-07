package org.jetbrains.exposed.v1.spring.boot.r2dbc

import org.jetbrains.exposed.v1.r2dbc.R2dbcDatabase
import org.jetbrains.exposed.v1.spring.boot.r2dbc.tables.TestTable
import org.jetbrains.exposed.v1.spring.boot.r2dbc.tables.ignore.IgnoreTable
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.ApplicationContext

@SpringBootTest(
    classes = [Application::class],
    properties = [
        "spring.autoconfigure.exclude=org.jetbrains.exposed.v1.spring.boot.r2dbc.autoconfigure.ExposedAutoConfiguration"
    ]
)
open class DatabaseInitializerTest {

    @Autowired
    private lateinit var applicationContext: ApplicationContext

    @Test
    fun `should ignore excluded package table`() {
        R2dbcDatabase.connect("r2dbc:h2:mem:///test-spring", user = "sa", driver = "h2")
        val tables = discoverExposedTables(
            applicationContext,
            listOf("org.jetbrains.exposed.v1.spring.boot.r2dbc.tables.ignore")
        )
        Assertions.assertEquals(TestTable, tables.single())
    }

    @Test
    fun `ignore non object Table`() {
        R2dbcDatabase.connect("r2dbc:h2:mem:///test-spring", user = "sa", driver = "h2")
        val tables = discoverExposedTables(applicationContext, listOf())
        Assertions.assertEquals(2, tables.size)
        assert(TestTable in tables && IgnoreTable in tables)
    }
}
