package org.jetbrains.exposed.v1.r2dbc.sql.tests.shared

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.v1.r2dbc.R2dbcDatabase
import org.jetbrains.exposed.v1.r2dbc.R2dbcDatabaseConfig
import org.jetbrains.exposed.v1.r2dbc.tests.R2dbcDatabaseTestsBase
import org.jetbrains.exposed.v1.r2dbc.tests.TestDB
import org.jetbrains.exposed.v1.r2dbc.tests.getInt
import org.jetbrains.exposed.v1.r2dbc.transactions.TransactionManager
import org.jetbrains.exposed.v1.r2dbc.transactions.suspendTransaction
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.Test
import kotlin.system.measureTimeMillis
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

class StatementCancellationTest : R2dbcDatabaseTestsBase() {

    private fun connect(pooled: Boolean, cancelOnCancellation: Boolean = true): R2dbcDatabase {
        val url = dialect.connection().let {
            if (pooled) it.replaceFirst("r2dbc:postgresql:", "r2dbc:pool:postgresql:") else it
        }
        return R2dbcDatabase.connect(
            databaseConfig = R2dbcDatabaseConfig {
                defaultMaxAttempts = 1
                cancelRunningStatementOnCancellation = cancelOnCancellation
                setUrl(url)
            }
        )
    }

    /** Launches a transaction running a long statement, cancels it once the statement is executing, and times the join. */
    private suspend fun cancelLongRunningStatement(db: R2dbcDatabase): Long {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val job = scope.launch {
            suspendTransaction(db = db) { exec("SELECT pg_sleep(10)") }
        }
        delay(1_000.milliseconds)

        return measureTimeMillis { job.cancelAndJoin() }
    }

    private suspend fun sleepingBackends(db: R2dbcDatabase): Int = suspendTransaction(db = db) {
        exec(
            "SELECT count(*) FROM pg_stat_activity " +
                "WHERE state = 'active' AND position(chr(112) || 'g_sleep' in query) > 0 " +
                "AND position('pg_stat_activity' in query) = 0"
        ) { it.getInt(1) }?.let { flow ->
            var count = 0
            flow.collect { count = it ?: 0 }
            count
        } ?: 0
    }

    @Test
    fun testCancellationAbortsRunningStatement() {
        Assumptions.assumeTrue(dialect in TestDB.ALL_POSTGRES)

        runBlocking {
            val db = connect(pooled = false)
            try {
                val elapsed = cancelLongRunningStatement(db)

                assertTrue(elapsed < 5_000, "Expected cancellation to abort the running statement, but join took ${elapsed}ms")
                assertEquals(0, sleepingBackends(db))
            } finally {
                TransactionManager.closeAndUnregister(db)
            }
        }
    }

    @Test
    fun testCancellationAbortsRunningStatementOnPooledConnection() {
        Assumptions.assumeTrue(dialect in TestDB.ALL_POSTGRES)

        runBlocking {
            val db = connect(pooled = true)
            try {
                val elapsed = cancelLongRunningStatement(db)

                assertTrue(elapsed < 5_000, "Expected cancellation to abort the running statement, but join took ${elapsed}ms")
                assertEquals(0, sleepingBackends(db))

                // the connection returned to the pool is still usable, and is not affected by the earlier cancel request
                val result = suspendTransaction(db = db) {
                    exec("SELECT pg_sleep(1)")
                    exec("SELECT 1") { it.getInt(1) }?.let { flow ->
                        var value = 0
                        flow.collect { value = it ?: 0 }
                        value
                    }
                }
                assertEquals(1, result)
            } finally {
                TransactionManager.closeAndUnregister(db)
            }
        }
    }

    @Test
    fun testCancellationWaitsForStatementWhenDisabled() {
        Assumptions.assumeTrue(dialect in TestDB.ALL_POSTGRES)

        runBlocking {
            val db = connect(pooled = false, cancelOnCancellation = false)
            try {
                val elapsed = cancelLongRunningStatement(db)

                assertTrue(elapsed > 5_000, "Expected cancellation to wait for the running statement, but join took ${elapsed}ms")
            } finally {
                TransactionManager.closeAndUnregister(db)
            }
        }
    }

    @Test
    fun testCancellationWithoutRunningStatement() {
        Assumptions.assumeTrue(dialect in TestDB.ALL_POSTGRES)

        runBlocking {
            val db = connect(pooled = true)
            try {
                val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
                val job = scope.launch {
                    suspendTransaction(db = db) {
                        exec("SELECT 1")
                        delay(10_000.milliseconds)
                    }
                }
                delay(1_000.milliseconds)

                val elapsed = measureTimeMillis { job.cancelAndJoin() }
                assertTrue(elapsed < 5_000, "Expected prompt cancellation, but join took ${elapsed}ms")

                // no statement was running, so no cancel request should have been able to affect the next statement
                suspendTransaction(db = db) { exec("SELECT pg_sleep(2)") }
            } finally {
                TransactionManager.closeAndUnregister(db)
            }
        }
    }
}
