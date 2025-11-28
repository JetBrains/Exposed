package org.jetbrains.exposed.v1.tests.shared.entities

import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.IdTable
import org.jetbrains.exposed.v1.core.dao.id.IntIdTable
import org.jetbrains.exposed.v1.dao.Entity
import org.jetbrains.exposed.v1.dao.ImmutableCachedEntityClass
import org.jetbrains.exposed.v1.dao.ImmutableEntityClass
import org.jetbrains.exposed.v1.dao.IntEntity
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import org.jetbrains.exposed.v1.tests.DatabaseTestsBase
import org.jetbrains.exposed.v1.tests.MISSING_R2DBC_TEST
import org.jetbrains.exposed.v1.tests.shared.assertEquals
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import kotlin.concurrent.thread
import kotlin.test.assertNotNull

@Tag(MISSING_R2DBC_TEST)
class ImmutableEntityTest : DatabaseTestsBase() {

    object Schema {
        object Organization : IdTable<Long>() {
            override val id = long("id").autoIncrement().entityId()
            val name = varchar("name", 256)
            val etag = long("etag").default(0)

            override val primaryKey = PrimaryKey(id)
        }
    }

    class EOrganization(id: EntityID<Long>) : Entity<Long>(id) {
        companion object : ImmutableEntityClass<Long, EOrganization>(Schema.Organization, EOrganization::class.java)

        val name by Schema.Organization.name
        val etag by Schema.Organization.etag
    }

    class ECachedOrganization(id: EntityID<Long>) : Entity<Long>(id) {
        companion object : ImmutableCachedEntityClass<Long, ECachedOrganization>(Schema.Organization, ECachedOrganization::class.java)

        val name by Schema.Organization.name
        val etag by Schema.Organization.etag
    }

    @Test
    fun immutableEntityReadAfterUpdate() {
        withTables(Schema.Organization, configure = { useNestedTransactions = true }) {
            transaction {
                Schema.Organization.insert {
                    it[name] = "JetBrains"
                    it[etag] = 0
                }
            }

            transaction {
                val org = EOrganization.all().single()

                EOrganization.forceUpdateEntity(org, Schema.Organization.etag, 1)

                assertEquals(1L, EOrganization.all().single().etag)
            }
        }
    }

    @Test
    fun immutableEntityCacheInvalidation() {
        withTables(Schema.Organization, configure = { useNestedTransactions = true }) {
            transaction {
                Schema.Organization.insert {
                    it[name] = "JetBrains"
                    it[etag] = 0
                }
            }

            transaction {
                Schema.Organization.update {
                    it[name] = "JetBrains Inc."
                }
            }

            transaction {
                val org = ECachedOrganization.all().single()

                ECachedOrganization.forceUpdateEntity(org, Schema.Organization.name, "JetBrains Gmbh")

                Schema.Organization.update {
                    it[etag] = 1
                }

                // Populate _cachedValues in ImmutableCachedEntityClass with inconsistent entity value
                ECachedOrganization.all().single()
            }

            transaction {
                val org = ECachedOrganization.all().single()

                assertEquals("JetBrains Gmbh", org.name)
                assertEquals(1L, org.etag)
            }
        }
    }

    object ImmutableCacheTable : IntIdTable("immutable_entity_cache") {
        val name = text("name")
    }

    class ImmutableCacheEntity(id: EntityID<Int>) : IntEntity(id) {
        var name by ImmutableCacheTable.name

        companion object : ImmutableCachedEntityClass<Int, ImmutableCacheEntity>(ImmutableCacheTable)
    }

    @Test
    fun testConcurrentAccessToTheImmutableEntityCache() {
        // The reason for the test is the concurrent access to the cache.
        // It's possible that the field ImmutableCachedEntityClass::_cachedValues would be access and parallel,
        // some thread could clean and, and some set new values.
        withTables(ImmutableCacheTable) {
            // 100k iteration is enough to reproduce the problem.
            // Usually it happens in the first 100 iterations, sometimes first 10k iterations.
            var iterationsLimit = 100_000
            val entityName = "Concurrent access to the cache"

            ImmutableCacheTable.insert {
                it[name] = entityName
            }

            val t = thread(start = true, isDaemon = true) {
                while (iterationsLimit > 0) {
                    ImmutableCacheEntity.expireCache()
                }
            }

            while (iterationsLimit > 0) {
                try {
                    iterationsLimit--
                    val entity = ImmutableCacheEntity.all().firstOrNull()
                    assertNotNull(entity)
                    assertEquals(entityName, entity.name)
                } catch (e: Exception) {
                    iterationsLimit = 0
                    t.join()
                    throw e
                }
            }
        }
    }
}
