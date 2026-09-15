package org.jetbrains.exposed.v1.r2dbc.sql.tests.shared

import io.r2dbc.spi.IsolationLevel
import io.r2dbc.spi.Option
import io.r2dbc.spi.TransactionDefinition
import kotlinx.coroutines.flow.single
import kotlinx.coroutines.flow.singleOrNull
import kotlinx.coroutines.test.runTest
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.r2dbc.R2dbcDatabase
import org.jetbrains.exposed.v1.r2dbc.R2dbcTransaction
import org.jetbrains.exposed.v1.r2dbc.SchemaUtils
import org.jetbrains.exposed.v1.r2dbc.insert
import org.jetbrains.exposed.v1.r2dbc.selectAll
import org.jetbrains.exposed.v1.r2dbc.tests.R2dbcDatabaseTestsBase
import org.jetbrains.exposed.v1.r2dbc.tests.TestDB
import org.jetbrains.exposed.v1.r2dbc.tests.getInt
import org.jetbrains.exposed.v1.r2dbc.tests.getString
import org.jetbrains.exposed.v1.r2dbc.tests.shared.assertEquals
import org.jetbrains.exposed.v1.r2dbc.transactions.TransactionManager
import org.jetbrains.exposed.v1.r2dbc.transactions.inTopLevelSuspendTransaction
import org.jetbrains.exposed.v1.r2dbc.transactions.suspendTransaction
import org.jetbrains.exposed.v1.r2dbc.update
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.Test
import kotlin.test.assertNotNull

class TransactionIsolationTest : R2dbcDatabaseTestsBase() {
    private val transactionIsolationSupportDb = TestDB.ALL_MYSQL_MARIADB + TestDB.POSTGRESQL + TestDB.SQLSERVER

    @Test
    fun testWhatTransactionIsolationWasApplied() {
        withDb {
            inTopLevelSuspendTransaction(transactionIsolation = IsolationLevel.SERIALIZABLE) {
                maxAttempts = 1
                assertEquals(IsolationLevel.SERIALIZABLE, this.connection().getTransactionIsolation())
            }
        }
    }

    @Test
    fun testTransactionIsolationSetOnDatabaseConfig() = runTest {
        Assumptions.assumeTrue(transactionIsolationSupportDb.containsAll(TestDB.enabledDialects()))

        val db = dialect.connect { defaultR2dbcIsolationLevel = IsolationLevel.READ_COMMITTED }

        suspendTransaction(db = db) {
            // transaction manager should default to use DatabaseConfig level
            assertEquals(IsolationLevel.READ_COMMITTED, transactionManager.defaultIsolationLevel)

            // database level should be set by DatabaseConfig
            assertTransactionIsolationLevel(dialect, IsolationLevel.READ_COMMITTED)
        }

        suspendTransaction(transactionIsolation = IsolationLevel.REPEATABLE_READ, db = db) {
            // transaction manager should default to use DatabaseConfig level
            assertEquals(IsolationLevel.READ_COMMITTED, transactionManager.defaultIsolationLevel)

            // database level should be set by transaction-specific setting
            assertTransactionIsolationLevel(dialect, IsolationLevel.REPEATABLE_READ)
        }
    }

    @Test
    fun testTransactionIsolationSetOnTransaction() {
        withDb(excludeSettings = TestDB.ALL - transactionIsolationSupportDb) {
            inTopLevelSuspendTransaction {
                // transaction manager should use database default since no level is configured
                assertEquals(R2dbcDatabase.getDefaultIsolationLevel(db), transactionManager.defaultIsolationLevel)
            }

            inTopLevelSuspendTransaction(transactionIsolation = IsolationLevel.READ_COMMITTED, db = db) {
                // transaction manager should use database default since no level is configured
                assertEquals(R2dbcDatabase.getDefaultIsolationLevel(db), transactionManager.defaultIsolationLevel)

                // database level should be set by transaction-specific setting
                assertTransactionIsolationLevel(dialect, IsolationLevel.READ_COMMITTED)
            }
        }
    }

    private class CustomTestTransactionDefinition : TransactionDefinition {
        override fun <T> getAttribute(option: Option<T?>): T? {
            return when (option) {
                TransactionDefinition.ISOLATION_LEVEL -> IsolationLevel.REPEATABLE_READ as T
                else -> null
            }
        }
    }

