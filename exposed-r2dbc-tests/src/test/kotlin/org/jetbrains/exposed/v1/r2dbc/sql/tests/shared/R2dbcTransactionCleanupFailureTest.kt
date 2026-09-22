/*
 * Copyright 2000-2026 JetBrains s.r.o. and contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE.txt file.
 */
package org.jetbrains.exposed.v1.r2dbc.sql.tests.shared

import io.r2dbc.spi.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.jetbrains.exposed.v1.core.vendors.H2Dialect
import org.jetbrains.exposed.v1.core.vendors.PostgreSQLDialect
import org.jetbrains.exposed.v1.r2dbc.R2dbcDatabase
import org.jetbrains.exposed.v1.r2dbc.R2dbcDatabaseConfig
import org.jetbrains.exposed.v1.r2dbc.tests.TestDB
import org.jetbrains.exposed.v1.r2dbc.transactions.TransactionManager
import org.jetbrains.exposed.v1.r2dbc.transactions.suspendTransaction
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.reactivestreams.Publisher
import reactor.core.publisher.Mono
import java.util.TimeZone
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.*

class R2dbcTransactionCleanupFailureTest {
    init {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
    }

    enum class CleanupBoundary { ROLLBACK, CONNECTION_CLOSE, BOTH }

    companion object {
        @JvmStatic
        fun databases() = TestDB.enabledDialects().filter { it == TestDB.H2_V2 || it == TestDB.POSTGRESQL }

        @JvmStatic
        fun cleanupCases() = databases().flatMap { db -> CleanupBoundary.entries.map { Arguments.of(db, it) } }

        @JvmStatic
        fun nestedCases() = databases().flatMap { db -> listOf(Arguments.of(db, false), Arguments.of(db, true)) }
    }

    private class PrimaryFailure : IllegalStateException("Transaction body failed")
    private class DriverFailure : R2dbcTransientException("Transaction body failed")

    // Extra fields prevent coroutine stacktrace recovery from cloning these fixtures and losing suppressed exceptions.
    private class StatementFailure(val marker: String) : IllegalStateException(marker)
    private class CleanupFailure(val boundary: String) : IllegalStateException(boundary)
    private class PrimaryCancellation(val location: String) : CancellationException(location)

    @ParameterizedTest(allowZeroInvocations = true)
    @MethodSource("databases")
    fun testStatementPublisherFailureKeepsCleanupFailuresExactlyOnce(testDB: TestDB) {
        runBlocking {
            val primary = StatementFailure("Statement publisher failed")
            val rollback = CleanupFailure("Rollback failed")
            val close = CleanupFailure("Close failed")
            var executions = 0
            var rollbacks = 0
            var closes = 0
            withDatabase(testDB, decorate = { connection, _ ->
                val counted = object : Connection by connection {
                    override fun createStatement(sql: String): Statement {
                        val statement = connection.createStatement(sql)
                        return object : Statement by statement {
                            override fun execute(): Publisher<out io.r2dbc.spi.Result> = Mono.defer {
                                executions++
                                Mono.error(primary)
                            }
                        }
                    }

                    override fun rollbackTransaction(): Publisher<Void?> = Mono.defer {
                        rollbacks++
                        Mono.from(connection.rollbackTransaction())
                    }

                    override fun close(): Publisher<Void?> = Mono.defer {
                        closes++
                        Mono.from(connection.close())
                    }
                }
                CleanupConnection(counted, listOf(rollback), close)
            }) { database ->
                val observed = assertFailsWith<StatementFailure> {
                    suspendTransaction(database) { exec("SELECT 1") }
                }
                assertSame(primary, observed)
                assertEquals(listOf(rollback, close), observed.suppressed.toList())
                assertEquals(1, executions)
                assertEquals(1, rollbacks)
                assertEquals(1, closes)
            }
        }
    }

