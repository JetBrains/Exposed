package org.jetbrains.exposed.v1.r2dbc.sql.tests.postgresql

import io.r2dbc.spi.ConnectionFactoryOptions
import io.r2dbc.spi.Option
import io.r2dbc.spi.R2dbcNonTransientResourceException
import kotlinx.coroutines.flow.single
import kotlinx.coroutines.test.runTest
import nl.altindag.log.LogCaptor
import org.jetbrains.exposed.v1.r2dbc.ExposedR2dbcException
import org.jetbrains.exposed.v1.r2dbc.R2dbcDatabase
import org.jetbrains.exposed.v1.r2dbc.R2dbcDatabaseConfig
import org.jetbrains.exposed.v1.r2dbc.R2dbcTransaction
import org.jetbrains.exposed.v1.r2dbc.tests.LogDbInTestName
import org.jetbrains.exposed.v1.r2dbc.tests.TestDB
import org.jetbrains.exposed.v1.r2dbc.tests.getInt
import org.jetbrains.exposed.v1.r2dbc.tests.getString
import org.jetbrains.exposed.v1.r2dbc.transactions.TransactionManager
import org.jetbrains.exposed.v1.r2dbc.transactions.suspendTransaction
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** r2dbc-postgresql sends the statement timeout as a session `SET`, so these tests check the queries it sends. */
class StatementTimeoutTests : LogDbInTestName() {
    @BeforeEach
    fun assumePostgres() {
        Assumptions.assumeTrue(TestDB.POSTGRESQL in TestDB.enabledDialects())
    }

    @Test
    fun testNoStatementTimeoutIsSentIfNotConfigured() = runTest {
        withSingleConnectionPool { db ->
            val sentQueries = captureSentQueries {
                suspendTransaction(db, readOnly = true) {
                    repeat(3) { exec("SELECT $it") }
                }
            }

            assertTrue(sentQueries.none { it.contains("statement_timeout", ignoreCase = true) })
            assertEquals(listOf("SELECT 0", "SELECT 1", "SELECT 2"), sentQueries.withoutTransactionBoundaries())
        }
    }

    @Test
    fun testStatementTimeoutIsSentOncePerTransaction() = runTest {
        withSingleConnectionPool { db ->
            val sentQueries = captureSentQueries {
                suspendTransaction(db) {
                    queryTimeout = 5
                    repeat(3) { exec("SELECT $it") }
                }
            }

            assertEquals(
                listOf("SET STATEMENT_TIMEOUT = 5000", "SELECT 0", "SELECT 1", "SELECT 2", "RESET statement_timeout"),
                sentQueries.withoutTransactionBoundaries()
            )
        }
    }

    @Test
    fun testConfiguredStatementTimeoutCancelsStatement() = runTest {
        withSingleConnectionPool { db ->
            assertStatementIsCancelled {
                suspendTransaction(db) {
                    queryTimeout = 1
                    exec("SELECT pg_sleep(3)")
                }
            }
        }

        withSingleConnectionPool({ defaultQueryTimeout = 1 }) { db ->
            assertStatementIsCancelled {
                suspendTransaction(db) {
                    exec("SELECT pg_sleep(3)")
                }
            }
        }
    }

    @Test
    fun testStatementTimeoutDoesNotLeakToNextTransactionOnPooledConnection() = runTest {
        withSingleConnectionPool { db ->
            val (initialPid, initialTimeout) = suspendTransaction(db) { backendPid() to statementTimeout() }

            suspendTransaction(db) {
                queryTimeout = 1
                assertEquals(initialPid, backendPid())
                assertEquals("1s", statementTimeout())
            }

            suspendTransaction(db) {
                assertEquals(initialPid, backendPid())
                assertEquals(initialTimeout, statementTimeout())
                // cancelled if the previous transaction's timeout leaked
                exec("SELECT pg_sleep(2)")
            }
        }
    }

    @Test
    fun testSessionStatementTimeoutSurvivesTransactionWithoutTimeout() = runTest {
        withSingleConnectionPool { db ->
            val pid = suspendTransaction(db) {
                exec("SELECT set_config('statement_timeout', '5s', false)")
                backendPid()
            }

            suspendTransaction(db) {
                repeat(3) { exec("SELECT $it") }
                assertEquals(pid, backendPid())
                assertEquals("5s", statementTimeout())
            }

            suspendTransaction(db) {
                assertEquals(pid, backendPid())
                assertEquals("5s", statementTimeout())
                exec("RESET statement_timeout")
            }
        }
    }

