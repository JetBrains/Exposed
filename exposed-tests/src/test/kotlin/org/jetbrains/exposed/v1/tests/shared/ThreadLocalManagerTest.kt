package org.jetbrains.exposed.v1.tests.shared

import kotlinx.coroutines.test.runTest
import org.jetbrains.exposed.v1.core.dao.id.IntIdTable
import org.jetbrains.exposed.v1.exceptions.ExposedSQLException
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager
import org.jetbrains.exposed.v1.jdbc.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.v1.jdbc.transactions.inTopLevelTransaction
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.transactions.transactionManager
import org.jetbrains.exposed.v1.tests.DatabaseTestsBase
import org.jetbrains.exposed.v1.tests.TestDB
import org.jetbrains.exposed.v1.tests.shared.dml.DMLTestsData
import org.junit.Assume
import org.junit.Test
import kotlin.concurrent.thread
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertNotEquals
import kotlin.test.fail

class ThreadLocalManagerTest : DatabaseTestsBase() {
    @Test
    fun testReconnection() {
        Assume.assumeTrue(TestDB.MYSQL_V5 in TestDB.enabledDialects())

        var secondThreadTm: TransactionManager? = null
        val db1 = TestDB.MYSQL_V5.connect()
        lateinit var db2: Database

        transaction {
            val firstThreadTm = db1.transactionManager
            SchemaUtils.create(DMLTestsData.Cities)
            thread {
                db2 = TestDB.MYSQL_V5.connect()
                transaction {
                    DMLTestsData.Cities.selectAll().toList()
                    secondThreadTm = db2.transactionManager
                    assertNotEquals(firstThreadTm, secondThreadTm)
                }
            }.join()
            assertEquals(firstThreadTm, db1.transactionManager)
            SchemaUtils.drop(DMLTestsData.Cities)
        }
        assertEquals(secondThreadTm, db2.transactionManager)
    }

    @Test
    fun testReadOnly() {
        withTables(excludeSettings = READ_ONLY_EXCLUDED_VENDORS, RollbackTable) {
            assertFails {
                inTopLevelTransaction(db.transactionManager.defaultIsolationLevel, true) {
                    maxAttempts = 1
                    RollbackTable.insert { it[value] = "random-something" }
                }
            }.message?.run { assertTrue(contains("read-only")) } ?: fail("message should not be null")
        }
    }

    @Test
    fun testSuspendedReadOnly() = runTest {
        Assume.assumeFalse(dialect in READ_ONLY_EXCLUDED_VENDORS)

        val database = dialect.connect()
        newSuspendedTransaction(db = database, readOnly = true) {
            expectException<ExposedSQLException> {
                SchemaUtils.create(RollbackTable)
            }
        }

        transaction(db = database) {
            SchemaUtils.create(RollbackTable)
        }

        newSuspendedTransaction(db = database, readOnly = true) {
            expectException<ExposedSQLException> {
                RollbackTable.insert { it[value] = "random-something" }
            }
        }

        transaction(db = database) {
            SchemaUtils.drop(RollbackTable)
        }
    }
}

object RollbackTable : IntIdTable("rollbackTable") {
    val value = varchar("value", 20)
}

// Explanation: MariaDB driver never set readonly to true, MSSQL silently ignores the call, SQLite does not
// promise anything, H2 has very limited functionality
private val READ_ONLY_EXCLUDED_VENDORS =
    TestDB.ALL_H2 + TestDB.ALL_MARIADB + listOf(TestDB.SQLITE, TestDB.SQLSERVER, TestDB.ORACLE)
