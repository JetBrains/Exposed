package org.jetbrains.exposed.v1.tests.shared.entities

import nl.altindag.log.LogCaptor
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.IntIdTable
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.exposedLogger
import org.jetbrains.exposed.v1.dao.EntityChange
import org.jetbrains.exposed.v1.dao.EntityChangeType
import org.jetbrains.exposed.v1.dao.EntityHook
import org.jetbrains.exposed.v1.dao.IntEntity
import org.jetbrains.exposed.v1.dao.IntEntityClass
import org.jetbrains.exposed.v1.dao.flushCache
import org.jetbrains.exposed.v1.dao.toEntity
import org.jetbrains.exposed.v1.jdbc.update
import org.jetbrains.exposed.v1.tests.DatabaseTestsBase
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Regression tests for [issue 2443](https://github.com/JetBrains/Exposed/issues/2443):
 * an [EntityHook] subscriber that resolves an event via [toEntity] and mutates the returned entity
 * must operate on the instance that was flushed, so that writes performed through instances held by
 * the application do not trigger the "Entity instance in cache differs from the provided" warning.
 */
class EntityHookSubscriberMutationTest : DatabaseTestsBase() {

    object Artists : IntIdTable() {
        val name = varchar("name", 50)
        val updatedAt = long("updated_at")
    }

    class Artist(id: EntityID<Int>) : IntEntity(id) {
        companion object : IntEntityClass<Artist>(Artists)

        var name by Artists.name
        var updatedAt by Artists.updatedAt
    }

    val allTables = arrayOf(Artists)

    private companion object {
        const val hookTimestamp = 100L
        const val applicationTimestamp = 200L
    }

    private fun mismatchLogs(logCaptor: LogCaptor): List<String> =
        logCaptor.errorLogs.filter { it.contains("Entity instance in cache differs from the provided") }

    private fun subscribeUpdateHook(): (EntityChange) -> Unit = EntityHook.subscribe { change ->
        if (change.changeType == EntityChangeType.Updated && change.entityClass == Artist) {
            change.toEntity(Artist)?.updatedAt = hookTimestamp
        }
    }

    /** A subscriber that stamps `updatedAt` on a plain DAO update must not log a cache mismatch. */
    @Test
    fun testSubscriberMutationOnDaoUpdate() {
        withTables(*allTables) {
            val created = Artist.new {
                name = "a"
                updatedAt = 0
            }
            flushCache()

            val logCaptor = LogCaptor.forName(exposedLogger.name)
            val hook = subscribeUpdateHook()
            try {
                val artist = Artist.findById(created.id)!!
                artist.name = "renamed"
                commit()

                assertTrue(mismatchLogs(logCaptor).isEmpty())
                assertEquals(hookTimestamp, Artist.findById(created.id)?.updatedAt)
            } finally {
                EntityHook.unsubscribe(hook)
                logCaptor.close()
            }
        }
    }

    /**
     * After a DSL statement has invalidated the cached entities of a table, an event subscriber must
     * still resolve the flushed instance, and writes through instances the application still holds
     * must not be reported as a cache mismatch.
     */
    @Test
    fun testSubscriberMutationAfterCacheInvalidation() {
        withTables(*allTables) {
            val created = Artist.new {
                name = "a"
                updatedAt = 0
            }
            flushCache()

            val logCaptor = LogCaptor.forName(exposedLogger.name)
            val hook = subscribeUpdateHook()
            try {
                val artist = Artist.findById(created.id)!!
                artist.name = "dao-write"

                // A DSL update invalidates every cached instance of the table, so the subscriber runs
                // against an emptied cache.
                Artists.update({ Artists.id eq artist.id }) {
                    it[Artists.name] = "dsl-write"
                }
                assertTrue(mismatchLogs(logCaptor).isEmpty())

                artist.updatedAt = applicationTimestamp
                assertTrue(mismatchLogs(logCaptor).isEmpty())

                commit()
                assertTrue(mismatchLogs(logCaptor).isEmpty())

                // The subscriber and the application worked on the same instance, which remained the
                // cached one; the hook re-stamps the value when processing the commit-time flush.
                assertSame(artist, Artist.findById(created.id))
                assertEquals(hookTimestamp, artist.updatedAt)
            } finally {
                EntityHook.unsubscribe(hook)
                logCaptor.close()
            }
        }
    }

    /**
     * A write through an instance that was replaced in the cache by an unrelated load is still
     * reported, so the check keeps flagging genuinely divergent entity instances.
     */
    @Test
    fun testWriteThroughEntityReplacedInCacheIsStillReported() {
        withTables(*allTables) {
            val created = Artist.new {
                name = "a"
                updatedAt = 0
            }
            flushCache()

            val logCaptor = LogCaptor.forName(exposedLogger.name)
            try {
                val artist = Artist.findById(created.id)!!
                // reload() removes the instance from the cache and caches a fresh instance for the id.
                Artist.reload(artist)

                artist.updatedAt = applicationTimestamp
                assertEquals(1, mismatchLogs(logCaptor).size)
            } finally {
                logCaptor.close()
            }
        }
    }
}
