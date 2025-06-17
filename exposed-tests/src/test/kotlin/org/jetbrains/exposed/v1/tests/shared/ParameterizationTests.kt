package org.jetbrains.exposed.v1.tests.shared

import org.jetbrains.exposed.v1.core.BooleanColumnType
import org.jetbrains.exposed.v1.core.IntegerColumnType
import org.jetbrains.exposed.v1.core.InternalApi
import org.jetbrains.exposed.v1.core.StdOutSqlLogger
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.VarCharColumnType
import org.jetbrains.exposed.v1.core.statements.StatementType
import org.jetbrains.exposed.v1.core.vendors.inProperCase
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.addLogger
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.tests.DatabaseTestsBase
import org.jetbrains.exposed.v1.tests.TestDB
import org.junit.Assume
import org.junit.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ParameterizationTests : DatabaseTestsBase() {
    object TempTable : Table("tmp") {
        val name = varchar("foo", 50).nullable()
    }

    private val supportMultipleStatements by lazy {
        TestDB.ALL_MARIADB + TestDB.SQLSERVER + TestDB.ALL_MYSQL + TestDB.POSTGRESQL
    }

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
        Assume.assumeTrue(supportMultipleStatements.containsAll(TestDB.enabledDialects()))

        val dialect = TestDB.enabledDialects().first()
        val db = Database.connect(
            dialect.connection.invoke().plus(urlExtra(dialect)),
            dialect.driver,
            dialect.user,
            dialect.pass
        )

        transaction(db) {
            try {
                SchemaUtils.create(TempTable)

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
                ) { resultSet ->
                    resultSet.next()
                    resultSet.getInt(1)
                }
                assertNotNull(result)
                assertEquals(1, result)

                assertEquals("Anya", TempTable.selectAll().single()[TempTable.name])
            } finally {
                SchemaUtils.drop(TempTable)
            }
        }

        TransactionManager.closeAndUnregister(db)
    }

    @OptIn(InternalApi::class)
    @Test
    fun testMultipleParametersWithMultipleStatements() {
        Assume.assumeTrue(supportMultipleStatements.containsAll(TestDB.enabledDialects()))

        val tester = object : Table("tester") {
            val name = varchar("foo", 50)
            val age = integer("age")
            val active = bool("active")
        }

        val dialect = TestDB.enabledDialects().first()
        val db = Database.connect(
            dialect.connection.invoke().plus(urlExtra(dialect)),
            dialect.driver,
            dialect.user,
            dialect.pass
        )

        transaction(db) {
            try {
                SchemaUtils.create(tester)

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
                ) { resultSet ->
                    resultSet.next()
                    resultSet.getInt(1)
                }
                assertNotNull(result)
                assertEquals(1, result)

                assertEquals(2, tester.selectAll().count())
            } finally {
                SchemaUtils.drop(tester)
            }
        }

        TransactionManager.closeAndUnregister(db)
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

    private fun urlExtra(testDB: TestDB): String {
        return when (testDB) {
            in TestDB.ALL_MYSQL -> "&allowMultiQueries=true"
            in TestDB.ALL_MARIADB -> "?&allowMultiQueries=true"
            else -> ""
        }
    }
}
