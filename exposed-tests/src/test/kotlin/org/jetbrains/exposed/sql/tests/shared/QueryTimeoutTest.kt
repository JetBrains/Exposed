package org.jetbrains.exposed.sql.tests.shared

import com.impossibl.postgres.jdbc.PGSQLSimpleException
import com.microsoft.sqlserver.jdbc.SQLServerException
import org.jetbrains.exposed.exceptions.ExposedSQLException
import org.jetbrains.exposed.sql.tests.DatabaseTestsBase
import org.jetbrains.exposed.sql.tests.TestDB
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
            TestDB.MYSQL_V5, TestDB.MARIADB -> "SELECT SLEEP($timeout) = 0;"
            TestDB.POSTGRESQL, TestDB.POSTGRESQLNG -> "SELECT pg_sleep($timeout);"
            TestDB.SQLSERVER -> "WAITFOR DELAY '00:00:$timeout';"
            else -> throw NotImplementedError()
        }
    }

    // TODO probably could be fixed for MySql 8, 5
    private val timeoutTestDBList = listOf(TestDB.MARIADB, TestDB.POSTGRESQL, TestDB.POSTGRESQLNG, TestDB.SQLSERVER)

    @Test
    fun timeoutStatements() {
        withDb(timeoutTestDBList) { testDB ->
            this.queryTimeout = 3
            try {
                TransactionManager.current().exec(
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
    fun timeoutMinusWithTimeoutStatement() {
        withDb(timeoutTestDBList) { testDB ->
            this.queryTimeout = -1
            try {
                TransactionManager.current().exec(
                    generateTimeoutStatements(testDB, 1)
                )
                fail("Should have thrown a timeout or cancelled statement exception")
            } catch (cause: ExposedSQLException) {
                when (testDB) {
                    // PostgreSQL throws a regular PSQLException with a minus timeout value
                    TestDB.POSTGRESQL -> assertTrue(cause.cause is PSQLException)
                    // MySQL, POSTGRESQLNG throws a regular SQLException with a minus timeout value
                    TestDB.MYSQL_V5, TestDB.POSTGRESQLNG -> assertTrue(cause.cause is SQLException)
                    // MariaDB throws a regular SQLSyntaxErrorException with a minus timeout value
                    TestDB.MARIADB -> assertTrue(cause.cause is SQLSyntaxErrorException)
                    // SqlServer throws a regular SQLServerException with a minus timeout value
                    TestDB.SQLSERVER -> assertTrue(cause.cause is SQLServerException)
                    else -> throw NotImplementedError()
                }
            }
        }
    }
}
