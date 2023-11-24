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

class TransactionExecTests : DatabaseTestsBase() {
    object ExecTable : Table("exec_table") {
        val id = integer("id").autoIncrement(idSeqName = "exec_id_seq")
        val amount = integer("amount")
        override val primaryKey = PrimaryKey(id)
    }

    @Test
    fun testExecWithSingleStatementQuery() {
        withTables(ExecTable) {
            val amounts = (90..99).toList()
            ExecTable.batchInsert(amounts, shouldReturnGeneratedValues = false) { amount ->
                this[ExecTable.id] = (amount % 10 + 1)
                this[ExecTable.amount] = amount
            }

            val results = exec(
                """SELECT * FROM ${ExecTable.tableName.inProperCase()}""",
                explicitStatementType = StatementType.SELECT
            ) { resultSet ->
                val allAmounts = mutableListOf<Int>()
                while (resultSet.next()) {
                    allAmounts.add(resultSet.getInt("amount"))
                }
                allAmounts
            }
            assertNotNull(results)
            assertEqualLists(amounts, results)
        }
    }

    @Test
    fun testExecWithMultiStatementQuery() {
        // PGjdbc-NG driver does not allow multiple commands in a single prepared statement.
        // Both SQLite and H2 drivers allow multiple but only return the result of the first statement:
        // SQLite issue tracker: https://github.com/xerial/sqlite-jdbc/issues/277
        // H2 issue tracker: https://github.com/h2database/h2database/issues/3704
        val toExclude = TestDB.allH2TestDB + listOf(TestDB.SQLITE, TestDB.MARIADB, TestDB.MYSQL, TestDB.POSTGRESQLNG)

        withTables(excludeSettings = toExclude, ExecTable) { testDb ->
            testInsertAndSelectInSingleExec(testDb)
        }
    }

    @Test
    fun testExecWithMultiStatementQueryUsingMySQL() {
        Assume.assumeTrue(setOf(TestDB.MYSQL, TestDB.MARIADB).containsAll(TestDB.enabledDialects()))

        val dialect = TestDB.enabledDialects().first()
        val extra = if (dialect == TestDB.MARIADB) "?" else ""
        val db = Database.connect(
            dialect.connection.invoke().plus("$extra&allowMultiQueries=true"),
            dialect.driver,
            dialect.user,
            dialect.pass
        )

        transaction(db) {
            try {
                SchemaUtils.create(ExecTable)

                testInsertAndSelectInSingleExec(dialect)
            } finally {
                SchemaUtils.drop(ExecTable)
            }
        }

        TransactionManager.closeAndUnregister(db)
    }

    private fun Transaction.testInsertAndSelectInSingleExec(testDb: TestDB) {
        ExecTable.insert {
            it[amount] = 99
        }

        val insertStatement = "INSERT INTO ${ExecTable.tableName.inProperCase()} " +
            "(${ExecTable.amount.name.inProperCase()}, ${ExecTable.id.name.inProperCase()}) " +
            "VALUES (100, ${ExecTable.id.autoIncColumnType?.nextValExpression})"

        val columnAlias = "last_inserted_id"
        val selectLastIdStatement = when (testDb) {
            TestDB.SQLSERVER -> "SELECT current_value AS $columnAlias FROM sys.sequences"
            TestDB.ORACLE -> "SELECT ${ExecTable.id.autoIncColumnType?.autoincSeq}.CURRVAL AS $columnAlias FROM DUAL"
            TestDB.POSTGRESQL -> "SELECT lastval() AS $columnAlias"
            else -> "SELECT LAST_INSERT_ID() AS $columnAlias"
        }

        val insertAndSelectStatements = if (testDb == TestDB.ORACLE) {
            """
                DECLARE
                    rc sys_refcursor;
                BEGIN
                    EXECUTE IMMEDIATE '$insertStatement';
                    OPEN rc FOR $selectLastIdStatement;
                    dbms_sql.return_result(rc);
                END;
            """
        } else {
            """
                $insertStatement;
                $selectLastIdStatement;
            """
        }

        val result = exec(
            insertAndSelectStatements.trimIndent(),
            explicitStatementType = StatementType.MULTI
        ) { resultSet ->
            resultSet.next()
            resultSet.getInt(columnAlias)
        }
        assertNotNull(result)
        assertEquals(2, result)
    }
}
