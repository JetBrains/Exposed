package org.jetbrains.exposed.v1.r2dbc.sql.tests.shared

import kotlinx.coroutines.flow.single
import org.jetbrains.exposed.v1.core.BooleanColumnType
import org.jetbrains.exposed.v1.core.IntegerColumnType
import org.jetbrains.exposed.v1.core.InternalApi
import org.jetbrains.exposed.v1.core.StdOutSqlLogger
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.VarCharColumnType
import org.jetbrains.exposed.v1.core.statements.StatementType
import org.jetbrains.exposed.v1.core.vendors.inProperCase
import org.jetbrains.exposed.v1.r2dbc.addLogger
import org.jetbrains.exposed.v1.r2dbc.selectAll
import org.jetbrains.exposed.v1.r2dbc.tests.R2dbcDatabaseTestsBase
import org.jetbrains.exposed.v1.r2dbc.tests.TestDB
import org.jetbrains.exposed.v1.r2dbc.tests.shared.assertEquals
import org.junit.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ParameterizationTests : R2dbcDatabaseTestsBase() {
    object TempTable : Table("tmp") {
        val name = varchar("foo", 50).nullable()
    }

    // NOTE: UNSUPPORTED by r2dbc-mysql && r2dbc-postgresql
    // r2dbc-mysql does NOT support allowMultiQueries option: https://github.com/asyncer-io/r2dbc-mysql/issues/291
    // r2dbc-postgresql does NOT seem to support multiple statements either, even though the attached issue is old:
    // https://github.com/pgjdbc/r2dbc-postgresql/issues/82, but the exception being thrown is the same:
    // "... cannot be created. This is often due to the presence of both multiple statements and parameters at the same time."
    private val multipleStatementsNotSupported = TestDB.ALL - TestDB.SQLSERVER

    @Test
    fun testInsertWithQuotesAndGetItBack() {
        withTables(TempTable) {
            exec(
                "INSERT INTO ${TempTable.tableName} (foo) VALUES (?)",
                listOf(VarCharColumnType() to "John \"Johny\" Johnson")
            )

            assertEquals("John \"Johny\" Johnson", TempTable.selectAll().single()[TempTable.name])
        }
    }

    @OptIn(InternalApi::class)
    @Test
    fun testSingleParametersWithMultipleStatements() {
        withTables(excludeSettings = multipleStatementsNotSupported, TempTable) {
            val table = TempTable.tableName.inProperCase()
            val column = TempTable.name.name.inProperCase()

            val result = exec(
                """
                        INSERT INTO $table ($column) VALUES (?);
                        INSERT INTO $table ($column) VALUES (?);
                        INSERT INTO $table ($column) VALUES (?);
                        DELETE FROM $table WHERE $table.$column LIKE ?;
                        SELECT COUNT(*) FROM $table;
                """.trimIndent(),
                args = listOf(
                    VarCharColumnType() to "Anne",
                    VarCharColumnType() to "Anya",
                    VarCharColumnType() to "Anna",
                    VarCharColumnType() to "Ann%",
                ),
                explicitStatementType = StatementType.MULTI
            ) { row ->
                row.get(0)
            }?.single()
            assertNotNull(result)
            assertEquals(1, result)

            assertEquals("Anya", TempTable.selectAll().single()[TempTable.name])
        }
    }

    @OptIn(InternalApi::class)
    @Test
    fun testMultipleParametersWithMultipleStatements() {
        val tester = object : Table("tester") {
            val name = varchar("foo", 50)
            val age = integer("age")
            val active = bool("active")
        }

        withTables(excludeSettings = multipleStatementsNotSupported, tester) {
            val table = tester.tableName.inProperCase()
            val (name, age, active) = tester.columns.map { it.name.inProperCase() }

            val result = exec(
                """
                        INSERT INTO $table ($active, $age, $name) VALUES (?, ?, ?);
                        INSERT INTO $table ($active, $age, $name) VALUES (?, ?, ?);
                        UPDATE $table SET $age=? WHERE ($table.$name LIKE ?) AND ($table.$active = ?);
                        SELECT COUNT(*) FROM $table WHERE ($table.$name LIKE ?) AND ($table.$age = ?);
                """.trimIndent(),
                args = listOf(
                    BooleanColumnType() to true, IntegerColumnType() to 1, VarCharColumnType() to "Anna",
                    BooleanColumnType() to false, IntegerColumnType() to 1, VarCharColumnType() to "Anya",
                    IntegerColumnType() to 2, VarCharColumnType() to "A%", BooleanColumnType() to true,
                    VarCharColumnType() to "A%", IntegerColumnType() to 2
                ),
                explicitStatementType = StatementType.MULTI
            ) { row ->
                row.get(0)
            }?.single()
            assertNotNull(result)
            assertEquals(1, result)

            assertEquals(2, tester.selectAll().count())
        }
    }

    @Test
    fun testNullParameterWithLogger() {
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