    @Test
    fun testDefaultStatementTimeoutIsRestoredAfterTransactionWithTimeout() = runTest {
        // a connection-level timeout is a session default, like a role or database one
        withSingleConnectionPool({
            connectionFactoryOptions { option(ConnectionFactoryOptions.STATEMENT_TIMEOUT, Duration.ofSeconds(7)) }
        }) { db ->
            suspendTransaction(db) {
                assertEquals("7s", statementTimeout())
            }

            suspendTransaction(db) {
                queryTimeout = 1
                assertEquals("1s", statementTimeout())
            }

            suspendTransaction(db) {
                assertEquals("7s", statementTimeout())
            }

            suspendTransaction(db) {
                queryTimeout = 0
                assertEquals("0", statementTimeout())
            }

            suspendTransaction(db) {
                assertEquals("7s", statementTimeout())
            }
        }
    }

    @Test
    fun testStatementTimeoutChangedWithinTransaction() = runTest {
        withSingleConnectionPool { db ->
            val initialTimeout = suspendTransaction(db) { statementTimeout() }

            val sentQueries = captureSentQueries {
                suspendTransaction(db) {
                    queryTimeout = 1
                    assertEquals("1s", statementTimeout())
                    queryTimeout = 2
                    assertEquals("2s", statementTimeout())
                    queryTimeout = null
                    assertEquals(initialTimeout, statementTimeout())
                    assertEquals(initialTimeout, statementTimeout())
                }
            }

            assertEquals(
                listOf(
                    "SET STATEMENT_TIMEOUT = 1000",
                    "SET STATEMENT_TIMEOUT = 2000",
                    "RESET statement_timeout",
                ),
                sentQueries.filter { it.contains("statement_timeout", ignoreCase = true) && !it.startsWith("SELECT") }
            )
        }
    }

    @Test
    fun testStatementTimeoutIsReappliedAfterRollback() = runTest {
        withSingleConnectionPool { db ->
            assertStatementIsCancelled {
                suspendTransaction(db) {
                    queryTimeout = 1
                    exec("SELECT 1")
                    // PostgreSQL reverts the timeout set in the rolled back transaction
                    rollback()
                    exec("SELECT pg_sleep(3)")
                }
            }
        }
    }

    private suspend fun withSingleConnectionPool(
        config: R2dbcDatabaseConfig.Builder.() -> Unit = {},
        body: suspend (R2dbcDatabase) -> Unit
    ) {
        val db = R2dbcDatabase.connect {
            defaultMaxAttempts = 1
            setUrl(TestDB.POSTGRESQL.connection.invoke().replace("r2dbc:", "r2dbc:pool:"))
            connectionFactoryOptions {
                option(Option.valueOf("initialSize"), 1)
                option(Option.valueOf("maxSize"), 1)
            }
            config()
        }
        try {
            // keep pool and dialect warm-up queries out of the captured ones
            suspendTransaction(db) { exec("SELECT 1") }
            body(db)
        } finally {
            TransactionManager.closeAndUnregister(db)
        }
    }

    private suspend fun captureSentQueries(body: suspend () -> Unit): List<String> {
        val logCaptor = LogCaptor.forName("io.r2dbc.postgresql.QUERY")
        try {
            logCaptor.setLogLevelToDebug()
            body()
            return logCaptor.debugLogs.mapNotNull { message ->
                message.substringAfter("Executing query: ", "").takeIf { it.isNotEmpty() }
            }
        } finally {
            logCaptor.close()
        }
    }

    private fun List<String>.withoutTransactionBoundaries(): List<String> = filterNot {
        it.startsWith("BEGIN") || it.startsWith("COMMIT") || it.startsWith("ROLLBACK")
    }

    private suspend fun assertStatementIsCancelled(body: suspend () -> Unit) {
        try {
            body()
            Assertions.fail("Should have thrown a cancelled statement exception")
        } catch (cause: ExposedR2dbcException) {
            assertTrue(cause.cause is R2dbcNonTransientResourceException)
        }
    }

    private suspend fun R2dbcTransaction.backendPid(): Int = exec("SELECT pg_backend_pid()") { it.getInt(1) }!!.single()!!

    private suspend fun R2dbcTransaction.statementTimeout(): String =
        exec("SELECT current_setting('statement_timeout')") { it.getString(1) }!!.single()!!
}
