package org.jetbrains.exposed.v1.tests.h2

import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.tests.LogDbInTestName
import org.jetbrains.exposed.v1.tests.NOT_APPLICABLE_TO_R2DBC
import org.jetbrains.exposed.v1.tests.TestDB
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.sql.DriverManager
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Regression test for issue #2813.
 *
 * The [Database] capability flags [Database.supportsAlterTableWithAddColumn],
 * [Database.supportsAlterTableWithDropColumn], [Database.supportsMultipleResultSets] and
 * [Database.supportsSelectForUpdate] were declared with `lazy(LazyThreadSafetyMode.NONE)`.
 * A `Database` is shared across every transaction / coroutine, so when several threads trigger
 * the first read at once, `UnsafeLazyImpl` has no memory barrier: it runs the initializer on
 * every racing thread (and, on weak memory models such as ARM, can throw `NullPointerException`).
 * Each initializer opens a metadata connection, so an unsafe flag turns a startup burst into a
 * connection storm.
 *
 * This test connects a `Database` whose initializer counts connections, warms up the unrelated
 * lazies, then races the first read of the four flags from a thread pool. A thread-safe flag must
 * initialize exactly once, i.e. open exactly one connection per flag, no matter how many threads
 * race the first access.
 */
@Tag(NOT_APPLICABLE_TO_R2DBC)
class DatabaseMetadataConcurrencyTest : LogDbInTestName() {

    @Test
    fun testConcurrentFirstAccessInitializesFlagsOnce() {
        Assumptions.assumeTrue(dialect == TestDB.H2_V2.name)

        val threadCount = 32
        val connections = AtomicInteger(0)
        val delayConnections = AtomicBoolean(false)
        val db = Database.connect(
            getNewConnection = {
                connections.incrementAndGet()
                // Widen the initialization window so an unsafe (LazyThreadSafetyMode.NONE) flag is
                // observably initialized by many racing threads instead of exactly one.
                if (delayConnections.get()) Thread.sleep(WINDOW_MILLIS)
                DriverManager.getConnection("jdbc:h2:mem:metadataRace;DB_CLOSE_DELAY=-1;", "root", "")
            }
        )
        // Initialize every lazy that isn't under test so only the four flags open connections
        // once the workers are released.
        db.dialect

        val start = CountDownLatch(1)
        val done = CountDownLatch(threadCount)
        val errors = ConcurrentLinkedQueue<Throwable>()
        val observed = ConcurrentLinkedQueue<List<Boolean>>()
        val pool = Executors.newFixedThreadPool(threadCount)
        try {
            repeat(threadCount) {
                pool.execute {
                    try {
                        start.await()
                        observed += listOf(
                            db.supportsAlterTableWithAddColumn,
                            db.supportsAlterTableWithDropColumn,
                            db.supportsMultipleResultSets,
                            db.supportsSelectForUpdate,
                        )
                    } catch (cause: Throwable) {
                        errors += cause
                    } finally {
                        done.countDown()
                    }
                }
            }
            delayConnections.set(true)
            connections.set(0)
            start.countDown()
            assertTrue(done.await(1, TimeUnit.MINUTES), "Workers did not finish")
        } finally {
            pool.shutdownNow()
        }

        assertTrue(errors.isEmpty(), "Concurrent first access threw: ${errors.firstOrNull()}")
        assertEquals(1, observed.toSet().size, "Workers observed inconsistent flags: ${observed.toSet()}")
        assertEquals(
            FLAG_COUNT,
            connections.get(),
            "Each capability flag must initialize exactly once, but the four flags opened ${connections.get()} connections"
        )
    }

    private companion object {
        const val FLAG_COUNT = 4
        const val WINDOW_MILLIS = 25L
    }
}
