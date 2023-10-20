package org.jetbrains.exposed.sql.tests.shared

import org.jetbrains.exposed.exceptions.ExposedSQLException
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.DatabaseConfig
import org.jetbrains.exposed.sql.tests.DatabaseTestsBase
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.Test
import java.sql.Connection
import java.sql.SQLTransientException
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

class ConnectionTimeoutTest : DatabaseTestsBase() {

    private class ExceptionOnGetConnectionDataSource : DataSourceStub() {
        var connectCount = 0

        override fun getConnection(): Connection {
            connectCount++
            throw GetConnectException()
        }
    }

    private class GetConnectException : SQLTransientException()

    @Test
    fun `connect fail causes repeated connect attempts`() {
        val datasource = ExceptionOnGetConnectionDataSource()
        val db = Database.connect(datasource = datasource)

        try {
            transaction(Connection.TRANSACTION_SERIALIZABLE, db = db) {
                repetitionAttempts = 42
                exec("SELECT 1;")
                // NO OP
            }
            fail("Should have thrown ${GetConnectException::class.simpleName}")
        } catch (e: ExposedSQLException) {
            assertTrue(e.cause is GetConnectException)
            assertEquals(42, datasource.connectCount)
        }
    }

    @Test
    @Suppress("SwallowedException")
    fun testTransactionRepetitionWithDefaults() {
        val datasource = ExceptionOnGetConnectionDataSource()
        val db = Database.connect(
            datasource = datasource,
            databaseConfig = DatabaseConfig {
                defaultRepetitionAttempts = 10
            }
        )

        try {
            // transaction block should use default DatabaseConfig values when no property is set
            transaction(Connection.TRANSACTION_SERIALIZABLE, db = db) {
                exec("SELECT 1;")
            }
            fail("Should have thrown ${GetConnectException::class.simpleName}")
        } catch (cause: ExposedSQLException) {
            assertEquals(10, datasource.connectCount)
        }

        datasource.connectCount = 0 // reset connection count

        try {
            // property set in transaction block should override default DatabaseConfig
            transaction(Connection.TRANSACTION_SERIALIZABLE, db = db) {
                repetitionAttempts = 25
                exec("SELECT 1;")
            }
            fail("Should have thrown ${GetConnectException::class.simpleName}")
        } catch (cause: ExposedSQLException) {
            assertEquals(25, datasource.connectCount)
        }
    }
}
