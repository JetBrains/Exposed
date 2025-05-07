package org.jetbrains.exposed.r2dbc.sql.tests.shared

// NOTE: Needs R2dbcDatabase.connect(connectionFactory = ) to mimic hooking up to a custom connection provider

// import io.r2dbc.spi.Connection
// import io.r2dbc.spi.ConnectionFactories
// import io.r2dbc.spi.ConnectionFactory
// import io.r2dbc.spi.ConnectionFactoryMetadata
// import io.r2dbc.spi.R2dbcException
// import io.r2dbc.spi.R2dbcTransientException
// import kotlinx.coroutines.flow.emptyFlow
// import kotlinx.coroutines.reactive.asPublisher
// import kotlinx.coroutines.reactive.awaitFirst
// import kotlinx.coroutines.runBlocking
// import kotlinx.coroutines.test.runTest
// import org.jetbrains.exposed.r2dbc.sql.R2dbcDatabase
// import org.jetbrains.exposed.r2dbc.sql.tests.TestDB
// import org.jetbrains.exposed.r2dbc.sql.transactions.TransactionManager
// import org.jetbrains.exposed.r2dbc.sql.transactions.suspendTransaction
// import org.junit.After
// import org.junit.Assume
// import org.junit.Test
// import org.reactivestreams.Publisher
// import kotlin.test.assertContains
// import kotlin.test.assertEquals
// import kotlin.test.assertFalse
// import kotlin.test.assertTrue
// import kotlin.test.fail
//
// class ConnectionExceptions {
//
//    abstract class ConnectionSpy(private val connection: Connection) : Connection by connection {
//        var commitCalled = false
//        var rollbackCalled = false
//        var closeCalled = false
//
//        override fun commitTransaction(): Publisher<Void> {
//            commitCalled = true
//            throw CommitException()
//        }
//
//        override fun rollbackTransaction(): Publisher<Void> {
//            rollbackCalled = true
//            return emptyFlow<Void>().asPublisher()
//        }
//
//        override fun close(): Publisher<Void> {
//            closeCalled = true
//            return emptyFlow<Void>().asPublisher()
//        }
//    }
//
//    private class WrappingConnectionFactory<T : Connection>(
//        private val testDB: TestDB,
//        private val connectionDecorator: (Connection) -> T
//    ) : ConnectionFactory {
//        val connections = mutableListOf<T>()
//
//        override fun create(): Publisher<out Connection?> {
//            val publisher = ConnectionFactories.get(testDB.connection()).create()
//            val connection = runBlocking { publisher.awaitFirst() }
//            val wrapped = connectionDecorator(connection)
//            connections.add(wrapped)
//            return publisher
//        }
//
//        override fun getMetadata(): ConnectionFactoryMetadata {
//            throw NotImplementedError()
//        }
//    }
//
//    private class RollbackException : R2dbcTransientException()
//    private class ExceptionOnRollbackConnection(connection: Connection) : ConnectionSpy(connection) {
//        override fun rollbackTransaction(): Publisher<Void>  {
//            super.rollbackTransaction()
//            throw RollbackException()
//        }
//    }
//
//    @Test
//    fun `transaction repetition works even if rollback throws exception`() = runTest {
//        `_transaction repetition works even if rollback throws exception`(::ExceptionOnRollbackConnection)
//    }
//
//    private suspend fun `_transaction repetition works even if rollback throws exception`(connectionDecorator: (Connection) -> ConnectionSpy) {
//        Assume.assumeTrue(TestDB.H2_V2 in TestDB.enabledDialects())
//        Class.forName(TestDB.H2_V2.driver).getDeclaredConstructor().newInstance()
//
//        val wrappingConnectionFactory = WrappingConnectionFactory(TestDB.H2_V2, connectionDecorator)
//        val db = R2dbcDatabase.connect(connectionFactory = wrappingConnectionFactory)
//        try {
//            suspendTransaction(transactionIsolation = java.sql.Connection.TRANSACTION_SERIALIZABLE, db = db) {
//                maxAttempts = 5
//                this.exec("BROKEN_SQL_THAT_CAUSES_EXCEPTION()")
//            }
//            fail("Should have thrown an exception")
//        } catch (e: R2dbcException) {
//            assertContains(e.toString(), "BROKEN_SQL_THAT_CAUSES_EXCEPTION", ignoreCase = false)
//            assertEquals(5, wrappingConnectionFactory.connections.size)
//            wrappingConnectionFactory.connections.forEach {
//                assertFalse(it.commitCalled)
//                assertTrue(it.rollbackCalled)
//                assertTrue(it.closeCalled)
//            }
//        }
//    }
//
//    private class CommitException : R2dbcTransientException()
//    private class ExceptionOnCommitConnection(connection: Connection) : ConnectionSpy(connection) {
//        override fun commitTransaction(): Publisher<Void> {
//            super.commitTransaction()
//            throw CommitException()
//        }
//    }
//
//    @Test
//    fun `transaction repetition works when commit throws exception`() = runTest {
//        `_transaction repetition works when commit throws exception`(::ExceptionOnCommitConnection)
//    }
//
//    private suspend fun `_transaction repetition works when commit throws exception`(connectionDecorator: (Connection) -> ConnectionSpy) {
//        Assume.assumeTrue(TestDB.H2_V2 in TestDB.enabledDialects())
//        Class.forName(TestDB.H2_V2.driver).getDeclaredConstructor().newInstance()
//
//        val wrappingConnectionFactory = WrappingConnectionFactory(TestDB.H2_V2, connectionDecorator)
//        val db = R2dbcDatabase.connect(connectionFactory = wrappingConnectionFactory)
//        try {
//            suspendTransaction(transactionIsolation = java.sql.Connection.TRANSACTION_SERIALIZABLE, db = db) {
//                maxAttempts = 5
//                this.exec("SELECT 1;")
//            }
//            fail("Should have thrown an exception")
//        } catch (_: CommitException) {
//            assertEquals(5, wrappingConnectionFactory.connections.size)
//            wrappingConnectionFactory.connections.forEach {
//                assertTrue(it.commitCalled)
//                assertTrue(it.closeCalled)
//            }
//        }
//    }
//
//    @Test
//    fun `transaction throws exception if all commits throws exception`() = runTest {
//        `_transaction throws exception if all commits throws exception`(::ExceptionOnCommitConnection)
//    }
//
//    private suspend fun `_transaction throws exception if all commits throws exception`(connectionDecorator: (Connection) -> ConnectionSpy) {
//        Assume.assumeTrue(TestDB.H2_V2 in TestDB.enabledDialects())
//        Class.forName(TestDB.H2_V2.driver).getDeclaredConstructor().newInstance()
//
//        val wrappingConnectionFactory = WrappingConnectionFactory(TestDB.H2_V2, connectionDecorator)
//        val db = R2dbcDatabase.connect(connectionFactory = wrappingConnectionFactory)
//        try {
//            suspendTransaction(transactionIsolation = java.sql.Connection.TRANSACTION_SERIALIZABLE, db = db) {
//                maxAttempts = 5
//                this.exec("SELECT 1;")
//            }
//            fail("Should have thrown an exception")
//        } catch (_: CommitException) {
//            // Yay
//        }
//    }
//
//    private class CloseException : R2dbcTransientException()
//    private class ExceptionOnRollbackCloseConnection(connection: Connection) : ConnectionSpy(connection) {
//        override fun rollbackTransaction(): Publisher<Void> {
//            super.rollbackTransaction()
//            throw RollbackException()
//        }
//
//        override fun close(): Publisher<Void> {
//            super.close()
//            throw CloseException()
//        }
//    }
//
//    @Test
//    fun `transaction repetition works even if rollback and close throws exception`() = runTest {
//        `_transaction repetition works even if rollback throws exception`(::ExceptionOnRollbackCloseConnection)
//    }
//
//    @Test
//    fun `transaction repetition works when commit and close throws exception`() = runTest {
//        `_transaction repetition works when commit throws exception`(::ExceptionOnCommitConnection)
//    }
//
//    private class ExceptionOnCommitCloseConnection(connection: Connection) : ConnectionSpy(connection) {
//        override fun commitTransaction(): Publisher<Void> {
//            super.commitTransaction()
//            throw CommitException()
//        }
//
//        override fun close(): Publisher<Void> {
//            super.close()
//            throw CloseException()
//        }
//    }
//
//    @Test
//    fun `transaction throws exception if all commits and close throws exception`() = runTest {
//        `_transaction throws exception if all commits throws exception`(::ExceptionOnCommitCloseConnection)
//    }
//
//    @After
//    fun teardown() {
//        TransactionManager.resetCurrent(null)
//    }
// }
