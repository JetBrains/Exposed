package org.jetbrains.exposed.v1.tests.shared

import com.impossibl.postgres.jdbc.PGSQLSimpleException
import com.microsoft.sqlserver.jdbc.SQLServerException
import nl.altindag.log.LogCaptor
import org.jetbrains.exposed.v1.core.exposedLogger
import org.jetbrains.exposed.v1.exceptions.ExposedSQLException
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager
import org.jetbrains.exposed.v1.tests.DatabaseTestsBase
import org.jetbrains.exposed.v1.tests.INCOMPLETE_R2DBC_TEST
import org.jetbrains.exposed.v1.tests.NOT_APPLICABLE_TO_R2DBC
import org.jetbrains.exposed.v1.tests.TestDB
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.postgresql.util.PSQLException
import java.sql.SQLException
import java.sql.SQLSyntaxErrorException
import java.sql.SQLTimeoutException

/**
 * @author ivan@daangn.com
 */
class QueryTimeoutTest : DatabaseTestsBase() {

    private fun generateTimeoutStatements(db: TestDB, timeout: Int): String {
        return when (db) {
            in TestDB.ALL_MYSQL_MARIADB -> "SELECT SLEEP($timeout) = 0;"
            in TestDB.ALL_POSTGRES -> "SELECT pg_sleep($timeout);"
            TestDB.SQLSERVER -> "WAITFOR DELAY '00:00:$timeout';"
            else -> throw NotImplementedError()
        }
    }

    // MySql V5 is excluded now due to error: "java.lang.NoClassDefFoundError: com/mysql/cj/jdbc/exceptions/MySQLTimeoutException"
    // Looks like it tries to load class from the V8 version of driver.
    // Probably it happens because of driver mapping configuration in org.jetbrains.exposed.v1.sql.Database::driverMapping
    // that expects that all the versions of the Driver have the same package.
    private val timeoutTestDBList = TestDB.ALL_MARIADB + TestDB.ALL_POSTGRES + TestDB.SQLSERVER + TestDB.MYSQL_V8

    @Tag(INCOMPLETE_R2DBC_TEST)
    @Test
    fun timeoutStatements() {
        withDb(timeoutTestDBList) { testDB ->
            this.queryTimeout = 3
            try {
                TransactionManager.current().exec(
                    generateTimeoutStatements(testDB, 5)
                )
                Assertions.fail("Should have thrown a timeout or cancelled statement exception")
            } catch (cause: ExposedSQLException) {
                when (testDB) {
                    // PostgreSQL throws a regular PgSQLException with a cancelled statement message
                    TestDB.POSTGRESQL -> assertTrue(cause.cause is PSQLException)
                    // PostgreSQLNG throws a regular PGSQLSimpleException with a cancelled statement message
                    TestDB.POSTGRESQLNG -> assertTrue(cause.cause is PGSQLSimpleException)
                    else -> assertTrue(cause.cause is SQLTimeoutException)
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

    // Value of -1 is not a valid timeout for any R2DBC drivers
    @Tag(NOT_APPLICABLE_TO_R2DBC)
    @Test
    fun timeoutMinusWithTimeoutStatement() {
        withDb(timeoutTestDBList) { testDB ->
            this.queryTimeout = -1
            try {
                TransactionManager.current().exec(
                    generateTimeoutStatements(testDB, 1)
                )
                Assertions.fail("Should have thrown a timeout or cancelled statement exception")
            } catch (cause: ExposedSQLException) {
                when (testDB) {
                    // PostgreSQL throws a regular PSQLException with a minus timeout value
                    TestDB.POSTGRESQL -> assertTrue(cause.cause is PSQLException)
                    // MySQL, POSTGRESQLNG throws a regular SQLException with a minus timeout value
                    in (TestDB.ALL_MYSQL + TestDB.POSTGRESQLNG) -> assertTrue(cause.cause is SQLException)
                    // MariaDB throws a regular SQLSyntaxErrorException with a minus timeout value
                    in TestDB.ALL_MARIADB -> assertTrue(cause.cause is SQLSyntaxErrorException)
                    // SqlServer throws a regular SQLServerException with a minus timeout value
                    TestDB.SQLSERVER -> assertTrue(cause.cause is SQLServerException)
                    else -> throw NotImplementedError()
                }
            }
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
            } catch (cause: ExposedSQLException) {
                assertTrue(cause.cause is SQLTimeoutException)
            }

            assertTrue(logCaptor.warnLogs.single().contains("Long query"))
        }

        logCaptor.clearLogs()
        logCaptor.close()
    }
}
