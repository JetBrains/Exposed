package org.jetbrains.exposed.sql.tests.shared

import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.statements.StatementType
import org.jetbrains.exposed.sql.tests.DatabaseTestsBase
import org.jetbrains.exposed.sql.tests.TestDB
import org.jetbrains.exposed.sql.tests.inProperCase
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.Assume
import org.junit.Test
import kotlin.test.assertNotNull

class ParameterizationTests : DatabaseTestsBase() {
    object TempTable : Table("tmp") {
        val name = varchar("foo", 50)
    }

    private val supportMultipleStatements by lazy {
        setOf(TestDB.MYSQL, TestDB.MARIADB, TestDB.POSTGRESQL, TestDB.SQLSERVER)
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

    @Test
    fun testSingleParametersWithMultipleStatements() {
        Assume.assumeTrue(supportMultipleStatements.containsAll(TestDB.enabledDialects()))

        val dialect = TestDB.enabledDialects().first()
        val urlExtra = when (dialect) {
            TestDB.MYSQL -> "&allowMultiQueries=true"
            TestDB.MARIADB -> "?&allowMultiQueries=true"
            else -> ""
        }
        val db = Database.connect(
            dialect.connection.invoke().plus(urlExtra),
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

    @Test
    fun testMultipleParametersWithMultipleStatements() {
        Assume.assumeTrue(supportMultipleStatements.containsAll(TestDB.enabledDialects()))

        val tester = object : Table("tester") {
            val name = varchar("foo", 50)
            val age = integer("age")
            val active = bool("active")
        }

        val dialect = TestDB.enabledDialects().first()
        val urlExtra = when (dialect) {
            TestDB.MYSQL -> "&allowMultiQueries=true"
            TestDB.MARIADB -> "?&allowMultiQueries=true"
            else -> ""
        }
        val db = Database.connect(
            dialect.connection.invoke().plus(urlExtra),
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
}
