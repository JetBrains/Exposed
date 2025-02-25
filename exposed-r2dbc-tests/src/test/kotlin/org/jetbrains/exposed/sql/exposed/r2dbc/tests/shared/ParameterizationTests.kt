package org.jetbrains.exposed.sql.exposed.r2dbc.tests.shared

import kotlinx.coroutines.flow.single
import kotlinx.coroutines.test.runTest
import org.jetbrains.exposed.r2dbc.sql.selectAll
import org.jetbrains.exposed.sql.StdOutSqlLogger
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.VarCharColumnType
import org.jetbrains.exposed.sql.addLogger
import org.jetbrains.exposed.sql.tests.R2dbcDatabaseTestsBase
import org.jetbrains.exposed.sql.tests.shared.assertEquals
import org.junit.Test
import kotlin.test.assertNull

class ParameterizationTests : R2dbcDatabaseTestsBase() {
    object TempTable : Table("tmp") {
        val name = varchar("foo", 50).nullable()
    }

    @Test
    fun testInsertWithQuotesAndGetItBack() = runTest {
        withTables(TempTable) {
            exec(
                "INSERT INTO ${TempTable.tableName} (foo) VALUES (?)",
                listOf(VarCharColumnType() to "John \"Johny\" Johnson")
            )

            assertEquals("John \"Johny\" Johnson", TempTable.selectAll().single()[TempTable.name])
        }
    }

    @Test
    fun testNullParameterWithLogger() = runTest {
        withTables(TempTable) {
            // the logger is left in to test that it does not throw IllegalStateException with null parameter arg
            addLogger(StdOutSqlLogger)

            exec(
                stmt = "INSERT INTO ${TempTable.tableName} (${TempTable.name.name}) VALUES (?)",
                args = listOf(VarCharColumnType() to null)
            )

            assertNull(TempTable.selectAll().single()[TempTable.name])
        }
    }
}
