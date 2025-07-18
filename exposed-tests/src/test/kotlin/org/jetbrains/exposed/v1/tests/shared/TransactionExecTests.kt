package org.jetbrains.exposed.v1.tests.shared

import org.jetbrains.exposed.v1.core.InternalApi
import org.jetbrains.exposed.v1.core.SqlExpressionBuilder.eq
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.autoIncColumnType
import org.jetbrains.exposed.v1.core.statements.StatementType
import org.jetbrains.exposed.v1.core.statements.buildStatement
import org.jetbrains.exposed.v1.core.upperCase
import org.jetbrains.exposed.v1.core.vendors.inProperCase
import org.jetbrains.exposed.v1.jdbc.*
import org.jetbrains.exposed.v1.jdbc.statements.toExecutable
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.tests.DatabaseTestsBase
import org.jetbrains.exposed.v1.tests.TestDB
import org.jetbrains.exposed.v1.tests.shared.dml.withCitiesAndUsers
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

    @OptIn(InternalApi::class)
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

    @OptIn(InternalApi::class)
    private fun JdbcTransaction.testInsertAndSelectInSingleExec(testDb: TestDB) {
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

    @OptIn(InternalApi::class)
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

    @Test
    fun testExecWithBuildStatement() {
        withCitiesAndUsers(
            exclude = TestDB.ALL_H2_V1
        ) { cities, users, userData ->
            val initialCityCount = cities.selectAll().count()
            val initialUserDataCount = userData.selectAll().count()

            val newCity = "Amsterdam"
            val insertCity = buildStatement {
                cities.insert {
                    it[name] = newCity
                }
            }
            val upsertCity = buildStatement {
                cities.upsert(onUpdate = { it[cities.name] = cities.name.upperCase() }) {
                    it[id] = initialCityCount.toInt() + 1
                    it[name] = newCity
                }
            }
            val newName = "Alexey"
            val userFilter = users.id eq "alex"
            val updateUser = buildStatement {
                users.update({ userFilter }) {
                    it[users.name] = newName
                }
            }
            val deleteAllUserData = buildStatement { userData.deleteAll() }

            insertCity.toExecutable().execute(this)
            assertEquals(initialCityCount + 1, cities.selectAll().count())

            exec(upsertCity.toExecutable())
            assertEquals(initialCityCount + 1, cities.selectAll().count())
            val updatedCity = cities.selectAll().where { cities.id eq (initialCityCount.toInt() + 1) }.single()
            assertEquals(newCity.uppercase(), updatedCity[cities.name])

            updateUser.toExecutable().execute(this)
            val updatedUserName = users.select(users.name).where { userFilter }.first()
            assertEquals(newName, updatedUserName[users.name])

            val rowsDeleted = exec(deleteAllUserData.toExecutable())
            assertEquals(initialUserDataCount, rowsDeleted?.toLong())
            assertEquals(0, userData.selectAll().count())
        }
    }
}
