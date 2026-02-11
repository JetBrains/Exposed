package org.jetbrains.exposed.v1.tests.shared.entities

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.IntIdTable
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.dao.IntEntity
import org.jetbrains.exposed.v1.dao.IntEntityClass
import org.jetbrains.exposed.v1.jdbc.*
import org.jetbrains.exposed.v1.jdbc.transactions.inTopLevelTransaction
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.tests.DatabaseTestsBase
import org.jetbrains.exposed.v1.tests.MISSING_R2DBC_TEST
import org.jetbrains.exposed.v1.tests.TestDB
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.sql.Connection
import kotlin.test.assertEquals

/**
 * Tests for GitHub issue #1527: SELECT FOR UPDATE should return fresh data, not cached entities.
 *
 * The bug was that wrapRow() would return cached entities even when SELECT FOR UPDATE
 * was used to retrieve fresh data from the database. The fix ensures that entity._readValues
 * is always updated with the current ResultRow data.
 *
 * @see <a href="https://github.com/JetBrains/Exposed/issues/1527">GitHub Issue #1527</a>
 */
@Tag(MISSING_R2DBC_TEST)
class EntityCacheRefreshTests : DatabaseTestsBase() {
    // Skip databases that don't support SELECT FOR UPDATE
    val excludedDbs = listOf(TestDB.SQLITE, TestDB.SQLSERVER)

    object TestTable : IntIdTable("entity_cache_refresh_test") {
        val value = integer("value")
    }

    class TestEntity(id: EntityID<Int>) : IntEntity(id) {
        var value by TestTable.value

        companion object : IntEntityClass<TestEntity>(TestTable)
    }

    // Extended table for testing partial SELECT with multiple columns
    object ExtendedTestTable : IntIdTable("extended_test") {
        val value = integer("value")
        val name = varchar("name", 50)
    }

    class ExtendedTestEntity(id: EntityID<Int>) : IntEntity(id) {
        var value by ExtendedTestTable.value
        var name by ExtendedTestTable.name

        companion object : IntEntityClass<ExtendedTestEntity>(ExtendedTestTable)
    }

    /**
     * This test reproduces the original bug report where multiple threads
     * concurrently increment a counter using SELECT FOR UPDATE. Without the fix,
     * cached entities would cause lost updates.
     *
     * Expected: All increments should be applied correctly, resulting in final value equal to thread count.
     */
    @Test
    fun testConcurrentIncrementsWithSelectForUpdate() {
        if (dialect in excludedDbs) {
            Assumptions.assumeFalse(true)
        }

        withTables(TestTable) {
            val db = dialect.connect()

            // Create a single entity with initial value 0
            val entityIdValue = transaction(db = db) {
                TestEntity.new { value = 0 }.id.value
            }

            val threadCount = 20

            runBlocking(Dispatchers.IO) {
                List(threadCount) {
                    launch {
                        transaction(db = db, transactionIsolation = Connection.TRANSACTION_READ_COMMITTED) {
                            // This is important line, because it forces DAO to cache
                            // the value from the beginning of transaction
                            TestEntity.find { TestTable.id eq entityIdValue }.single()

                            val entity = TestEntity.find { TestTable.id eq entityIdValue }
                                .forUpdate()
                                .single()

                            val currentValue = entity.value

                            entity.value = currentValue + 1
                        }
                    }
                }.forEach { it.join() }
            }

            // Verify all increments were applied
            transaction(db = db) {
                val finalEntity = TestEntity[entityIdValue]
                assertEquals(
                    threadCount,
                    finalEntity.value,
                    "Expected value to be $threadCount after $threadCount concurrent increments, " +
                        "but got ${finalEntity.value}. This indicates lost updates due to stale cache."
                )
            }
        }
    }