    @ParameterizedTest(allowZeroInvocations = true)
    @MethodSource("cleanupCases")
    fun testCleanupFailuresAreSuppressedInOccurrenceOrder(testDB: TestDB, boundary: CleanupBoundary) {
        runBlocking {
            val primary = PrimaryFailure()
            val rollback = CleanupFailure("Rollback failed")
            val close = CleanupFailure("Close failed")
            val rollbackFailures = if (boundary != CleanupBoundary.CONNECTION_CLOSE) listOf(rollback) else emptyList()
            val closeFailure = if (boundary != CleanupBoundary.ROLLBACK) close else null
            withDatabase(testDB, decorate = { connection, _ ->
                CleanupConnection(connection, rollbackFailures, closeFailure)
            }) { database ->
                val observed = assertFailsWith<PrimaryFailure> {
                    suspendTransaction(database) {
                        exec("SELECT 1")
                        throw primary
                    }
                }
                assertSame(primary, observed)
                val expected = when (boundary) {
                    CleanupBoundary.ROLLBACK -> listOf(rollback)
                    CleanupBoundary.CONNECTION_CLOSE -> listOf(close)
                    CleanupBoundary.BOTH -> listOf(rollback, close)
                }
                assertEquals(expected, observed.suppressed.toList())
            }
        }
    }

    @ParameterizedTest(allowZeroInvocations = true)
    @MethodSource("databases")
    fun testCleanupDoesNotSuppressThePrimaryExceptionOntoItself(testDB: TestDB) {
        runBlocking {
            val primary = PrimaryFailure()
            withDatabase(testDB, decorate = { connection, _ -> CleanupConnection(connection, listOf(primary), primary) }) { database ->
                val observed = assertFailsWith<PrimaryFailure> {
                    suspendTransaction(database) {
                        exec("SELECT 1")
                        throw primary
                    }
                }
                assertSame(primary, observed)
                assertTrue(observed.suppressed.isEmpty())
            }
        }
    }

    @ParameterizedTest(allowZeroInvocations = true)
    @MethodSource("databases")
    fun testCloseFailureDoesNotReplaceSuccessfulResult(testDB: TestDB) {
        runBlocking {
            val close = CleanupFailure("Close failed")
            withDatabase(testDB, decorate = { connection, _ -> CleanupConnection(connection, closeFailure = close) }) { database ->
                val result = suspendTransaction(database) {
                    exec("SELECT 1")
                    42
                }
                assertEquals(42, result)
            }
        }
    }

    @ParameterizedTest(allowZeroInvocations = true)
    @MethodSource("databases")
    fun testRetryFailuresKeepTheirOwnCleanupExceptions(testDB: TestDB) {
        runBlocking {
            val first = DriverFailure()
            val second = DriverFailure()
            val firstRollback = CleanupFailure("First attempt rollback failed")
            val firstRetryRollback = CleanupFailure("First attempt retry rollback failed")
            val firstClose = CleanupFailure("First attempt close failed")
            val secondRollback = CleanupFailure("Second attempt rollback failed")
            val secondRetryRollback = CleanupFailure("Second attempt retry rollback failed")
            val secondClose = CleanupFailure("Second attempt close failed")
            var attempts = 0
            withDatabase(testDB, decorate = { connection, index ->
                if (index == 0) {
                    CleanupConnection(connection, listOf(firstRollback, firstRetryRollback), firstClose)
                } else {
                    CleanupConnection(connection, listOf(secondRollback, secondRetryRollback), secondClose)
                }
            }) { database ->
                val observed = assertFailsWith<DriverFailure> {
                    suspendTransaction(database) {
                        maxAttempts = 2
                        minRetryDelay = 0
                        maxRetryDelay = 0
                        exec("SELECT 1")
                        throw if (attempts++ == 0) first else second
                    }
                }
                assertEquals(2, attempts)
                assertSame(second, observed)
                assertEquals(listOf(firstRollback, firstRetryRollback, firstClose), first.suppressed.toList())
                assertEquals(listOf(secondRollback, secondRetryRollback, secondClose), second.suppressed.toList())
            }
        }
    }

