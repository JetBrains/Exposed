package org.jetbrains.exposed.v1.r2dbc.sql.tests.shared

import kotlinx.coroutines.test.runTest
import org.jetbrains.exposed.v1.core.dao.id.IntIdTable
import org.jetbrains.exposed.v1.r2dbc.ExposedR2dbcException
import org.jetbrains.exposed.v1.r2dbc.SchemaUtils
import org.jetbrains.exposed.v1.r2dbc.insert
import org.jetbrains.exposed.v1.r2dbc.tests.R2dbcDatabaseTestsBase
import org.jetbrains.exposed.v1.r2dbc.tests.TestDB
import org.jetbrains.exposed.v1.r2dbc.tests.shared.expectException
import org.jetbrains.exposed.v1.r2dbc.transactions.suspendTransaction
import org.junit.Assume
import org.junit.Ignore
import org.junit.Test

// equivalent to exposed-tests/ThreadLocalManagerTest.kt
class ReadOnlyTests : R2dbcDatabaseTestsBase() {
    // Explanation: MariaDB driver never set readonly to true;
    // MSSQL silently ignores the call;
    // H2 has very limited functionality
    private val READ_ONLY_EXCLUDED_VENDORS =
        TestDB.ALL_H2_V2 + TestDB.ALL_MARIADB + listOf(TestDB.SQLSERVER, TestDB.ORACLE)

    object RollbackTable : IntIdTable("rollbackTable") {
        val value = varchar("value", 20)
    }

    // TODO - bug
    @Ignore
    @Test
    fun testReadOnly() = runTest {
        Assume.assumeFalse(dialect in READ_ONLY_EXCLUDED_VENDORS)

        val database = dialect.connect()
        suspendTransaction(db = database, readOnly = true) {
            expectException<ExposedR2dbcException> {
                SchemaUtils.create(RollbackTable)
            }
        }

        suspendTransaction(db = database) {
            SchemaUtils.create(RollbackTable)
        }

        suspendTransaction(db = database, readOnly = true) {
            expectException<ExposedR2dbcException> {
                RollbackTable.insert { it[RollbackTable.value] = "random-something" }
            }
        }

        suspendTransaction(db = database) {
            SchemaUtils.drop(RollbackTable)
        }
    }
}
