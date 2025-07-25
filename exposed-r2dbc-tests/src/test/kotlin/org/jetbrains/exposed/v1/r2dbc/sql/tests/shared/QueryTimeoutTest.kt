package org.jetbrains.exposed.v1.r2dbc.sql.tests.shared

import io.r2dbc.postgresql.api.PostgresqlException
import io.r2dbc.spi.R2dbcTimeoutException
import nl.altindag.log.LogCaptor
import org.jetbrains.exposed.v1.core.exposedLogger
import org.jetbrains.exposed.v1.r2dbc.ExposedR2dbcException
import org.jetbrains.exposed.v1.r2dbc.tests.R2dbcDatabaseTestsBase
import org.jetbrains.exposed.v1.r2dbc.tests.TestDB
import org.jetbrains.exposed.v1.r2dbc.tests.shared.assertTrue
import org.jetbrains.exposed.v1.r2dbc.transactions.TransactionManager
import org.junit.Assert.fail
import org.junit.Test

class QueryTimeoutTest : R2dbcDatabaseTestsBase() {

    private fun generateTimeoutStatements(db: TestDB, timeout: Int): String {
        return when (db) {
            in TestDB.ALL_MYSQL_MARIADB -> "SELECT SLEEP($timeout) = 0;"
            in TestDB.ALL_POSTGRES -> "SELECT pg_sleep($timeout);"
            TestDB.SQLSERVER -> "WAITFOR DELAY '00:00:$timeout';"
            else -> throw NotImplementedError()
        }
    }

    // Unlike JDBC, MYSQL excluded as timeout not applied, even though Connection.setStatementTimeout() should support V8+
    // https://github.com/asyncer-io/r2dbc-mysql/blob/trunk/r2dbc-mysql/src/main/java/io/asyncer/r2dbc/mysql/MySqlSimpleConnection.java
    // Low-level connection also fails if either setStatementTimeout() or "SET SESSION MAX_EXECUTION_TIME" is used.
    private val timeoutTestDBList = TestDB.ALL_MARIADB + TestDB.ALL_POSTGRES + TestDB.SQLSERVER

    @Test
    fun timeoutStatements() {
        withDb(timeoutTestDBList) { testDB ->
            this.queryTimeout = 3
            try {
                TransactionManager.current().exec(
                    generateTimeoutStatements(testDB, 5)
                )
                fail("Should have thrown a timeout or cancelled statement exception")
            } catch (cause: ExposedR2dbcException) {
                when (testDB) {
                    // PostgreSQL throws a regular PostgresqlException with a cancelled statement message
                    TestDB.POSTGRESQL -> assertTrue(cause.cause is PostgresqlException)
                    else -> assertTrue(cause.cause is R2dbcTimeoutException)
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
    }
}