    /**
     * Scenario:
     * 1. Transaction T1 reads entity with value A (entity gets cached)
     * 2. Transaction T2 updates entity value to B and commits
     * 3. Transaction T1 performs SELECT FOR UPDATE on the same entity
     * 4. Transaction T1 should see value B, not cached value A
     */
    @Test
    fun testSelectForUpdateReturnsCurrentData() {
        // Skip databases that don't support SELECT FOR UPDATE
        if (dialect in excludedDbs) {
            Assumptions.assumeFalse(true)
        }

        withTables(TestTable) {
            val db1 = dialect.connect()
            val db2 = dialect.connect()

            val entityId = transaction(db = db1) {
                TestEntity.new { value = 100 }.id
            }

            transaction(db = db1, transactionIsolation = Connection.TRANSACTION_READ_COMMITTED) {
                val entity = TestEntity[entityId]
                assertEquals(100, entity.value, "Initial value should be 100")

                // In a separate transaction, update the value
                transaction(db = db2) {
                    val entity2 = TestEntity[entityId]
                    entity2.value = 200
                }

                val entityWithForUpdate = TestEntity.find { TestTable.id eq entityId.value }
                    .forUpdate()
                    .single()

                assertEquals(
                    200,
                    entityWithForUpdate.value,
                    "SELECT FOR UPDATE should return fresh data (200), not cached data (100)"
                )

                assertEquals(
                    entity.id.value,
                    entityWithForUpdate.id.value,
                    "Should be the same entity instance"
                )
            }
        }
    }

    /**
     * This test verifies that when an entity is first accessed via TestEntity[id]
     * (which caches it), and then accessed again via SELECT FOR UPDATE, the fresh
     * data is returned even though the entity is already in cache.
     */
    @Test
    fun testEntityByIdThenSelectForUpdateSeesFreshData() {
        // Skip databases that don't support SELECT FOR UPDATE
        if (dialect in excludedDbs) {
            Assumptions.assumeFalse(true)
        }

        withTables(TestTable) {
            val db1 = dialect.connect()
            val db2 = dialect.connect()

            val entityId = transaction(db = db1) {
                TestEntity.new { value = 50 }.id
            }

            transaction(db = db1, transactionIsolation = Connection.TRANSACTION_READ_COMMITTED) {
                // Access by ID (caches entity)
                val entityById = TestEntity[entityId]
                assertEquals(50, entityById.value)

                // Another transaction updates
                inTopLevelTransaction(db = db2) {
                    TestEntity[entityId].value = 75
                }

                // Access same entity via SELECT FOR UPDATE
                val entityForUpdate = TestEntity.find { TestTable.id eq entityId.value }
                    .forUpdate()
                    .single()

                // Should see fresh value 75, not cached 50
                assertEquals(
                    75,
                    entityForUpdate.value,
                    "Entity accessed via SELECT FOR UPDATE should have fresh value (75), not cached (50)"
                )

                // Should be the same entity object (same cache entry)
                assertEquals(entityById.id.value, entityForUpdate.id.value)
            }
        }
    }

    /**
     * This test verifies that when users manually create a partial SELECT
     * (selecting only some columns) and call wrapRow(), the method performs a selective merge:
     * - Columns in the partial SELECT are updated with fresh data
     * - Columns not in the partial SELECT retain their previously cached values
     *
     * This ensures that manual DSL queries combined with wrapRow() work correctly.
     */
    @Test
    fun testManualPartialSelectMergesWithCachedColumns() {
        withTables(ExtendedTestTable) {
            val entityId = ExtendedTestEntity.new {
                value = 100
                name = "Original"
            }.id
            commit()

            // Load entity fully (all columns cached)
            val fullEntity = ExtendedTestEntity[entityId]
            assertEquals(100, fullEntity.value)
            assertEquals("Original", fullEntity.name)

            val db2 = db
            inTopLevelTransaction(db = db2) {
                ExtendedTestTable.update({ ExtendedTestTable.id eq entityId }) {
                    it[value] = 200
                }
            }

            val partialResults = ExtendedTestTable
                .select(ExtendedTestTable.id)
                .where { ExtendedTestTable.id eq entityId.value }
                .map { row -> ExtendedTestEntity.wrapRow(row) }

            val entityFromPartial = partialResults.single()

            // Should see old value, because the new value is not fetched from the query
            assertEquals(
                100,
                entityFromPartial.value,
                "Value should be updated from partial SELECT"
            )

            assertEquals(
                "Original",
                entityFromPartial.name,
                "Name should still be accessible from cached data (not in partial SELECT)"
            )

            assertEquals(entityId.value, entityFromPartial.id.value)
        }
    }
}
