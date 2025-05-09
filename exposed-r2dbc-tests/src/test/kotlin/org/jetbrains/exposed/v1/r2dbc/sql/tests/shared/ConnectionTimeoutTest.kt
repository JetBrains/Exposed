package org.jetbrains.exposed.v1.r2dbc.sql.tests.shared

// NOTE: Needs R2dbcDatabase.connect(connectionFactory = ) to mimic hooking up to a custom connection provider

// import io.r2dbc.spi.ConnectionFactory
// import io.r2dbc.spi.ConnectionFactoryMetadata
// import io.r2dbc.spi.R2dbcTransientException
// import kotlinx.coroutines.test.runTest
// import org.jetbrains.exposed.v1.exceptions.ExposedSQLException
// import org.jetbrains.exposed.v1.r2dbc.sql.R2dbcDatabase
// import org.jetbrains.exposed.v1.r2dbc.sql.tests.R2dbcDatabaseTestsBase
// import org.jetbrains.exposed.v1.r2dbc.sql.transactions.suspendTransaction
// import org.jetbrains.exposed.v1.core.DatabaseConfig
// import org.junit.Test
// import org.reactivestreams.Publisher
// import java.sql.Connection
// import kotlin.test.assertEquals
// import kotlin.test.assertTrue
// import kotlin.test.fail
//
// class ConnectionTimeoutTest : R2dbcDatabaseTestsBase() {
//
//    private class ExceptionOnGetConnectionFactory : ConnectionFactory {
//        var connectCount = 0
//
//        override fun create(): Publisher<out io.r2dbc.spi.Connection?> {
//            connectCount++
//            throw GetConnectException()
//        }
//
//        override fun getMetadata(): ConnectionFactoryMetadata {
//            throw NotImplementedError()
//        }
//    }
//
//    private class GetConnectException : R2dbcTransientException()
//
//    @Test
//    fun `connect fail causes repeated connect attempts`() = runTest {
//        val wrappingConnectionFactory = ExceptionOnGetConnectionFactory()
//        val db = R2dbcDatabase.connect(connectioNFactory = wrappingConnectionFactory)
//
//        try {
//            suspendTransaction(transactionIsolation = Connection.TRANSACTION_SERIALIZABLE, db = db) {
//                maxAttempts = 42
//                exec("SELECT 1;")
//                // NO OP
//            }
//            fail("Should have thrown ${GetConnectException::class.simpleName}")
//        } catch (e: ExposedSQLException) {
//            assertTrue(e.cause is GetConnectException)
//            assertEquals(42, wrappingConnectionFactory.connectCount)
//        }
//    }
//
//    @Test
//    @Suppress("SwallowedException")
//    fun testTransactionRepetitionWithDefaults() = runTest {
//        val wrappingConnectionFactory = ExceptionOnGetConnectionFactory()
//        val db = R2dbcDatabase.connect(
//            connectioNFactory = wrappingConnectionFactory,
//            databaseConfig = DatabaseConfig {
//                defaultMaxAttempts = 10
//            }
//        )
//
//        try {
//            // transaction block should use default DatabaseConfig values when no property is set
//            suspendTransaction(transactionIsolation = Connection.TRANSACTION_SERIALIZABLE, db = db) {
//                exec("SELECT 1;")
//            }
//            fail("Should have thrown ${GetConnectException::class.simpleName}")
//        } catch (cause: ExposedSQLException) {
//            assertEquals(10, wrappingConnectionFactory.connectCount)
//        }
//
//        wrappingConnectionFactory.connectCount = 0 // reset connection count
//
//        try {
//            // property set in transaction block should override default DatabaseConfig
//            suspendTransaction(transactionIsolation = Connection.TRANSACTION_SERIALIZABLE, db = db) {
//                maxAttempts = 25
//                exec("SELECT 1;")
//            }
//            fail("Should have thrown ${GetConnectException::class.simpleName}")
//        } catch (cause: ExposedSQLException) {
//            assertEquals(25, wrappingConnectionFactory.connectCount)
//        }
//    }
// }
