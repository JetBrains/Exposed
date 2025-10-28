package org.jetbrains.exposed.v1.r2dbc.sql.tests.shared

import kotlinx.coroutines.flow.toList
import org.jetbrains.exposed.v1.core.InternalApi
import org.jetbrains.exposed.v1.core.StdOutSqlLogger
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.r2dbc.SchemaUtils
import org.jetbrains.exposed.v1.r2dbc.insert
import org.jetbrains.exposed.v1.r2dbc.selectAll
import org.jetbrains.exposed.v1.r2dbc.tests.R2dbcDatabaseTestsBase
import org.jetbrains.exposed.v1.r2dbc.tests.TestDB
import org.jetbrains.exposed.v1.r2dbc.tests.getInt
import org.jetbrains.exposed.v1.r2dbc.tests.withTransactionContext
import org.jetbrains.exposed.v1.r2dbc.transactions.TransactionManager
import org.junit.Assume
import org.junit.Before
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals

class UserCreatedTransactionsTests : R2dbcDatabaseTestsBase() {

    // Exclude settings are needed just to avoid problems with correctness of column and table names in hardcoded SQL
    private val excludeSettings = TestDB.ALL - TestDB.H2_V2 - TestDB.POSTGRESQL

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
            val tx = TransactionManager.manager.currentOrNew()

            withTransactionContext(tx) {
                SchemaUtils.drop(TestTable)
                SchemaUtils.create(TestTable)
                TestTable.insert { it[param] = 100 }
            }

            val result = tx.exec("select param from txtest") {
                it.getInt(1)
            }?.toList()

            assertEquals(1, result?.size)
            assertEquals(100, result?.get(0))

            tx.close()
        }
    }

    @Test
    fun testAddLogger() {
        withConnection(dialect) { db, testDb ->
            val tx = TransactionManager.manager.currentOrNew()
            // Test that the next line does not throw errors
            tx.addLogger(StdOutSqlLogger)

            tx.close()
        }
    }

    @OptIn(InternalApi::class)
    @Test
    fun testCommit() {
        withConnection(dialect) { db, testDb ->
            val tx = TransactionManager.manager.currentOrNew()

            withTransactionContext(tx) {
                SchemaUtils.drop(TestTable)
                SchemaUtils.create(TestTable)
                TestTable.insert { it[param] = 100 }
            }

            tx.commit()

            val result = tx.exec("select param from txtest") {
                it.getInt(1)
            }?.toList()

            assertEquals(1, result?.size)
            assertEquals(100, result?.get(0))

            tx.close()
        }
    }

    @OptIn(InternalApi::class)
    @Ignore("Rollback actually does not work. The inserted value could be read after rollback.")
    @Test
    fun testRollback() {
        withConnection(dialect) { db, testDb ->
            val tx = TransactionManager.manager.currentOrNew()

            withTransactionContext(tx) {
                SchemaUtils.drop(TestTable)
                SchemaUtils.create(TestTable)
                TestTable.insert { it[param] = 100 }
            }

            tx.rollback()

            val result = tx.exec("select param from txtest") {
                it.getInt(1)
            }?.toList()

            assertEquals(0, result?.size)

            tx.close()
        }
    }

    @OptIn(InternalApi::class)
    @Test
    fun testCloseExecutedStatements() {
        withConnection(dialect) { db, testDb ->
            val tx = TransactionManager.Companion.manager.currentOrNew()

            withTransactionContext(tx) {
                SchemaUtils.drop(TestTable)
                SchemaUtils.create(TestTable)
                TestTable.insert { it[param] = 100 }
            }

            val result = tx.exec("select param from txtest") {
                it.getInt(1)
            }?.toList()

            assertEquals(1, result?.size)
            assertEquals(100, result?.get(0))

            tx.closeExecutedStatements()

            tx.close()
        }
    }

    @OptIn(InternalApi::class)
    @Test
    @Ignore("By unknown reason only one insert from the batch is applied to the database.")
    fun testExecInBatch() {
        withConnection(dialect) { db, testDb ->
            val tx = TransactionManager.Companion.manager.currentOrNew()

            withTransactionContext(tx) {
                SchemaUtils.drop(TestTable)
                SchemaUtils.create(TestTable)
            }

            tx.execInBatch(
                listOf(
                    "insert into txtest (param) values (3)",
                    "insert into txtest (param) values (4)",
                )
            )

            val result = tx.exec("select count(*) from txtest") {
                it.get(0)
            }?.toList()

            assertEquals(1, result?.size)
            assertEquals(2L, result?.get(0))

            tx.close()
        }
    }

    @OptIn(InternalApi::class)
    @Test
    fun testExecQuery() {
        withConnection(dialect) { db, testDb ->
            val tx = TransactionManager.manager.currentOrNew()

            withTransactionContext(tx) {
                SchemaUtils.drop(TestTable)
                SchemaUtils.create(TestTable)
                TestTable.insert { it[param] = 100 }
            }

            val result = tx.execQuery(TestTable.selectAll()) { rs ->
                rs.mapRows {
                    it.getObject(1)
                }
            }?.toList()

            assertEquals(1, result?.size)
            assertEquals(100, result?.get(0))

            tx.close()
        }
    }
}
