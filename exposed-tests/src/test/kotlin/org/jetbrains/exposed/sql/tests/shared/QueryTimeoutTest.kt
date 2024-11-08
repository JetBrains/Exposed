package org.jetbrains.exposed.sql.tests.shared

import com.impossibl.postgres.jdbc.PGSQLSimpleException
import com.microsoft.sqlserver.jdbc.SQLServerException
import org.jetbrains.exposed.exceptions.ExposedSQLException
import org.jetbrains.exposed.sql.tests.DatabaseTestsBase
import org.jetbrains.exposed.sql.tests.TestDB
import org.jetbrains.exposed.sql.transactions.JdbcTransaction
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.junit.Assert.fail
import org.junit.Test
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
    // Probably it happens because of driver mapping configuration in org.jetbrains.exposed.sql.Database::driverMapping
    // that expects that all the versions of the Driver have the same package.
    private val timeoutTestDBList = TestDB.ALL_MARIADB + TestDB.ALL_POSTGRES + TestDB.SQLSERVER + TestDB.MYSQL_V8

    @Test
    fun timeoutStatements() {
        withDb(timeoutTestDBList) { testDB ->
            this.queryTimeout = 3
            try {
                (TransactionManager.current() as JdbcTransaction).exec(
                    generateTimeoutStatements(testDB, 5)
                )
                fail("Should have thrown a timeout or cancelled statement exception")
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
            (TransactionManager.current() as JdbcTransaction).exec(
                generateTimeoutStatements(it, 1)
            )
        }
    }

    @Test
    fun timeoutZeroWithTimeoutStatement() {
        withDb(timeoutTestDBList) {
            this.queryTimeout = 0
            (TransactionManager.current() as JdbcTransaction).exec(
                generateTimeoutStatements(it, 1)
            )
        }
    }

    @Test
    fun timeoutMinusWithTimeoutStatement() {
        withDb(timeoutTestDBList) { testDB ->
            this.queryTimeout = -1
            try {
                (TransactionManager.current() as JdbcTransaction).exec(
                    generateTimeoutStatements(testDB, 1)
                )
                fail("Should have thrown a timeout or cancelled statement exception")
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
}
