package org.jetbrains.exposed.v1.r2dbc.sql.tests.shared

import io.r2dbc.spi.Connection
import io.r2dbc.spi.ConnectionFactory
import io.r2dbc.spi.ConnectionFactoryMetadata
import io.r2dbc.spi.IsolationLevel
import io.r2dbc.spi.R2dbcTransientException
import kotlinx.coroutines.test.runTest
import org.jetbrains.exposed.v1.core.vendors.H2Dialect
import org.jetbrains.exposed.v1.r2dbc.ExposedR2dbcException
import org.jetbrains.exposed.v1.r2dbc.R2dbcDatabase
import org.jetbrains.exposed.v1.r2dbc.R2dbcDatabaseConfig
import org.jetbrains.exposed.v1.r2dbc.tests.R2dbcDatabaseTestsBase
import org.jetbrains.exposed.v1.r2dbc.transactions.suspendTransaction
import org.junit.Test
import org.reactivestreams.Publisher
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

class ConnectionTimeoutTest : R2dbcDatabaseTestsBase() {

    private class ExceptionOnGetConnectionFactory : ConnectionFactory {
        var connectCount = 0

        override fun create(): Publisher<out Connection?> {
            connectCount++
            throw GetConnectException()
        }

        override fun getMetadata(): ConnectionFactoryMetadata {
            throw NotImplementedError()
        }
    }

    private class GetConnectException : R2dbcTransientException()

    @Test
    fun `connect fail causes repeated connect attempts`() = runTest {
        val connectionFactory = ExceptionOnGetConnectionFactory()
        val db = R2dbcDatabase.connect(
            connectionFactory = connectionFactory,
            databaseConfig = R2dbcDatabaseConfig { explicitDialect = H2Dialect() }
        )

        try {
            suspendTransaction(db = db, transactionIsolation = IsolationLevel.SERIALIZABLE) {
                maxAttempts = 42
                exec("SELECT 1;")
                // NO OP
            }
            fail("Should have thrown ${GetConnectException::class.simpleName}")
        } catch (e: ExposedR2dbcException) {
            assertTrue(e.cause?.cause is GetConnectException)
            assertEquals(42, connectionFactory.connectCount)
        }
    }

    @Test
    @Suppress("SwallowedException")
    fun testTransactionRepetitionWithDefaults() = runTest {
        val connectionFactory = ExceptionOnGetConnectionFactory()
        val db = R2dbcDatabase.connect(
            connectionFactory = connectionFactory,
            databaseConfig = R2dbcDatabaseConfig {
                defaultMaxAttempts = 10
                explicitDialect = H2Dialect()
            }
        )

        try {
            // transaction block should use default DatabaseConfig values when no property is set
            suspendTransaction(db = db, transactionIsolation = IsolationLevel.SERIALIZABLE) {
                exec("SELECT 1;")
            }
            fail("Should have thrown ${GetConnectException::class.simpleName}")
        } catch (_: ExposedR2dbcException) {
            assertEquals(10, connectionFactory.connectCount)
        }

        connectionFactory.connectCount = 0 // reset connection count

        try {
            // property set in transaction block should override default DatabaseConfig
            suspendTransaction(db = db, transactionIsolation = IsolationLevel.SERIALIZABLE) {
                maxAttempts = 25
                exec("SELECT 1;")
            }
            fail("Should have thrown ${GetConnectException::class.simpleName}")
        } catch (_: ExposedR2dbcException) {
            assertEquals(25, connectionFactory.connectCount)
        }
    }
}
