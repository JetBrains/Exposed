package org.jetbrains.exposed.v1.r2dbc.sql.tests.shared.ddl

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.single
import org.jetbrains.exposed.v1.core.FieldSet
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.QueryBuilder
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.statements.StatementType
import org.jetbrains.exposed.v1.core.stringLiteral
import org.jetbrains.exposed.v1.r2dbc.Query
import org.jetbrains.exposed.v1.r2dbc.insert
import org.jetbrains.exposed.v1.r2dbc.selectAll
import org.jetbrains.exposed.v1.r2dbc.statements.api.R2dbcResult
import org.jetbrains.exposed.v1.r2dbc.statements.api.origin
import org.jetbrains.exposed.v1.r2dbc.tests.R2dbcDatabaseTestsBase
import org.jetbrains.exposed.v1.r2dbc.tests.TestDB
import org.jetbrains.exposed.v1.r2dbc.tests.distinct
import org.jetbrains.exposed.v1.r2dbc.tests.getBoolean
import org.jetbrains.exposed.v1.r2dbc.tests.getInt
import org.jetbrains.exposed.v1.r2dbc.tests.getString
import org.jetbrains.exposed.v1.r2dbc.tests.shared.assertEquals
import org.jetbrains.exposed.v1.r2dbc.tests.shared.assertFalse
import org.jetbrains.exposed.v1.r2dbc.tests.shared.expectException
import org.junit.Test
import kotlin.test.assertContains
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ColumnDefinitionTests : R2dbcDatabaseTestsBase() {
    @Test
    fun testColumnComment() {
        val comment = "Amount of testers"
        val tester = object : Table("tester") {
            val amount = integer("amount").withDefinition("COMMENT", stringLiteral(comment))
        }

        val columnCommentSupportedDB = TestDB.ALL_H2 + TestDB.ALL_MYSQL_MARIADB

        withTables(excludeSettings = TestDB.ALL - columnCommentSupportedDB, tester) { testDb ->
            assertTrue { org.jetbrains.exposed.v1.r2dbc.SchemaUtils.statementsRequiredToActualizeScheme(tester).isEmpty() }

            tester.insert { it[amount] = 9 }
            assertEquals(9, tester.selectAll().single()[tester.amount])

            val tableName = tester.nameInDatabaseCase()
            val showStatement = when (testDb) {
                in TestDB.ALL_MYSQL_MARIADB -> "SHOW FULL COLUMNS FROM $tableName"
                else -> "SELECT REMARKS FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = '$tableName'"
            }
            val resultLabel = when (testDb) {
                in TestDB.ALL_MYSQL_MARIADB -> "Comment"
                else -> "REMARKS"
            }

            val result = exec(showStatement) { row ->
                row.getString(resultLabel)
            }?.first()
            assertNotNull(result)
            assertContains(result, comment)
        }
    }

    @Test
    fun testColumnDataMasking() {
        val tester = object : Table("tester") {
            val email = varchar("email", 128).uniqueIndex().withDefinition("MASKED WITH (FUNCTION = 'email()')")
        }

        withTables(TestDB.ALL - TestDB.SQLSERVER, tester) {
            assertTrue { org.jetbrains.exposed.v1.r2dbc.SchemaUtils.statementsRequiredToActualizeScheme(tester).isEmpty() }

            val testEmail = "mysecretemail123@gmail.com"
            tester.insert {
                it[email] = testEmail
            }

            // create a new user with limited permissions
            exec("CREATE USER MaskingTestUser WITHOUT LOGIN;")
            exec("GRANT SELECT ON ${tester.nameInDatabaseCase()} TO MaskingTestUser;")
            exec("EXECUTE AS USER = 'MaskingTestUser';", explicitStatementType = StatementType.OTHER)

            // Email function obfuscates data of all length to form 'aXXX@XXXX.com', where 'a' is original first letter
            val maskedEmail = "${testEmail.first()}XXX@XXXX.com"
            val maskedResult = tester.selectAll().single()[tester.email]
            assertEquals(maskedEmail, maskedResult)

            exec("REVERT;")
            exec("DROP USER MaskingTestUser;")
        }
    }

    @Test
    fun testColumnDefaultOnNull() {
        val itemA = "Item A"

        withDb(TestDB.ALL_H2_V2 + TestDB.ORACLE) { testDb ->
            val tester = object : Table("tester") {
                val amount = integer("amount")
                val item = varchar("item", 32).apply {
                    if (testDb == TestDB.ORACLE) {
                        withDefinition("DEFAULT ON NULL", stringLiteral(itemA))
                    } else {
                        default(itemA).withDefinition("DEFAULT ON NULL")
                    }
                }
            }
            org.jetbrains.exposed.v1.r2dbc.SchemaUtils.create(tester)

            if (testDb != TestDB.ORACLE) {
                // Oracle would not work with this use case as special DEFAULT syntax is not registered & causes mismatch
                assertTrue { org.jetbrains.exposed.v1.r2dbc.SchemaUtils.statementsRequiredToActualizeScheme(tester).isEmpty() }
            }

            tester.insert {
                it[amount] = 111
            }

            exec(
                """
                    INSERT INTO ${tester.nameInDatabaseCase()}
                    (${tester.amount.nameInDatabaseCase()}, ${tester.item.nameInDatabaseCase()})
                    VALUES (222, null)
                """.trimIndent()
            )

            val result1 = tester.selectAll().map { it[tester.item] }.distinct().single()
            assertEquals(itemA, result1)

            // when Docker image is updated to Oracle23+, this can be removed to test update as well
            if (testDb != TestDB.ORACLE) {
                tester.insert {
                    it[amount] = 333
                    it[item] = "Item B"
                }

                exec(
                    """
                        UPDATE ${tester.nameInDatabaseCase()}
                        SET ${tester.amount.nameInDatabaseCase()} = 999,
                        ${tester.item.nameInDatabaseCase()} = null
                    """.trimIndent()
                )

                val (singleAmount, singleItem) = tester.selectAll().map {
                    it[tester.amount] to it[tester.item]
                }.distinct().single()
                assertEquals(999, singleAmount)
                assertEquals(itemA, singleItem)
            }

            org.jetbrains.exposed.v1.r2dbc.SchemaUtils.drop(tester)
        }
    }

    @Test
    fun testColumnVisibility() {
        val tester = object : Table("tester") {
            val amount = integer("amount")
            val active = bool("active").nullable().withDefinition("INVISIBLE")
        }

        // this Query uses SELECT * FROM instead of the usual SELECT column_1, column_2, ... FROM
        class ImplicitQuery(set: FieldSet, where: Op<Boolean>?) : Query(set, where) {
            override fun prepareSQL(builder: QueryBuilder): String {
                return super.prepareSQL(builder).replaceBefore(" FROM ", "SELECT *")
            }
        }

        fun FieldSet.selectImplicitAll(): Query = ImplicitQuery(this, null)

        val invisibilitySupportedDB = TestDB.ALL_H2 + TestDB.ALL_MARIADB + TestDB.MYSQL_V8 + TestDB.ORACLE

        withTables(excludeSettings = TestDB.ALL - invisibilitySupportedDB, tester) { testDb ->
            if (testDb == TestDB.MYSQL_V8 || testDb == TestDB.ORACLE) {
                // H2 metadata query does not return invisible column info
                // Bug in MariaDB with nullable column - metadata default value returns as NULL - EXPOSED-415
                assertTrue { org.jetbrains.exposed.v1.r2dbc.SchemaUtils.statementsRequiredToActualizeScheme(tester).isEmpty() }
            }

            tester.insert {
                it[amount] = 999
            }

            // an invisible column is only returned in Row if explicitly named
            val result1 = (
                tester
                    .selectAll()
                    .where { tester.amount greater 100 }
                    .execute(this) as R2dbcResult
                )
                .mapRows { row ->
                    row.origin.getInt(tester.amount.name) to row.origin.getBoolean(tester.active.name)
                }.single()
            assertNotNull(result1)
            assertEquals(999, result1.first)
            assertFalse(result1.second)

            val result2 = (
                tester
                    .selectImplicitAll()
                    .where { tester.amount greater 100 }
                    .execute(this) as R2dbcResult
                )
                .mapRows { row ->
                    val amount = row.origin.getInt(tester.amount.name)
                    expectException<NoSuchElementException> {
                        row.origin.getBoolean(tester.active.name)
                    }
                    amount to null
                }.single()
            assertNotNull(result2)
            assertEquals(999, result2.first)
            assertNull(result2.second)
        }
    }
}
