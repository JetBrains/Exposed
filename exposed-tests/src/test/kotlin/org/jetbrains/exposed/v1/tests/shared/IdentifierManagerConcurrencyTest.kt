package org.jetbrains.exposed.v1.tests.shared

import org.jetbrains.exposed.v1.core.statements.api.IdentifierManagerApi
import org.jetbrains.exposed.v1.tests.DatabaseTestsBase
import org.junit.jupiter.api.Test
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.assertTrue

/**
 * Regression test for issue #1704.
 *
 * A single [IdentifierManagerApi] is shared across every transaction / coroutine on a Database,
 * so its 5 internal identifier caches are accessed concurrently during SQL preparation. Before
 * the fix those caches were plain [java.util.LinkedHashMap] instances and once a bucket grew past
 * 8 entries `HashMap.treeifyBin` would throw `ClassCastException` under contention. This test
 * pulls the live [IdentifierManagerApi] off a real database and hammers all four cache-backed
 * entry points from a thread pool, failing if any worker sees an exception.
 *
 * The concurrent section runs outside any transaction, which is representative: the identifier
 * manager is a process-wide singleton on the Database and is routinely read from background
 * threads (DataLoader batches, async statement preparation) that don't own the current
 * transaction thread-local.
 */
class IdentifierManagerConcurrencyTest : DatabaseTestsBase() {

    @Test
    fun identifierManagerCachesSurviveConcurrentResolution() {
        withDb { testDb ->
            val manager: IdentifierManagerApi = db.identifierManager
            // Prime the `keywords` / `shouldPreserveKeywordCasing` lazies from this transaction so
            // worker threads can safely read them without a transaction context.
            manager.needQuotes("warmup")
            manager.shouldQuoteIdentifier("warmup")
            manager.inProperCase("warmup")
            manager.quoteIfNecessary("warmup")

            // Use unique keys per task so every worker is populating the cache (not just reading
            // already-cached entries). A read-only workload never races on `put`, so the original
            // LinkedHashMap-based cache would appear safe; to reliably reproduce #1704 the caches
            // must be under constant mutation pressure.
            val pool = Executors.newFixedThreadPool(16)
            val errors = ConcurrentLinkedQueue<Throwable>()
            try {
                val futures = (0 until 2000).map { taskId ->
                    pool.submit {
                        try {
                            repeat(200) { i ->
                                val id = "col_${taskId}_$i"
                                manager.needQuotes(id)
                                manager.inProperCase(id)
                                manager.quoteIfNecessary(id)
                                manager.shouldQuoteIdentifier(id)
                                // Tokens containing `-` are not valid unquoted identifiers, so
                                // `quoteTokenIfNecessary` routes them through `quote(...)` and
                                // populates `quotedIdentifiersCache` — exercise the 5th cache.
                                manager.quoteIfNecessary("col-$taskId-$i")
                            }
                        } catch (t: Throwable) {
                            errors += t
                        }
                    }
                }
                futures.forEach { it.get(120, TimeUnit.SECONDS) }
            } finally {
                pool.shutdownNow()
            }
            assertTrue(
                errors.isEmpty(),
                "Expected no concurrency errors on $testDb, got ${errors.size}: ${errors.firstOrNull()}"
            )
        }
    }
}
