/*
 * Copyright 2000-2026 JetBrains s.r.o. and contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE.txt file.
 */

package org.jetbrains.exposed.v1.tests.shared

import kotlinx.coroutines.*
import org.jetbrains.exposed.v1.core.DatabaseConfig
import org.jetbrains.exposed.v1.core.vendors.H2Dialect
import org.jetbrains.exposed.v1.core.vendors.PostgreSQLDialect
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.tests.TestDB
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.sql.*
import java.util.TimeZone
import kotlin.test.*

// Fault injection adapted from Sunghyouk Bae's reproducer linked in EXPOSED-1076.
class JdbcTransactionCleanupFailureTest {
    init {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
    }

    enum class ExecutionMode { BLOCKING, SUSPENDING }
    enum class CleanupBoundary { ROLLBACK, STATEMENT_CLOSE, CONNECTION_CLOSE, ALL }

    companion object {
        @JvmStatic
        fun databases(): List<TestDB> {
            return TestDB.enabledDialects().filter { it == TestDB.H2_V2 || it == TestDB.POSTGRESQL }
        }

        @JvmStatic
        fun modes(): List<Arguments> {
            return databases().flatMap { db -> ExecutionMode.entries.map { Arguments.of(db, it) } }
        }

        @JvmStatic
        fun cleanupBoundaries(): List<Arguments> {
            return databases().flatMap { db ->
                ExecutionMode.entries.flatMap { mode -> CleanupBoundary.entries.map { Arguments.of(db, mode, it) } }
            }
        }

        @JvmStatic
        fun retryOutcomes(): List<Arguments> {
            return databases().flatMap { db ->
                ExecutionMode.entries.flatMap { mode -> listOf(false, true).map { Arguments.of(db, mode, it) } }
            }
        }
    }

    // The additional field prevents coroutine stacktrace recovery from copying these exceptions.
    private class PrimaryFailure(val marker: String) : IllegalStateException(marker)
    private class PrimarySqlFailure(val marker: String) : SQLException(marker)
    private class PrimaryCancellation(val marker: String) : CancellationException(marker)

    @ParameterizedTest(name = "{0}: {1}, {2}", allowZeroInvocations = true)
    @MethodSource("cleanupBoundaries")
    fun testCleanupFailuresAreSuppressedInOrder(testDB: TestDB, mode: ExecutionMode, boundary: CleanupBoundary) {
        runBlocking {
            val primary = PrimaryFailure("statement execution failed")
            val failures = CleanupFailures(
                rollback = IllegalStateException("rollback failed").takeIf { boundary == CleanupBoundary.ROLLBACK || boundary == CleanupBoundary.ALL },
                statementClose = IllegalStateException("statement close failed").takeIf {
                    boundary == CleanupBoundary.STATEMENT_CLOSE || boundary == CleanupBoundary.ALL
                },
                connectionClose = IllegalStateException("connection close failed").takeIf {
                    boundary == CleanupBoundary.CONNECTION_CLOSE || boundary == CleanupBoundary.ALL
                },
                execution = primary
            )
            withDatabase(testDB, { failures }) { db ->
                val observed = assertFailsWith<PrimaryFailure> {
                    mode.execute(db) { exec("SELECT 1") }
                }
                assertSame(primary, observed)
                assertEquals(listOfNotNull(failures.rollback, failures.statementClose, failures.connectionClose), observed.suppressed.toList())
            }
        }
    }

    @ParameterizedTest(name = "{0}: {1}", allowZeroInvocations = true)
    @MethodSource("modes")
    fun testCommitFailureRetainsCleanupFailures(testDB: TestDB, mode: ExecutionMode) {
        runBlocking {
            val primary = PrimaryFailure("commit failed")
            val rollback = IllegalStateException("rollback failed")
            val statementClose = IllegalStateException("statement close failed")
            val close = IllegalStateException("connection close failed")
            withDatabase(testDB, { CleanupFailures(rollback, statementClose, close, commit = primary) }) { db ->
                val observed = assertFailsWith<PrimaryFailure> {
                    mode.execute(db) { exec("SELECT 1") }
                }
                assertSame(primary, observed)
                assertEquals(listOf(rollback, statementClose, close), observed.suppressed.toList())
            }
        }
    }

