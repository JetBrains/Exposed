package org.jetbrains.exposed.dao.r2dbc.tests.shared

import io.r2dbc.spi.IsolationLevel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.single
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.r2dbc.dao.IntR2dbcEntity
import org.jetbrains.exposed.r2dbc.dao.IntR2dbcEntityClass
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.IntIdTable
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.r2dbc.select
import org.jetbrains.exposed.v1.r2dbc.tests.R2dbcDatabaseTestsBase
import org.jetbrains.exposed.v1.r2dbc.tests.TestDB
import org.jetbrains.exposed.v1.r2dbc.transactions.inTopLevelSuspendTransaction
import org.jetbrains.exposed.v1.r2dbc.transactions.suspendTransaction
import org.jetbrains.exposed.v1.r2dbc.update
import org.junit.jupiter.api.Assumptions
import kotlin.test.Test
import kotlin.test.assertEquals

class R2dbcEntityCacheRefreshTests : R2dbcDatabaseTestsBase() {
    val excludedDbs = listOf(TestDB.SQLSERVER)

    object TestTable : IntIdTable("entity_cache_refresh_test") {
        val value = integer("value")
    }

    class TestEntity(id: EntityID<Int>) : IntR2dbcEntity(id) {
        var value by TestTable.value

        companion object : IntR2dbcEntityClass<TestEntity>(TestTable)
    }

    // Extended table for testing partial SELECT with multiple columns
    object ExtendedTestTable : IntIdTable("extended_test") {
        val value = integer("value")
        val name = varchar("name", 50)
    }

    class ExtendedTestEntity(id: EntityID<Int>) : IntR2dbcEntity(id) {
        var value by ExtendedTestTable.value
        var name by ExtendedTestTable.name

        companion object : IntR2dbcEntityClass<ExtendedTestEntity>(ExtendedTestTable)
    }

    @Test
    fun testConcurrentIncrementsWithSelectForUpdate() {
        if (dialect in excludedDbs) {
            Assumptions.assumeFalse(true)
        }

        withTables(TestTable) {
            val db = dialect.connect()

            // Create a single entity with initial value 0
            val entityIdValue = suspendTransaction(db = db) {
                val entity = TestEntity.new { value = 0 }

                entity.flush()

                entity.id.value
            }

            val threadCount = 20

            runBlocking(Dispatchers.IO) {
                List(threadCount) {
                    launch {
                        suspendTransaction(db = db, transactionIsolation = IsolationLevel.READ_COMMITTED) {
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
            suspendTransaction(db = db) {
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

            val entityId = suspendTransaction(db = db1) {
                TestEntity.new { value = 100 }.id
            }

            suspendTransaction(db = db1, transactionIsolation = IsolationLevel.READ_COMMITTED) {
                val entity = TestEntity[entityId]
                assertEquals(100, entity.value, "Initial value should be 100")

                // In a separate transaction, update the value
                suspendTransaction(db = db2) {
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
            inTopLevelSuspendTransaction(db = db2) {
                ExtendedTestTable.update({ ExtendedTestTable.id eq entityId }) {
                    it[value] = 200
                    it[name] = "Updated"
                }
            }

            val partialResults = ExtendedTestTable
                .select(ExtendedTestTable.id, ExtendedTestTable.value)
                .where { ExtendedTestTable.id eq entityId.value }
                .map { row -> ExtendedTestEntity.wrapRow(row) }

            val entityFromPartial = partialResults.single()

            // Should see new value, because the new value was fetched from the query
            assertEquals(
                200,
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
