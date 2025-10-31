package org.jetbrains.exposed.v1.tests.shared

import org.jetbrains.exposed.v1.core.InternalApi
import org.jetbrains.exposed.v1.core.StdOutSqlLogger
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.transactions.withThreadLocalTransaction
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager
import org.jetbrains.exposed.v1.tests.DatabaseTestsBase
import org.jetbrains.exposed.v1.tests.TestDB
import org.junit.Assume
import org.junit.Before
import kotlin.collections.minus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class UserCreatedTransactionsTests : DatabaseTestsBase() {

    // Exclude settings are needed just to avoid problems with correctness of column and table names in hardcoded SQL
    private val excludeSettings = TestDB.Companion.ALL - TestDB.H2_V2 - TestDB.POSTGRESQL

    object TestTable : Table("txtest") {
        val param = integer("param")
    }

    @Before
    fun before() {
        if (dialect in excludeSettings) {
            Assume.assumeFalse(true)
            return
        }
    }

    @OptIn(InternalApi::class)
    @Test
    fun testSelectWithExec() {
        withConnection(dialect) { db, testDb ->
            val tx = TransactionManager.currentOrNew()

            withThreadLocalTransaction(tx) {
                SchemaUtils.drop(TestTable)
                SchemaUtils.create(TestTable)
                TestTable.insert { it[param] = 100 }
            }

            tx.exec("select param from txtest") {
                it.next()
                assertEquals(100, it.getInt(1))
            }

            tx.close()
        }
    }

    @Test
    fun testAddLogger() {
        withConnection(dialect) { db, testDb ->
            val tx = TransactionManager.currentOrNew()
            // Test that the next line does not throw errors
            tx.addLogger(StdOutSqlLogger)

            tx.close()
        }
    }

    @OptIn(InternalApi::class)
    @Test
    fun testCommit() {
        withConnection(dialect) { db, testDb ->
            val tx1 = TransactionManager.currentOrNew()

            withThreadLocalTransaction(tx1) {
                SchemaUtils.drop(TestTable)
                SchemaUtils.create(TestTable)
                TestTable.insert { it[param] = 100 }
            }

            tx1.commit()

            val tx2 = TransactionManager.manager.newTransaction()

            tx2.exec("select param from txtest") {
                it.next()
                assertEquals(100, it.getInt(1))
            }

            tx1.close()
            tx2.close()
        }
    }

    @OptIn(InternalApi::class)
    @Test
    fun testRollback() {
        withConnection(dialect) { db, testDb ->
            val tx = TransactionManager.currentOrNew()

            withThreadLocalTransaction(tx) {
                SchemaUtils.drop(TestTable)
                SchemaUtils.create(TestTable)
                TestTable.insert { it[param] = 100 }
            }

            tx.rollback()

            tx.exec("select param from txtest") {
                assertFalse(it.next())
            }

            tx.close()
        }
    }

    @OptIn(InternalApi::class)
    @Test
    fun testCloseExecutedStatements() {
        withConnection(dialect) { db, testDb ->
            val tx = TransactionManager.currentOrNew()

            withThreadLocalTransaction(tx) {
                SchemaUtils.drop(TestTable)
                SchemaUtils.create(TestTable)
                TestTable.insert { it[param] = 100 }
            }

            tx.exec("select param from txtest") {
                it.next()
                assertEquals(100, it.getInt(1))
            }

            tx.closeExecutedStatements()

            tx.close()
        }
    }

    @OptIn(InternalApi::class)
    @Test
    fun testExecInBatch() {
        withConnection(dialect) { db, testDb ->
            val tx = TransactionManager.currentOrNew()

            withThreadLocalTransaction(tx) {
                SchemaUtils.drop(TestTable)
                SchemaUtils.create(TestTable)
            }

            tx.execInBatch(
                listOf(
                    "insert into txtest (param) values (1)",
                    "insert into txtest (param) values (2)",
                )
            )

            tx.exec("select count(*) from txtest") {
                it.next()
                assertEquals(2, it.getInt(1))
            }

            tx.close()
        }
    }

    @OptIn(InternalApi::class)
    @Test
    fun testExecQuery() {
        withConnection(dialect) { db, testDb ->
            val tx = TransactionManager.currentOrNew()

            withThreadLocalTransaction(tx) {
                SchemaUtils.drop(TestTable)
                SchemaUtils.create(TestTable)
                TestTable.insert { it[param] = 100 }
            }

            tx.execQuery(TestTable.selectAll()) { rs ->
                rs.next()
                assertEquals(100, rs.getInt(1))
            }

            tx.close()
        }
    }
}