    @ParameterizedTest(name = "{0}: {1}", allowZeroInvocations = true)
    @MethodSource("modes")
    fun testCleanupCannotSuppressThePrimaryExceptionOnItself(testDB: TestDB, mode: ExecutionMode) {
        runBlocking {
            val primary = PrimaryFailure("primary failure")
            val failures = CleanupFailures(primary, primary, primary, primary)
            withDatabase(testDB, { failures }) { db ->
                val observed = assertFailsWith<PrimaryFailure> {
                    mode.execute(db) { exec("SELECT 1") }
                }
                assertSame(primary, observed)
                assertTrue(observed.suppressed.isEmpty())
            }
        }
    }

    @ParameterizedTest(name = "{0}: {1}", allowZeroInvocations = true)
    @MethodSource("modes")
    fun testSuccessfulTransactionStillReturnsWhenCleanupFails(testDB: TestDB, mode: ExecutionMode) {
        runBlocking {
            val failures = CleanupFailures(
                statementClose = IllegalStateException("statement close failed"),
                connectionClose = IllegalStateException("connection close failed")
            )
            withDatabase(testDB, { failures }) { db ->
                val result = mode.execute(db) {
                    exec("SELECT 1") { result ->
                        assertTrue(result.next())
                        result.getInt(1)
                    }
                }
                assertEquals(1, result)
            }
        }
    }

    @ParameterizedTest(name = "{0}: {1}, eventual success: {2}", allowZeroInvocations = true)
    @MethodSource("retryOutcomes")
    fun testRetriesKeepCleanupFailuresOnTheirOwnAttempt(testDB: TestDB, mode: ExecutionMode, eventualSuccess: Boolean) {
        runBlocking {
            val primaryFailures = listOf(PrimarySqlFailure("first attempt"), PrimarySqlFailure("second attempt"))
            val cleanupFailures = List(2) { attempt ->
                CleanupFailures(
                    rollback = IllegalStateException("rollback $attempt"),
                    statementClose = IllegalStateException("statement close $attempt"),
                    connectionClose = IllegalStateException("connection close $attempt")
                )
            }
            var attempts = 0
            val attemptLimit = if (eventualSuccess) 3 else 2
            withDatabase(testDB, { cleanupFailures.getOrElse(attempts) { CleanupFailures() } }) { db ->
                val body: JdbcTransaction.() -> Int = {
                    maxAttempts = attemptLimit
                    minRetryDelay = 0
                    maxRetryDelay = 0
                    exec("SELECT 1")
                    val attempt = attempts++
                    if (attempt < primaryFailures.size) throw primaryFailures[attempt]
                    42
                }
                if (eventualSuccess) {
                    assertEquals(42, mode.execute(db, body))
                } else {
                    assertSame(primaryFailures.last(), assertFailsWith<PrimarySqlFailure> { mode.execute(db, body) })
                }
                assertEquals(attemptLimit, attempts)
                primaryFailures.forEachIndexed { index, primary ->
                    val failures = cleanupFailures[index]
                    assertEquals(
                        listOf(failures.rollback, failures.rollback, failures.statementClose, failures.connectionClose),
                        primary.suppressed.toList()
                    )
                }
            }
        }
    }

    @ParameterizedTest(name = "{0}: {1}", allowZeroInvocations = true)
    @MethodSource("modes")
    fun testNestedTransactionCleanupPreservesTheOuterFailure(testDB: TestDB, mode: ExecutionMode) {
        runBlocking {
            for (useSavepoints in listOf(false, true)) {
                val primary = PrimaryFailure("nested transaction failed")
                val rollback = IllegalStateException("rollback failed")
                val close = IllegalStateException("connection close failed")
                withDatabase(testDB, { CleanupFailures(rollback = rollback, connectionClose = close) }, useSavepoints) { db ->
                    val observed = assertFailsWith<PrimaryFailure> {
                        when (mode) {
                            ExecutionMode.BLOCKING -> transaction(db) {
                                transaction(db) {
                                    exec("SELECT 1")
                                    throw primary
                                }
                            }
                            ExecutionMode.SUSPENDING -> suspendTransaction(db) {
                                suspendTransaction(db) {
                                    exec("SELECT 1")
                                    throw primary
                                }
                            }
                        }
                    }
                    assertSame(primary, observed)
                    val expected = if (useSavepoints) listOf(rollback, rollback, close) else listOf(rollback, close)
                    assertEquals(expected, observed.suppressed.toList())
                }
            }
        }
    }

