package org.jetbrains.exposed.v1.r2dbc.sql.tests.shared

import io.r2dbc.spi.IsolationLevel
import io.r2dbc.spi.Option
import io.r2dbc.spi.TransactionDefinition
import kotlinx.coroutines.flow.singleOrNull
import kotlinx.coroutines.test.runTest
import org.jetbrains.exposed.v1.r2dbc.R2dbcDatabase
import org.jetbrains.exposed.v1.r2dbc.R2dbcTransaction
import org.jetbrains.exposed.v1.r2dbc.tests.R2dbcDatabaseTestsBase
import org.jetbrains.exposed.v1.r2dbc.tests.TestDB
import org.jetbrains.exposed.v1.r2dbc.tests.getInt
import org.jetbrains.exposed.v1.r2dbc.tests.getString
import org.jetbrains.exposed.v1.r2dbc.tests.shared.assertEquals
import org.jetbrains.exposed.v1.r2dbc.transactions.inTopLevelSuspendTransaction
import org.jetbrains.exposed.v1.r2dbc.transactions.suspendTransaction
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.Test
import kotlin.test.assertNotNull

class TransactionIsolationTest : R2dbcDatabaseTestsBase() {
    private val transactionIsolationSupportDb = TestDB.ALL_MARIADB + TestDB.MYSQL_V5 + TestDB.POSTGRESQL + TestDB.SQLSERVER

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
        override fun <T : Any?> getAttribute(option: Option<T?>): T? {
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

    private suspend fun R2dbcTransaction.assertTransactionIsolationLevel(testDb: TestDB, expected: IsolationLevel) {
        val (sql, repeatable, committed) = when (testDb) {
            TestDB.POSTGRESQL -> Triple("SHOW TRANSACTION ISOLATION LEVEL", "repeatable read", "read committed")
            in TestDB.ALL_MYSQL_MARIADB -> Triple("SELECT @@tx_isolation", "REPEATABLE-READ", "READ-COMMITTED")
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