    @Test
    fun testTransactionIsolationSetByManualDefinition() = runTest {
        Assumptions.assumeTrue(transactionIsolationSupportDb.containsAll(TestDB.enabledDialects()))

        val db = dialect.connect { defaultR2dbcIsolationLevel = IsolationLevel.READ_COMMITTED }

        suspendTransaction(db = db) {
            // transaction manager should default to use DatabaseConfig level
            assertEquals(IsolationLevel.READ_COMMITTED, transactionManager.defaultIsolationLevel)

            this.connection().setTransactionDefinition(CustomTestTransactionDefinition())

            // database level should be set by the value in CustomTestTransactionDefinition
            assertTransactionIsolationLevel(dialect, IsolationLevel.REPEATABLE_READ)
        }

        suspendTransaction(db = db) {
            // overrides any Exposed parameter setting & forces beginTransaction() to be called with no definition
            this.connection().setTransactionDefinition(null)

            // database level should be set by the database's own defaults
            assertTransactionIsolationLevel(dialect, R2dbcDatabase.getDefaultIsolationLevel(db))
        }
    }

    @Test
    fun testMySqlTransactionIsolationDoesNotChangeSessionDefault() {
        runTest {
            Assumptions.assumeTrue(dialect in TestDB.ALL_MYSQL_MARIADB)

            val db = dialect.connect { defaultMaxAttempts = 1 }
            val sql = if (dialect == TestDB.MYSQL_V8) {
                "SELECT @@session.transaction_isolation"
            } else {
                "SELECT @@session.tx_isolation"
            }
            try {
                val sessionDefault = suspendTransaction(db = db) {
                    connection().setTransactionDefinition(null)
                    exec(sql) { it.getString(1) }?.singleOrNull()
                }
                assertNotNull(sessionDefault)
                val isolation = if (sessionDefault == "READ-COMMITTED") {
                    IsolationLevel.REPEATABLE_READ
                } else {
                    IsolationLevel.READ_COMMITTED
                }

                suspendTransaction(db = db, transactionIsolation = isolation) {
                    assertEquals(isolation, connection().getTransactionIsolation())
                    assertEquals(sessionDefault, exec(sql) { it.getString(1) }?.singleOrNull())
                }
            } finally {
                TransactionManager.closeAndUnregister(db)
            }
        }
    }

    private suspend fun R2dbcTransaction.assertOnTwoConnections(testDb: TestDB, expected: IsolationLevel) {
        val tester = object : Table("transaction_isolation_tester") {
            val amount = integer("amount")
        }
        val expectedAmount = when (expected) {
            IsolationLevel.REPEATABLE_READ -> 0
            IsolationLevel.READ_COMMITTED -> 1
            else -> throw UnsupportedOperationException("Isolation level $expected not supported by this check")
        }
        val writer = testDb.connect { defaultMaxAttempts = 1 }
        try {
            inTopLevelSuspendTransaction(db = writer) {
                SchemaUtils.drop(tester)
                SchemaUtils.create(tester)
                tester.insert { it[amount] = 0 }
            }
            assertEquals(0, tester.selectAll().single()[tester.amount])
            // The writer commits on a separate connection before this transaction reads again.
            suspendTransaction(db = writer) { tester.update { it[amount] = 1 } }
            assertEquals(expectedAmount, tester.selectAll().single()[tester.amount])
        } finally {
            try {
                SchemaUtils.drop(tester)
            } finally {
                TransactionManager.closeAndUnregister(writer)
            }
        }
    }

    private suspend fun R2dbcTransaction.assertTransactionIsolationLevel(testDb: TestDB, expected: IsolationLevel) {
        if (testDb in TestDB.ALL_MYSQL_MARIADB) {
            // Session variables do not report transaction-specific isolation, so check its effect on reads.
            assertOnTwoConnections(testDb, expected)
            return
        }
        val (sql, repeatable, committed) = when (testDb) {
            TestDB.POSTGRESQL -> Triple("SHOW TRANSACTION ISOLATION LEVEL", "repeatable read", "read committed")
            TestDB.SQLSERVER -> Triple("SELECT transaction_isolation_level FROM sys.dm_exec_sessions WHERE session_id = @@SPID", "3", "2")
            else -> throw UnsupportedOperationException("Cannot query isolation level using ${testDb.name}")
        }
        val expectedLevel = when (expected) {
            IsolationLevel.READ_COMMITTED -> committed
            IsolationLevel.REPEATABLE_READ -> repeatable
            else -> throw UnsupportedOperationException("Isolation level $expected not supported by all testDB")
        }

        val actual = exec("$sql;") {
            if (testDb == TestDB.SQLSERVER) it.getInt(1).toString() else it.getString(1)
        }?.singleOrNull()
        assertNotNull(actual)
        assertEquals(expectedLevel, actual)
    }
}
