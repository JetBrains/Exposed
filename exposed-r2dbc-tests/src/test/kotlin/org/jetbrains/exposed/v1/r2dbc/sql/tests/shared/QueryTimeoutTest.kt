package org.jetbrains.exposed.v1.r2dbc.sql.tests.shared

import io.r2dbc.spi.R2dbcNonTransientResourceException
import io.r2dbc.spi.R2dbcTimeoutException
import kotlinx.coroutines.test.runTest
import nl.altindag.log.LogCaptor
import org.jetbrains.exposed.v1.core.exposedLogger
import org.jetbrains.exposed.v1.r2dbc.ExposedR2dbcException
import org.jetbrains.exposed.v1.r2dbc.tests.R2dbcDatabaseTestsBase
import org.jetbrains.exposed.v1.r2dbc.tests.TestDB
import org.jetbrains.exposed.v1.r2dbc.tests.shared.assertTrue
import org.jetbrains.exposed.v1.r2dbc.transactions.TransactionManager
import org.jetbrains.exposed.v1.r2dbc.transactions.suspendTransaction
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.Test

class QueryTimeoutTest : R2dbcDatabaseTestsBase() {

    private fun generateTimeoutStatements(db: TestDB, timeout: Int): String {
        return when (db) {
            in TestDB.ALL_MYSQL_MARIADB -> "SELECT 1 = 0 WHERE SLEEP($timeout);"
            in TestDB.ALL_POSTGRES -> "SELECT pg_sleep($timeout);"
            TestDB.SQLSERVER -> "WAITFOR DELAY '00:00:$timeout';"
            else -> throw NotImplementedError()
        }
    }

    private val timeoutTestDBList = TestDB.ALL_MARIADB + TestDB.ALL_POSTGRES + TestDB.SQLSERVER + TestDB.MYSQL_V8

    @Test
    fun timeoutStatements() = runTest {
        Assumptions.assumeTrue(dialect in timeoutTestDBList)

        if (dialect == TestDB.POSTGRESQL) {
            try {
                suspendTransaction(dialect.connect { defaultMaxAttempts = 1 }) {
                    this.queryTimeout = 3
                    TransactionManager.current().exec(
                        generateTimeoutStatements(dialect, 5)
                    )
                    Assertions.fail("Should have thrown a timeout or cancelled statement exception")
                }
            } catch (cause: ExposedR2dbcException) {
                kotlin.test.assertTrue(cause.cause is R2dbcNonTransientResourceException)
            }
        } else {
            withDb { testDB ->
                this.queryTimeout = 3
                try {
                    TransactionManager.current().exec(
                        generateTimeoutStatements(testDB, 5)
                    )
                    Assertions.fail("Should have thrown a timeout or cancelled statement exception")
                } catch (cause: ExposedR2dbcException) {
                    assertTrue(cause.cause is R2dbcTimeoutException)
                }
            }
        }
    }

    @Test
    fun noTimeoutWithTimeoutStatement() {
        withDb(timeoutTestDBList) {
            this.queryTimeout = 3
            TransactionManager.current().exec(
                generateTimeoutStatements(it, 1)
            )
        }
    }

    @Test
    fun timeoutZeroWithTimeoutStatement() {
        withDb(timeoutTestDBList) {
            this.queryTimeout = 0
            TransactionManager.current().exec(
                generateTimeoutStatements(it, 1)
            )
        }
    }

    @Test
    fun testLongQueryThrowsWarning() {
        val logCaptor = LogCaptor.forName(exposedLogger.name)

        withDb(timeoutTestDBList) { testDB ->
            this.warnLongQueriesDuration = 2

            assertTrue(logCaptor.warnLogs.isEmpty())

            try {
                TransactionManager.current().exec(
                    generateTimeoutStatements(testDB, 4)
                )
            } catch (cause: ExposedR2dbcException) {
                assertTrue(cause.cause is R2dbcTimeoutException)
            }

            assertTrue(logCaptor.warnLogs.single().contains("Long query"))
        }

        logCaptor.clearLogs()
        logCaptor.close()
    }
}
