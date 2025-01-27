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
import kotlin.test.assertNull

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
        val toExclude = TestDB.ALL_H2 + TestDB.ALL_MYSQL_LIKE + listOf(TestDB.SQLITE, TestDB.POSTGRESQLNG)

        withTables(excludeSettings = toExclude, ExecTable) { testDb ->
            testInsertAndSelectInSingleExec(testDb)
        }
    }

    @Test
    fun testExecWithMultiStatementQueryUsingMySQL() {
        Assume.assumeTrue(TestDB.ALL_MYSQL_MARIADB.containsAll(TestDB.enabledDialects()))

        val dialect = TestDB.enabledDialects().first()
        val extra = if (dialect in TestDB.ALL_MARIADB) "?" else ""
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
            TestDB.MARIADB -> "SELECT LASTVAL(${ExecTable.id.autoIncColumnType?.autoincSeq}) AS $columnAlias"
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

    @Test
    fun testExecWithNullableAndEmptyResultSets() {
        val tester = object : Table("tester") {
            val id = integer("id")
            val title = varchar("title", 32)
        }

        withTables(tester) { testDb ->
            tester.insert {
                it[id] = 1
                it[title] = "Exposed"
            }

            val (table, id, title) = listOf(tester.tableName, tester.id.name, tester.title.name).map { it.inProperCase() }

            val stringResult = exec("""SELECT $title FROM $table WHERE $id = 1""") { rs ->
                rs.next()
                rs.getString(title)
            }
            assertNotNull(stringResult)
            assertEquals("Exposed", stringResult)

            // no record exists for id = 999, but result set returns single nullable value due to subquery alias
            val dualExtra = if (testDb == TestDB.ORACLE) " FROM DUAL" else ""
            val nullColumnResult = exec("""SELECT (SELECT $title FROM $table WHERE $id = 999) AS sub$dualExtra""") { rs ->
                rs.next()
                rs.getString("sub")
            }
            assertNull(nullColumnResult)

            // no record exists for id = 999, so result set is empty and rs.next() is false
            val nullTransformResult = exec("""SELECT $title FROM $table WHERE $id = 999""") { rs ->
                if (rs.next()) {
                    rs.getString(title)
                } else {
                    null
                }
            }
            assertNull(nullTransformResult)
        }
    }
}
