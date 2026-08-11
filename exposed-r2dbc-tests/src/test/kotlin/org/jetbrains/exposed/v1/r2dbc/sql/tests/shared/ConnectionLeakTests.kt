package org.jetbrains.exposed.v1.r2dbc.sql.tests.shared

import io.r2dbc.spi.Connection
import io.r2dbc.spi.ConnectionFactories
import io.r2dbc.spi.ConnectionFactory
import io.r2dbc.spi.ConnectionFactoryMetadata
import io.r2dbc.spi.IsolationLevel
import io.r2dbc.spi.R2dbcTransientException
import io.r2dbc.spi.TransactionDefinition
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.jetbrains.exposed.v1.core.vendors.H2Dialect
import org.jetbrains.exposed.v1.r2dbc.R2dbcDatabase
import org.jetbrains.exposed.v1.r2dbc.R2dbcDatabaseConfig
import org.jetbrains.exposed.v1.r2dbc.tests.TestDB
import org.jetbrains.exposed.v1.r2dbc.transactions.TransactionManager
import org.jetbrains.exposed.v1.r2dbc.transactions.suspendTransaction
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.Test
import org.reactivestreams.Publisher
import reactor.core.publisher.Mono
import java.util.TimeZone
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertTrue

/**
 * Tests that a connection handed over by the driver is always released, even if it cannot be used because
 * starting a transaction on it fails.
 */
class ConnectionLeakTests {
    init {
        // these tests open H2 connections without extending R2dbcDatabaseTestsBase, so the same default time zone
        // has to be set here, otherwise H2 sessions opened by this class would break time zone sensitive tests
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
    }

    private class ConnectionSpy(
        private val connection: Connection,
        private val beginBehaviour: (real: () -> Publisher<Void?>) -> Publisher<Void?>
    ) : Connection by connection {
        @Volatile
        var closeCalled = false

        override fun beginTransaction(): Publisher<Void?> = beginBehaviour { connection.beginTransaction() }

        override fun beginTransaction(definition: TransactionDefinition): Publisher<Void?> =
            beginBehaviour { connection.beginTransaction(definition) }

        override fun close(): Publisher<Void?> {
            closeCalled = true
            return connection.close()
        }
    }

    private class WrappingConnectionFactory(
        private val testDB: TestDB,
        private val connectionDecorator: (Connection) -> ConnectionSpy
    ) : ConnectionFactory {
        val connections = CopyOnWriteArrayList<ConnectionSpy>()

        override fun create(): Publisher<out Connection?> = Mono.defer {
            Mono
                .from(ConnectionFactories.get(testDB.connection()).create())
                .map { connection ->
                    connectionDecorator(connection).also { connections.add(it) }
                }
        }

        override fun getMetadata(): ConnectionFactoryMetadata {
            throw NotImplementedError()
        }
    }

    private class BeginException : R2dbcTransientException()

    private fun connect(connectionFactory: ConnectionFactory) = R2dbcDatabase.connect(
        connectionFactory = connectionFactory,
        databaseConfig = R2dbcDatabaseConfig { explicitDialect = H2Dialect() }
    )

    private fun WrappingConnectionFactory.assertNoConnectionLeaked() {
        assertTrue(connections.isNotEmpty(), "No connection was ever acquired, so nothing was actually tested")
        val leaked = connections.withIndex().filterNot { it.value.closeCalled }.map { it.index }
        assertTrue(leaked.isEmpty(), "Connections $leaked of the ${connections.size} acquired were never closed")
    }

    @Test
    fun testConnectionIsReleasedWhenBeginTransactionFails() = runTest {
        Assumptions.assumeTrue(TestDB.H2_V2 in TestDB.enabledDialects())

        val connectionFactory = WrappingConnectionFactory(TestDB.H2_V2) { connection ->
            ConnectionSpy(connection) { Mono.error(BeginException()) }
        }
        val db = connect(connectionFactory)

        try {
            val cause = assertFails {
                suspendTransaction(db = db, transactionIsolation = IsolationLevel.SERIALIZABLE) {
                    maxAttempts = 1
                    exec("SELECT 1;")
                }
            }
            assertTrue(
                generateSequence(cause) { it.cause }.take(10).any { it is BeginException },
                "Expected ${BeginException::class.simpleName} to be reported but got $cause"
            )

            connectionFactory.assertNoConnectionLeaked()
        } finally {
            TransactionManager.closeAndUnregister(db)
        }
    }

    @Test
    fun testConnectionIsReleasedWhenCancelledDuringBeginTransaction() = runTest {
        Assumptions.assumeTrue(TestDB.H2_V2 in TestDB.enabledDialects())

        val beginStarted = CompletableDeferred<Unit>()
        val firstBegin = AtomicBoolean(true)
        // only the first BEGIN stalls, so any cleanup path that acquires another connection still completes
        val connectionFactory = WrappingConnectionFactory(TestDB.H2_V2) { connection ->
            ConnectionSpy(connection) { real ->
                if (firstBegin.getAndSet(false)) {
                    beginStarted.complete(Unit)
                    Mono.never()
                } else {
                    real()
                }
            }
        }
        val db = connect(connectionFactory)

        try {
            val job = launch {
                suspendTransaction(db = db, transactionIsolation = IsolationLevel.SERIALIZABLE) {
                    maxAttempts = 1
                    exec("SELECT 1;")
                }
            }
            beginStarted.await()
            job.cancelAndJoin()

            connectionFactory.assertNoConnectionLeaked()
        } finally {
            TransactionManager.closeAndUnregister(db)
        }
    }

    @Test
    fun testFailedConnectionAttemptsAreNotRetainedOnRetry() = runTest {
        Assumptions.assumeTrue(TestDB.H2_V2 in TestDB.enabledDialects())

        val attempts = 3
        val connectionFactory = WrappingConnectionFactory(TestDB.H2_V2) { connection ->
            ConnectionSpy(connection) { Mono.error(BeginException()) }
        }
        val db = connect(connectionFactory)

        try {
            assertFails {
                suspendTransaction(db = db, transactionIsolation = IsolationLevel.SERIALIZABLE) {
                    maxAttempts = attempts
                    exec("SELECT 1;")
                }
            }

            // every retry must start from a fresh connection & none of them may be left behind
            assertEquals(attempts, connectionFactory.connections.size)
            connectionFactory.assertNoConnectionLeaked()
        } finally {
            TransactionManager.closeAndUnregister(db)
        }
    }
}
