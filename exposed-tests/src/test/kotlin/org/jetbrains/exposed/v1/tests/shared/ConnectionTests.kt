package org.jetbrains.exposed.v1.tests.shared

import org.jetbrains.exposed.v1.core.StdOutSqlLogger
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.dao.id.LongIdTable
import org.jetbrains.exposed.v1.core.vendors.ColumnMetadata
import org.jetbrains.exposed.v1.core.vendors.H2Dialect
import org.jetbrains.exposed.v1.jdbc.name
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.tests.DatabaseTestsBase
import org.jetbrains.exposed.v1.tests.TestDB
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.Test
import java.sql.Types
import kotlin.test.assertContains

class ConnectionTests : DatabaseTestsBase() {

    object People : LongIdTable() {
        val firstName = varchar("firstname", 80).nullable()
        val lastName = varchar("lastname", 42).default("Doe")
        val age = integer("age").default(18)
    }

    @Test
    fun testGettingColumnMetadata() {
        withTables(excludeSettings = TestDB.ALL - TestDB.ALL_H2_V2, People) {
            val columnMetadata = connection.metadata {
                requireNotNull(columns(People)[People])
            }.toSet()

            val h2Dialect = (db.dialect as H2Dialect)
            val idType = "BIGINT"
            val firstNameType = if (h2Dialect.h2Mode == H2Dialect.H2CompatibilityMode.Oracle) "VARCHAR2(80)" else "VARCHAR(80)"
            val lastNameType = if (h2Dialect.h2Mode == H2Dialect.H2CompatibilityMode.Oracle) "VARCHAR2(42)" else "VARCHAR(42)"
            val ageType = if (h2Dialect.h2Mode == H2Dialect.H2CompatibilityMode.Oracle) "INTEGER" else "INT"

            val expected = setOf(
                ColumnMetadata(People.id.nameInDatabaseCase(), Types.BIGINT, idType, false, 64, null, h2Dialect.h2Mode != H2Dialect.H2CompatibilityMode.Oracle, null),
                ColumnMetadata(People.firstName.nameInDatabaseCase(), Types.VARCHAR, firstNameType, true, 80, null, false, null),
                ColumnMetadata(People.lastName.nameInDatabaseCase(), Types.VARCHAR, lastNameType, false, 42, null, false, "Doe"),
                ColumnMetadata(People.age.nameInDatabaseCase(), Types.INTEGER, ageType, false, 32, null, false, "18"),
            )

            assertEquals(expected, columnMetadata)
        }
    }

    @Test
    fun testDatabaseNameParsedFromConnectionUrl() {
        // MSSQL connection url with a named database requires that the database already exists in the server,
        // so TestDB.SQLSERVER uses a url that omits this to connect to the default (master) database.
        // All H2 modes omitted for simplicity because they follow the same pattern as TestDB.H2_V2 but with different names.
        val excludedDb = TestDB.ALL_H2_V2 - TestDB.H2_V2 + TestDB.SQLSERVER

        withDb(excludeSettings = excludedDb) { testDb ->
            val expectedName = when (testDb) {
                TestDB.ORACLE -> "FREEPDB1"
                in TestDB.ALL_POSTGRES -> "postgres"
                TestDB.SQLITE -> "jdbc:sqlite:file:test"
                TestDB.H2_V2 -> "jdbc:h2:mem:regular"
                else -> "testdb"
            }
            val actualName = this.db.name
            assertEquals(expectedName, actualName)

            // Getting attached db names should be possible using SQLite's "PRAGMA database_list",
            // but this returns no name for the main db, assuming this is because an in-memory db is being used.
            if (testDb != TestDB.SQLITE) {
                val queryDatabaseName = when (testDb) {
                    TestDB.ORACLE -> "SELECT SYS_CONTEXT('USERENV','DB_NAME') FROM DUAL"
                    in TestDB.ALL_POSTGRES -> "SELECT current_database()"
                    TestDB.H2_V2 -> "SELECT CURRENT_CATALOG"
                    else -> "SELECT DATABASE()"
                }

                val resultName = exec(queryDatabaseName) {
                    it.next()
                    it.getString(1)
                }
                if (testDb == TestDB.H2_V2) {
                    assertTrue(actualName.substringAfterLast(':').equals(resultName, ignoreCase = true))
                } else {
                    assertTrue(actualName.equals(resultName, ignoreCase = true))
                }
            }
        }
    }

    @Test
    fun testTableConstraintsWithFKColumnsThatNeedQuoting() {
        val parent = object : LongIdTable("parent") {
            val scale = integer("scale").uniqueIndex()
        }
        val child = object : LongIdTable("child") {
            val scale = reference("scale", parent.scale)
        }

        withTables(child, parent) { testDb ->
            val constraints = connection.metadata {
                tableConstraints(listOf(child))
            }
            // tableConstraints() returns entries for all tables involved in the FK (parent + child)
            assertEquals(2, constraints.keys.size)

            // EXPOSED-711 https://youtrack.jetbrains.com/issue/EXPOSED-711/Oracle-tableConstraints-columnContraints-dont-return-foreign-keys
            // but only child entry has a non-empty list of FKs
            if (testDb != TestDB.ORACLE) {
                assertEquals(
                    1,
                    constraints.values.count { fks ->
                        fks.any { it.fkName == child.scale.foreignKey?.fkName }
                    }
                )
            }
        }
    }

    @Test
    fun testAddingLoggerDoesNotCauseNoTransactionInContext() {
        Assumptions.assumeTrue(TestDB.H2_V2 in TestDB.enabledDialects())
        TestDB.H2_V2.connect()

        val tester = object : Table("tester") {
            val amount = integer("amount")
        }

        try {
            transaction {
                // the logger is left in to test that it does not throw 'no transaction in context'
                addLogger(StdOutSqlLogger)
                tester.selectAll().toList()
            }
        } catch (cause: Exception) {
            assertTrue(cause.message != null)
            assertContains(cause.message!!, "Table \"TESTER\" not found")
        }
    }
}
