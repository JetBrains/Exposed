package org.jetbrains.exposed.sql.tests.shared

import org.jetbrains.exposed.exceptions.ExposedSQLException
import org.jetbrains.exposed.sql.tests.DatabaseTestsBase
import org.jetbrains.exposed.sql.tests.TestDB
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.junit.Test
import kotlin.test.assertFailsWith

/**
 * @author ivan@daangn.com
 */
class QueryTimeoutTest : DatabaseTestsBase() {

    private fun generateTimeoutStatements(db: TestDB, timeout: Int): String {
        return when (db) {
            TestDB.MYSQL -> "SELECT SLEEP($timeout) = 0;"
            TestDB.POSTGRESQL -> "SELECT pg_sleep($timeout);"
            else -> throw NotImplementedError()
        }
    }

    @Test
    fun timeoutStatements() {
        withDb(listOf(TestDB.MYSQL, TestDB.POSTGRESQL)) {
            this.timeout = 3
            assertFailsWith<ExposedSQLException> {
                TransactionManager.current().exec(
                    generateTimeoutStatements(it, 5)
                )
            }
        }
    }

    @Test
    fun noTimeoutWithTimeoutStatement() {
        withDb(listOf(TestDB.MYSQL, TestDB.POSTGRESQL)) {
            this.timeout = 3
            TransactionManager.current().exec(
                generateTimeoutStatements(it, 1)
            )
        }
    }
}
