package org.jetbrains.exposed.sql.tests.shared

import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.tests.DatabaseTestsBase
import org.jetbrains.exposed.sql.tests.TestDB
import org.jetbrains.exposed.sql.tests.shared.dml.DMLTestsData
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.transactions.inTopLevelTransaction
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.transactions.transactionManager
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
        Assume.assumeTrue(TestDB.MYSQL in TestDB.enabledDialects())

        var secondThreadTm: TransactionManager? = null
        val db1 = TestDB.MYSQL.connect()
        lateinit var db2: Database

        transaction {
            val firstThreadTm = db1.transactionManager
            SchemaUtils.create(DMLTestsData.Cities)
            thread {
                db2 = TestDB.MYSQL.connect()
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
        // Explanation: MariaDB driver never set readonly to true, MSSQL silently ignores the call, SQLLite does not
        // promise anything, H2 has very limited functionality
        val excludeSettings = TestDB.allH2TestDB + listOf(
            TestDB.MARIADB, TestDB.SQLITE,
            TestDB.SQLSERVER, TestDB.ORACLE
        )
        withTables(excludeSettings = excludeSettings, RollbackTable) {
            assertFails {
                inTopLevelTransaction(db.transactionManager.defaultIsolationLevel, true) {
                    repetitionAttempts = 1
                    RollbackTable.insert { it[value] = "random-something" }
                }
            }.message?.run { assertTrue(contains("read-only")) } ?: fail("message should not be null")
        }
    }
}

object RollbackTable : IntIdTable("rollbackTable") {
    val value = varchar("value", 20)
}
