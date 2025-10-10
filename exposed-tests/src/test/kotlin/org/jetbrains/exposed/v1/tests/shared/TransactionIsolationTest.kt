package org.jetbrains.exposed.v1.tests.shared

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.jetbrains.exposed.v1.core.DatabaseConfig
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager
import org.jetbrains.exposed.v1.jdbc.transactions.inTopLevelTransaction
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.tests.DatabaseTestsBase
import org.jetbrains.exposed.v1.tests.TestDB
import org.junit.Assume
import org.junit.Test
import java.sql.Connection
import kotlin.test.assertNotNull

class TransactionIsolationTest : DatabaseTestsBase() {
    private val transactionIsolationSupportDb = TestDB.ALL_MARIADB + TestDB.MYSQL_V5 + TestDB.POSTGRESQL + TestDB.SQLSERVER

    @Test
    fun testWhatTransactionIsolationWasApplied() {
        withDb {
            inTopLevelTransaction(transactionIsolation = Connection.TRANSACTION_SERIALIZABLE) {
                maxAttempts = 1
                assertEquals(Connection.TRANSACTION_SERIALIZABLE, this.connection.transactionIsolation)
            }
        }
    }

    @Test
    fun testTransactionIsolationWithHikariDataSource() {
        Assume.assumeTrue(transactionIsolationSupportDb.containsAll(TestDB.enabledDialects()))
        val dialect = TestDB.enabledDialects().first()

        val db = Database.connect(
            HikariDataSource(setupHikariConfig(dialect, "TRANSACTION_REPEATABLE_READ"))
        )
        val manager = TransactionManager.managerFor(db)

        transaction(db) {
            // transaction manager should use database default since no level is provided other than hikari
            assertEquals(Database.getDefaultIsolationLevel(db), manager?.defaultIsolationLevel)

            // database level should be set by hikari dataSource
            assertTransactionIsolationLevel(dialect, Connection.TRANSACTION_REPEATABLE_READ)
            // after first connection, transaction manager should use hikari level by default
            assertEquals(Connection.TRANSACTION_REPEATABLE_READ, manager?.defaultIsolationLevel)
        }

        transaction(transactionIsolation = Connection.TRANSACTION_READ_COMMITTED, db = db) {
            assertEquals(Connection.TRANSACTION_REPEATABLE_READ, manager?.defaultIsolationLevel)

            // database level should be set by transaction-specific setting
            assertTransactionIsolationLevel(dialect, Connection.TRANSACTION_READ_COMMITTED)
        }

        transaction(db) {
            assertEquals(Connection.TRANSACTION_REPEATABLE_READ, manager?.defaultIsolationLevel)

            // database level should be set by hikari dataSource
            assertTransactionIsolationLevel(dialect, Connection.TRANSACTION_REPEATABLE_READ)
        }

        TransactionManager.closeAndUnregister(db)
    }

    @Test
    fun testTransactionIsolationWithHikariAndDatabaseConfig() {
        Assume.assumeTrue(transactionIsolationSupportDb.containsAll(TestDB.enabledDialects()))
        val dialect = TestDB.enabledDialects().first()

        val db = Database.connect(
            HikariDataSource(setupHikariConfig(dialect, "TRANSACTION_REPEATABLE_READ")),
            databaseConfig = DatabaseConfig { defaultIsolationLevel = Connection.TRANSACTION_READ_COMMITTED }
        )
        val manager = TransactionManager.managerFor(db)

        transaction(db) {
            // transaction manager should default to use DatabaseConfig level
            assertEquals(Connection.TRANSACTION_READ_COMMITTED, manager?.defaultIsolationLevel)

            // database level should be set by DatabaseConfig
            assertTransactionIsolationLevel(dialect, Connection.TRANSACTION_READ_COMMITTED)
            // after first connection, transaction manager should retain DatabaseConfig level
            assertEquals(Connection.TRANSACTION_READ_COMMITTED, manager?.defaultIsolationLevel)
        }

        transaction(transactionIsolation = Connection.TRANSACTION_REPEATABLE_READ, db = db) {
            assertEquals(Connection.TRANSACTION_READ_COMMITTED, manager?.defaultIsolationLevel)

            // database level should be set by transaction-specific setting
            assertTransactionIsolationLevel(dialect, Connection.TRANSACTION_REPEATABLE_READ)
        }

        transaction(db) {
            assertEquals(Connection.TRANSACTION_READ_COMMITTED, manager?.defaultIsolationLevel)

            // database level should be set by DatabaseConfig
            assertTransactionIsolationLevel(dialect, Connection.TRANSACTION_READ_COMMITTED)
        }

        TransactionManager.closeAndUnregister(db)
    }

    private fun setupHikariConfig(dialect: TestDB, isolation: String): HikariConfig {
        return HikariConfig().apply {
            jdbcUrl = dialect.connection.invoke()
            driverClassName = dialect.driver
            username = dialect.user
            password = dialect.pass
            maximumPoolSize = 6
            isAutoCommit = false
            transactionIsolation = isolation
            validate()
        }
    }

    private fun JdbcTransaction.assertTransactionIsolationLevel(testDb: TestDB, expected: Int) {
        val (sql, repeatable, committed) = when (testDb) {
            TestDB.POSTGRESQL -> Triple("SHOW TRANSACTION ISOLATION LEVEL", "repeatable read", "read committed")
            in TestDB.ALL_MYSQL_MARIADB -> Triple("SELECT @@tx_isolation", "REPEATABLE-READ", "READ-COMMITTED")
            TestDB.SQLSERVER -> Triple("SELECT transaction_isolation_level FROM sys.dm_exec_sessions WHERE session_id = @@SPID", "3", "2")
            else -> throw UnsupportedOperationException("Cannot query isolation level using ${testDb.name}")
        }
        val expectedLevel = when (expected) {
            Connection.TRANSACTION_READ_COMMITTED -> committed
            Connection.TRANSACTION_REPEATABLE_READ -> repeatable
            else -> throw UnsupportedOperationException("Isolation level $expected not supported by all testDB")
        }

        val actual = exec("$sql;") {
            it.next()
            it.getString(1)
        }
        assertNotNull(actual)
        assertEquals(expectedLevel, actual)
    }
}