    @ParameterizedTest(allowZeroInvocations = true)
    @MethodSource("databases")
    fun testRetryCanSucceedAfterCleanupFailures(testDB: TestDB) {
        runBlocking {
            val primary = DriverFailure()
            val rollback = CleanupFailure("Rollback failed")
            val retryRollback = CleanupFailure("Retry rollback failed")
            val close = CleanupFailure("Close failed")
            var attempts = 0
            withDatabase(testDB, decorate = { connection, index ->
                if (index == 0) CleanupConnection(connection, listOf(rollback, retryRollback), close) else connection
            }) { database ->
                val result = suspendTransaction(database) {
                    maxAttempts = 2
                    minRetryDelay = 0
                    maxRetryDelay = 0
                    exec("SELECT 1")
                    if (attempts++ == 0) throw primary
                    42
                }
                assertEquals(42, result)
                assertEquals(2, attempts)
                assertEquals(listOf(rollback, retryRollback, close), primary.suppressed.toList())
            }
        }
    }

    @ParameterizedTest(allowZeroInvocations = true)
    @MethodSource("databases")
    fun testCommitFailureKeepsCleanupExceptions(testDB: TestDB) {
        runBlocking {
            val primary = DriverFailure()
            val rollback = CleanupFailure("Rollback failed")
            val retryRollback = CleanupFailure("Retry rollback failed")
            val close = CleanupFailure("Close failed")
            withDatabase(testDB, decorate = { connection, _ ->
                CleanupConnection(connection, listOf(rollback, retryRollback), close, commitFailure = primary)
            }) { database ->
                val observed = assertFailsWith<DriverFailure> {
                    suspendTransaction(database) {
                        minRetryDelay = 0
                        maxRetryDelay = 0
                        exec("SELECT 1")
                    }
                }
                assertSame(primary, observed)
                assertEquals(listOf(rollback, retryRollback, close), observed.suppressed.toList())
            }
        }
    }

    @ParameterizedTest(allowZeroInvocations = true)
    @MethodSource("nestedCases")
    fun testNestedFailureKeepsCleanupExceptions(testDB: TestDB, useSavepoints: Boolean) {
        runBlocking {
            val primary = PrimaryFailure()
            val savepointRollback = CleanupFailure("Savepoint rollback failed")
            val rollback = CleanupFailure("Rollback failed")
            val close = CleanupFailure("Close failed")
            withDatabase(testDB, useNestedTransactions = useSavepoints, decorate = { connection, _ ->
                CleanupConnection(connection, listOf(rollback), close, savepointRollback)
            }) { database ->
                val observed = assertFailsWith<PrimaryFailure> {
                    suspendTransaction(database) {
                        exec("SELECT 1")
                        suspendTransaction(database) {
                            exec("SELECT 1")
                            throw primary
                        }
                    }
                }
                assertSame(primary, observed)
                val expected = if (useSavepoints) listOf(savepointRollback, rollback, close) else listOf(rollback, close)
                assertEquals(expected, observed.suppressed.toList())
            }
        }
    }

