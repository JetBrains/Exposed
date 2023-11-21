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
    fun testParametersWithMultipleStatements() {
        val supported = setOf(TestDB.MYSQL, TestDB.MARIADB, TestDB.POSTGRESQL, TestDB.SQLSERVER)
        Assume.assumeTrue(supported.containsAll(TestDB.enabledDialects()))

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
}
