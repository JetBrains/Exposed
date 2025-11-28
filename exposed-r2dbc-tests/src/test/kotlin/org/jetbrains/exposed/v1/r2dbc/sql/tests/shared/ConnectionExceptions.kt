package org.jetbrains.exposed.v1.r2dbc.sql.tests.shared

import io.r2dbc.spi.*
import kotlinx.coroutines.test.runTest
import org.jetbrains.exposed.v1.core.vendors.H2Dialect
import org.jetbrains.exposed.v1.r2dbc.R2dbcDatabase
import org.jetbrains.exposed.v1.r2dbc.R2dbcDatabaseConfig
import org.jetbrains.exposed.v1.r2dbc.tests.TestDB
import org.jetbrains.exposed.v1.r2dbc.transactions.suspendTransaction
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.Test
import org.reactivestreams.Publisher
import reactor.core.publisher.Mono
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.fail

class ConnectionExceptions {

    abstract class ConnectionSpy(private val connection: Connection) : Connection by connection {
        var commitCalled = false
        var rollbackCalled = false
        var closeCalled = false

        override fun commitTransaction(): Publisher<Void?> {
            commitCalled = true
            throw CommitException()
        }

        override fun rollbackTransaction(): Publisher<Void?> {
            rollbackCalled = true
            return Mono.empty()
        }

        override fun close(): Publisher<Void?> {
            closeCalled = true
            return Mono.empty()
        }
    }

    private class WrappingConnectionFactory<T : Connection>(
        private val testDB: TestDB,
        private val connectionDecorator: (Connection) -> T
    ) : ConnectionFactory {
        val connections = mutableListOf<T>()

        override fun create(): Publisher<out Connection?> {
            return Mono.defer {
                Mono
                    .from(ConnectionFactories.get(testDB.connection()).create())
                    .map {
                        val wrapped = connectionDecorator(it)
                        connections.add(wrapped)
                        wrapped
                    }
            }
        }

        override fun getMetadata(): ConnectionFactoryMetadata {
            throw NotImplementedError()
        }
    }

    private class RollbackException : R2dbcTransientException()
    private class ExceptionOnRollbackConnection(connection: Connection) : ConnectionSpy(connection) {
        override fun rollbackTransaction(): Publisher<Void?> {
            Mono.from(super.rollbackTransaction()).subscribe()
            throw RollbackException()
        }
    }

    @Test
    fun `transaction repetition works even if rollback throws exception`() = runTest {
        `_transaction repetition works even if rollback throws exception`(::ExceptionOnRollbackConnection)
    }

    private suspend fun `_transaction repetition works even if rollback throws exception`(connectionDecorator: (Connection) -> ConnectionSpy) {
        Assumptions.assumeTrue(TestDB.H2_V2 in TestDB.enabledDialects())

        val wrappingConnectionFactory = WrappingConnectionFactory(TestDB.H2_V2, connectionDecorator)
        val db = R2dbcDatabase.connect(
            connectionFactory = wrappingConnectionFactory,
            databaseConfig = R2dbcDatabaseConfig { explicitDialect = H2Dialect() }
        )
        try {
            suspendTransaction(db = db, transactionIsolation = IsolationLevel.SERIALIZABLE) {
                maxAttempts = 5
                this.exec("BROKEN_SQL_THAT_CAUSES_EXCEPTION()")
            }
            fail("Should have thrown an exception")
        } catch (e: R2dbcException) {
            assertContains(e.message.toString(), "BROKEN_SQL_THAT_CAUSES_EXCEPTION", ignoreCase = false)
            assertEquals(5, wrappingConnectionFactory.connections.size)
            wrappingConnectionFactory.connections.forEach {
                assertFalse(it.commitCalled)
                assertTrue(it.rollbackCalled)
                assertTrue(it.closeCalled)
            }
        }
    }

    private class CommitException : R2dbcTransientException()
    private class ExceptionOnCommitConnection(connection: Connection) : ConnectionSpy(connection) {
        override fun commitTransaction(): Publisher<Void?> {
            Mono.from(super.commitTransaction()).subscribe()
            throw CommitException()
        }
    }

    @Test
    fun `transaction repetition works when commit throws exception`() = runTest {
        `_transaction repetition works when commit throws exception`(::ExceptionOnCommitConnection)
    }

    private suspend fun `_transaction repetition works when commit throws exception`(connectionDecorator: (Connection) -> ConnectionSpy) {
        Assumptions.assumeTrue(TestDB.H2_V2 in TestDB.enabledDialects())

        val wrappingConnectionFactory = WrappingConnectionFactory(TestDB.H2_V2, connectionDecorator)
        val db = R2dbcDatabase.connect(
            connectionFactory = wrappingConnectionFactory,
            databaseConfig = R2dbcDatabaseConfig { explicitDialect = H2Dialect() }
        )
        try {
            suspendTransaction(db = db, transactionIsolation = IsolationLevel.SERIALIZABLE) {
                maxAttempts = 5
                this.exec("SELECT 1;")
            }
            fail("Should have thrown an exception")
        } catch (_: CommitException) {
            assertEquals(5, wrappingConnectionFactory.connections.size)
            wrappingConnectionFactory.connections.forEach {
                assertTrue(it.commitCalled)
                assertTrue(it.closeCalled)
            }
        }
    }

    @Test
    fun `transaction throws exception if all commits throws exception`() = runTest {
        `_transaction throws exception if all commits throws exception`(::ExceptionOnCommitConnection)
    }

    private suspend fun `_transaction throws exception if all commits throws exception`(connectionDecorator: (Connection) -> ConnectionSpy) {
        Assumptions.assumeTrue(TestDB.H2_V2 in TestDB.enabledDialects())

        val wrappingConnectionFactory = WrappingConnectionFactory(TestDB.H2_V2, connectionDecorator)
        val db = R2dbcDatabase.connect(
            connectionFactory = wrappingConnectionFactory,
            databaseConfig = R2dbcDatabaseConfig { explicitDialect = H2Dialect() }
        )
        try {
            suspendTransaction(db = db, transactionIsolation = IsolationLevel.SERIALIZABLE) {
                maxAttempts = 5
                this.exec("SELECT 1;")
            }
            fail("Should have thrown an exception")
        } catch (_: CommitException) {
            // Yay
        }
    }

    private class CloseException : R2dbcTransientException()
    private class ExceptionOnRollbackCloseConnection(connection: Connection) : ConnectionSpy(connection) {
        override fun rollbackTransaction(): Publisher<Void?> {
            Mono.from(super.rollbackTransaction()).subscribe()
            throw RollbackException()
        }

        override fun close(): Publisher<Void?> {
            Mono.from(super.close()).subscribe()
            throw CloseException()
        }
    }

    @Test
    fun `transaction repetition works even if rollback and close throws exception`() = runTest {
        `_transaction repetition works even if rollback throws exception`(::ExceptionOnRollbackCloseConnection)
    }

    @Test
    fun `transaction repetition works when commit and close throws exception`() = runTest {
        `_transaction repetition works when commit throws exception`(::ExceptionOnCommitConnection)
    }

    private class ExceptionOnCommitCloseConnection(connection: Connection) : ConnectionSpy(connection) {
        override fun commitTransaction(): Publisher<Void?> {
            Mono.from(super.commitTransaction()).subscribe()
            throw CommitException()
        }

        override fun close(): Publisher<Void?> {
            Mono.from(super.close()).subscribe()
            throw CloseException()
        }
    }

    @Test
    fun `transaction throws exception if all commits and close throws exception`() = runTest {
        `_transaction throws exception if all commits throws exception`(::ExceptionOnCommitCloseConnection)
    }
}