    @ParameterizedTest(allowZeroInvocations = true)
    @MethodSource("databases")
    fun testCancellationKeepsCleanupExceptions(testDB: TestDB) {
        runBlocking {
            val bodyStarted = CompletableDeferred<Unit>()
            val observed = CompletableDeferred<Throwable>()
            val cancellation = PrimaryCancellation("Cancelled in transaction body")
            val rollback = CleanupFailure("Rollback failed")
            val close = CleanupFailure("Close failed")
            withDatabase(testDB, decorate = { connection, _ -> CleanupConnection(connection, listOf(rollback), close) }) { database ->
                val job = launch {
                    try {
                        suspendTransaction(database) {
                            exec("SELECT 1")
                            bodyStarted.complete(Unit)
                            awaitCancellation()
                        }
                    } catch (failure: Throwable) {
                        bodyStarted.completeExceptionally(failure)
                        observed.complete(failure)
                    }
                }
                bodyStarted.await()
                job.cancel(cancellation)
                job.join()
                val failure = observed.await()
                assertSame(cancellation, failure)
                assertEquals(listOf(rollback, close), failure.suppressed.toList())
            }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun testCancellationDuringRetryDelayKeepsConnectionCloseFailure() {
        Assumptions.assumeTrue(TestDB.H2_V2 in TestDB.enabledDialects())
        runTest {
            val rollbacksCompleted = CompletableDeferred<Unit>()
            val observed = CompletableDeferred<Throwable>()
            val primary = DriverFailure()
            val cancellation = PrimaryCancellation("Cancelled during retry delay")
            val rollback = CleanupFailure("Rollback failed")
            val retryRollback = CleanupFailure("Retry rollback failed")
            val close = CleanupFailure("Close failed")
            var attempts = 0
            withDatabase(TestDB.H2_V2, decorate = { connection, _ ->
                CleanupConnection(connection, listOf(rollback, retryRollback), close, afterRollback = { count ->
                    if (count == 2) rollbacksCompleted.complete(Unit)
                })
            }) { database ->
                val job = launch {
                    try {
                        suspendTransaction(database) {
                            maxAttempts = 2
                            minRetryDelay = 60_000
                            maxRetryDelay = 60_000
                            attempts++
                            exec("SELECT 1")
                            throw primary
                        }
                    } catch (failure: Throwable) {
                        rollbacksCompleted.completeExceptionally(failure)
                        observed.complete(failure)
                    }
                }
                rollbacksCompleted.await()
                runCurrent()
                assertTrue(job.isActive)
                job.cancel(cancellation)
                job.join()
                val failure = observed.await()
                assertSame(cancellation, failure)
                assertEquals(1, attempts)
                assertEquals(listOf(rollback, retryRollback), primary.suppressed.toList())
                assertEquals(listOf(close), failure.suppressed.toList())
            }
        }
    }

    private class CleanupConnection(
        private val connection: Connection,
        private val rollbackFailures: List<Throwable> = emptyList(),
        private val closeFailure: Throwable? = null,
        private val savepointFailure: Throwable? = null,
        private val commitFailure: Throwable? = null,
        private val afterRollback: (Int) -> Unit = {}
    ) : Connection by connection {
        private var rollbackCount = 0

        override fun commitTransaction(): Publisher<Void?> =
            if (commitFailure == null) connection.commitTransaction() else Mono.error(commitFailure)

        override fun rollbackTransaction(): Publisher<Void?> = Mono.from(connection.rollbackTransaction()).then(
            Mono.defer {
                val failure = rollbackFailures.getOrNull(rollbackCount++)
                afterRollback(rollbackCount)
                if (failure == null) Mono.empty() else Mono.error(failure)
            }
        )

        override fun rollbackTransactionToSavepoint(name: String): Publisher<Void?> =
            Mono.from(connection.rollbackTransactionToSavepoint(name)).then(
                Mono.defer {
                    if (savepointFailure == null) Mono.empty() else Mono.error(savepointFailure)
                }
            )

        override fun close(): Publisher<Void?> = Mono.from(connection.close()).then(
            Mono.defer {
                if (closeFailure == null) Mono.empty() else Mono.error(closeFailure)
            }
        )
    }

    private suspend fun withDatabase(
        testDB: TestDB,
        useNestedTransactions: Boolean = false,
        decorate: (Connection, Int) -> Connection,
        block: suspend (R2dbcDatabase) -> Unit
    ) {
        val previousDatabase = TransactionManager.defaultDatabase
        val delegate = ConnectionFactories.get(testDB.connection())
        val connectionCount = AtomicInteger()
        val factory = object : ConnectionFactory {
            override fun getMetadata() = delegate.metadata

            override fun create(): Publisher<out Connection> = Mono.from(delegate.create()).map { connection ->
                decorate(connection, connectionCount.getAndIncrement())
            }
        }
        val database = R2dbcDatabase.connect(
            factory,
            R2dbcDatabaseConfig.Builder().apply {
                setUrl(testDB.connection())
                explicitDialect = if (testDB == TestDB.H2_V2) H2Dialect() else PostgreSQLDialect()
                defaultR2dbcIsolationLevel = IsolationLevel.READ_COMMITTED
                defaultMaxAttempts = 1
                this.useNestedTransactions = useNestedTransactions
            }
        )
        try {
            block(database)
        } finally {
            TransactionManager.closeAndUnregister(database)
            TransactionManager.defaultDatabase = previousDatabase
        }
    }
}
