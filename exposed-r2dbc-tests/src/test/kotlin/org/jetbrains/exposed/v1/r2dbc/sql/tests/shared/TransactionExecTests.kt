package org.jetbrains.exposed.v1.r2dbc.sql.tests.shared

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.single
import kotlinx.coroutines.flow.singleOrNull
import kotlinx.coroutines.flow.toList
import org.jetbrains.exposed.v1.core.InternalApi
import org.jetbrains.exposed.v1.core.SqlExpressionBuilder.eq
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.autoIncColumnType
import org.jetbrains.exposed.v1.core.statements.StatementType
import org.jetbrains.exposed.v1.core.statements.buildStatement
import org.jetbrains.exposed.v1.core.upperCase
import org.jetbrains.exposed.v1.core.vendors.inProperCase
import org.jetbrains.exposed.v1.r2dbc.R2dbcTransaction
import org.jetbrains.exposed.v1.r2dbc.batchInsert
import org.jetbrains.exposed.v1.r2dbc.insert
import org.jetbrains.exposed.v1.r2dbc.select
import org.jetbrains.exposed.v1.r2dbc.selectAll
import org.jetbrains.exposed.v1.r2dbc.sql.tests.shared.dml.withCitiesAndUsers
import org.jetbrains.exposed.v1.r2dbc.statements.api.R2dbcResult
import org.jetbrains.exposed.v1.r2dbc.statements.api.origin
import org.jetbrains.exposed.v1.r2dbc.statements.toExecutable
import org.jetbrains.exposed.v1.r2dbc.tests.R2dbcDatabaseTestsBase
import org.jetbrains.exposed.v1.r2dbc.tests.TestDB
import org.jetbrains.exposed.v1.r2dbc.tests.getInt
import org.jetbrains.exposed.v1.r2dbc.tests.getString
import org.jetbrains.exposed.v1.r2dbc.tests.shared.assertEqualLists
import org.jetbrains.exposed.v1.r2dbc.tests.shared.assertEquals
import org.junit.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class TransactionExecTests : R2dbcDatabaseTestsBase() {
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
            ) { row ->
                row.getInt("amount")
            }?.toList()
            assertNotNull(results)
            assertEqualLists(amounts, results)
        }
    }

    @Test
    fun testExecWithMultiStatementQuery() {
        // MySQL only allows this with allowMultiQueries option, which is not supported: https://github.com/asyncer-io/r2dbc-mysql/issues/291
        val toExclude = TestDB.ALL_H2_V2 + TestDB.ALL_MYSQL_LIKE

        withTables(excludeSettings = toExclude, ExecTable) { testDb ->
            testInsertAndSelectInSingleExec(testDb)
        }
    }

    // r2dbc-mysql does NOT support allowMultiQueries option: https://github.com/asyncer-io/r2dbc-mysql/issues/291
//    @Test
//    fun testExecWithMultiStatementQueryUsingMySQL() = runTest {
//        Assume.assumeTrue(TestDB.ALL_MYSQL_MARIADB.containsAll(TestDB.enabledDialects()))
//
//        val dialect = TestDB.enabledDialects().first()
//        val extra = if (dialect in TestDB.ALL_MARIADB) "?" else ""
//        val db = R2dbcDatabase.connect(
//            dialect.connection.invoke().plus("$extra&allowMultiQueries=true")
//        )
//
//        suspendTransaction(db = db) {
//            try {
//                SchemaUtils.create(ExecTable)
//
//                testInsertAndSelectInSingleExec(dialect)
//            } finally {
//                SchemaUtils.drop(ExecTable)
//            }
//        }
//
//        TransactionManager.closeAndUnregister(db)
//    }

    @OptIn(InternalApi::class)
    private suspend fun R2dbcTransaction.testInsertAndSelectInSingleExec(testDb: TestDB) {
        ExecTable.insert {
            it[amount] = 99
        }

        val insertStatement = "INSERT INTO ${ExecTable.tableName.inProperCase()} " +
            "(${ExecTable.amount.name.inProperCase()}, ${ExecTable.id.name.inProperCase()}) " +
            "VALUES (100, ${ExecTable.id.autoIncColumnType?.nextValExpression})"

        val columnAlias = "last_inserted_id"
        val selectLastIdStatement = when (testDb) {
            // OG r2dbc-mssql queried current_value, but this throws sql_variant column not supported: https://github.com/r2dbc/r2dbc-mssql/issues/67
            TestDB.SQLSERVER -> "SELECT COUNT(*) AS $columnAlias FROM ${ExecTable.tableName.inProperCase()}"
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
        ) { row ->
            row.getInt(columnAlias)
        }?.single()
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

            val stringResult = exec("""SELECT $title FROM $table WHERE $id = 1""") { row ->
                row.getString(title)
            }?.singleOrNull()
            assertNotNull(stringResult)
            assertEquals("Exposed", stringResult)

            // no record exists for id = 999, but flow returns single nullable value due to subquery alias
            val dualExtra = if (testDb == TestDB.ORACLE) " FROM DUAL" else ""
            val nullColumnResult = exec("""SELECT (SELECT $title FROM $table WHERE $id = 999) AS sub$dualExtra""") { row ->
                row.getString("sub")
            }?.singleOrNull()
            assertNull(nullColumnResult)

            // no record exists for id = 999, so flow is empty
            val nullTransformResult = exec("""SELECT $title FROM $table WHERE $id = 999""") { row ->
                row.getString(title)
            }?.singleOrNull()
            assertNull(nullTransformResult)
        }
    }

    @Test
    fun testExecWithBuildStatement() {
        withCitiesAndUsers { cities, users, userData ->
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

    @Test
    fun testExecWithQueryInstance() {
        withTables(ExecTable) {
            val selectQuery = ExecTable.select(ExecTable.amount).where { ExecTable.amount less 100 }

            val amounts = (90..99).toList()
            ExecTable.batchInsert(amounts, shouldReturnGeneratedValues = false) { amount ->
                this[ExecTable.id] = (amount % 10 + 1)
                this[ExecTable.amount] = amount
            }

            val expectedSum = amounts.sum()

            // using broader exec(BlockingExecutable) with cast to R2dbcResult
            val result1 = exec(selectQuery) {
                it as R2dbcResult
                it.mapRows { row ->
                    row.getObject(1, java.lang.Integer::class.java)?.toInt()
                }
                    .toList()
                    .sumOf { num -> num ?: 0 }
            }
            assertEquals(expectedSum, result1)

            // using typed exec(AbstractQuery) that exposes R2dbcResult directly
            val result2 = execQuery(selectQuery) {
                it.mapRows { row ->
                    row.origin.get(0, java.lang.Integer::class.java)?.toInt()
                }
                    .toList()
                    .sumOf { num -> num ?: 0 }
            }
            assertEquals(expectedSum, result2)
        }
    }
}
