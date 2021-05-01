package org.jetbrains.exposed.sql.tests.shared

import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.tests.DatabaseTestsBase
import org.junit.Test

class ParameterizationTests : DatabaseTestsBase() {
    @Test
    fun testInsertWithQuotesAndGetItBack() {
        val table = object : Table("tmp") {
            val name = varchar("foo", 50)
        }

        withTables(table) {
            exec(
                "INSERT INTO ${table.tableName} (foo) VALUES (?)",
                listOf(VarCharColumnType() to "John \"Johny\" Johnson")
            )

            assertEquals("John \"Johny\" Johnson", table.selectAll().single()[table.name])
        }
    }
}