    @ParameterizedTest(allowZeroInvocations = true)
    @MethodSource("databases")
    fun testCancellationRetainsCleanupFailures(testDB: TestDB) {
        runBlocking {
            val primary = PrimaryCancellation("transaction cancelled")
            val rollback = IllegalStateException("rollback failed")
            val statementClose = IllegalStateException("statement close failed")
            val close = IllegalStateException("connection close failed")
            withDatabase(testDB, { CleanupFailures(rollback, statementClose, close) }) { db ->
                val observed = assertFailsWith<PrimaryCancellation> {
                    coroutineScope {
                        val job = currentCoroutineContext().job
                        suspendTransaction(db) {
                            exec("SELECT 1")
                            job.cancel(primary)
                            currentCoroutineContext().ensureActive()
                        }
                    }
                }
                assertSame(primary, observed)
                assertEquals(listOf(rollback, statementClose, close), observed.suppressed.toList())
            }
        }
    }

    @ParameterizedTest(allowZeroInvocations = true)
    @MethodSource("databases")
    fun testCancellationBeforeRetryDelayRetainsClosingFailures(testDB: TestDB) {
        runBlocking {
            val primary = PrimaryCancellation("retry cancelled")
            val sqlFailure = PrimarySqlFailure("transaction failed")
            val rollback = IllegalStateException("rollback failed")
            val statementClose = IllegalStateException("statement close failed")
            val close = IllegalStateException("connection close failed")
            withDatabase(testDB, { CleanupFailures(rollback, statementClose, close) }) { db ->
                val observed = assertFailsWith<PrimaryCancellation> {
                    coroutineScope {
                        val job = currentCoroutineContext().job
                        suspendTransaction(db) {
                            maxAttempts = 2
                            minRetryDelay = 60_000
                            maxRetryDelay = 60_000
                            exec("SELECT 1")
                            job.cancel(primary)
                            throw sqlFailure
                        }
                    }
                }
                assertSame(primary, observed)
                assertEquals(listOf(rollback, rollback), sqlFailure.suppressed.toList())
                assertEquals(listOf(statementClose, close), observed.suppressed.toList())
            }
        }
    }

    private suspend fun <T> ExecutionMode.execute(db: Database, body: JdbcTransaction.() -> T): T {
        return when (this) {
            ExecutionMode.BLOCKING -> transaction(db) { body() }
            ExecutionMode.SUSPENDING -> suspendTransaction(db) { body() }
        }
    }

    private data class CleanupFailures(
        val rollback: Exception? = null,
        val statementClose: Exception? = null,
        val connectionClose: Exception? = null,
        val execution: Exception? = null,
        val commit: Exception? = null
    )

    private suspend fun withDatabase(
        testDB: TestDB,
        failures: () -> CleanupFailures,
        useNestedTransactions: Boolean = false,
        body: suspend (Database) -> Unit
    ) {
        val previousDatabase = TransactionManager.defaultDatabase
        Class.forName(testDB.driver)
        val database = Database.connect(
            getNewConnection = {
                val connection = DriverManager.getConnection(testDB.connection(), testDB.user, testDB.pass)
                FailingConnection(connection, failures())
            },
            databaseConfig = DatabaseConfig {
                explicitDialect = if (testDB == TestDB.H2_V2) H2Dialect() else PostgreSQLDialect()
                defaultMaxAttempts = 1
                this.useNestedTransactions = useNestedTransactions
            }
        )
        try {
            body(database)
        } finally {
            TransactionManager.closeAndUnregister(database)
            TransactionManager.defaultDatabase = previousDatabase
        }
    }

    private class FailingConnection(private val connection: Connection, private val failures: CleanupFailures) : Connection by connection {
        override fun commit() {
            failures.commit?.let { throw it }
            connection.commit()
        }

        override fun prepareStatement(sql: String, autoGeneratedKeys: Int): PreparedStatement {
            val statement = connection.prepareStatement(sql, autoGeneratedKeys)
            return object : PreparedStatement by statement {
                override fun executeQuery(): ResultSet {
                    val result = statement.executeQuery()
                    failures.execution?.let { throw it }
                    return result
                }

                override fun close() {
                    statement.close()
                    failures.statementClose?.let { throw it }
                }
            }
        }

        override fun rollback() {
            connection.rollback()
            failures.rollback?.let { throw it }
        }

        override fun rollback(savepoint: Savepoint) {
            connection.rollback(savepoint)
            failures.rollback?.let { throw it }
        }

        override fun close() {
            connection.close()
            failures.connectionClose?.let { throw it }
        }
    }
}
