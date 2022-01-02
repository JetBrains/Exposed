package org.jetbrains.exposed.sql.tests.shared.entities

import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.entityCache
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.tests.DatabaseTestsBase
import org.jetbrains.exposed.sql.tests.TestDB
import org.jetbrains.exposed.sql.tests.shared.assertEqualCollections
import org.jetbrains.exposed.sql.tests.shared.assertEquals
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.Assume
import org.junit.Test
import kotlin.random.Random

class EntityCacheTests : DatabaseTestsBase() {

    object TestTable : IntIdTable("TestCache") {
        val value = integer("value")
    }

    class TestEntity(id: EntityID<Int>) : IntEntity(id) {
        var value by TestTable.value

        companion object : IntEntityClass<TestEntity>(TestTable)
    }

    @Test
    fun testGlobalEntityCacheLimit() {
        Assume.assumeTrue(TestDB.H2 in TestDB.enabledInTests())
        val entitiesCount = 25
        val cacheSize = 10
        val db = TestDB.H2.connect {
            maxEntitiesToStoreInCachePerEntity = cacheSize
        }

        transaction(db) {
            try {
                SchemaUtils.create(TestTable)

                repeat(entitiesCount) {
                    TestEntity.new {
                        value = Random.nextInt()
                    }
                }

                val allEntities = TestEntity.all().toList()
                assertEquals(entitiesCount, allEntities.size)
                val allCachedEntities = entityCache.findAll(TestEntity)
                assertEquals(cacheSize, allCachedEntities.size)
                assertEqualCollections(allEntities.drop(entitiesCount - cacheSize), allCachedEntities)
            } finally {
                SchemaUtils.drop(TestTable)
            }
        }
    }

    @Test
    fun testGlobalEntityCacheLimitZero() {
        Assume.assumeTrue(TestDB.H2 in TestDB.enabledInTests())
        val entitiesCount = 25
        val db = TestDB.H2.connect()
        val dbNoCache = TestDB.H2.connect {
            maxEntitiesToStoreInCachePerEntity = 10
        }

        val entityIds = transaction(db) {
            SchemaUtils.create(TestTable)

            repeat(entitiesCount) {
                TestEntity.new {
                    value = Random.nextInt()
                }
            }
            val entityIds = TestTable.selectAll().map { it[TestTable.id] }
            val initialStatementCount = statementCount
            entityIds.forEach {
                TestEntity[it]
            }
            // All read from cache
            assertEquals(initialStatementCount, statementCount)

            entityCache.clear()
            // Load all into cache
            TestEntity.all().toList()

            entityIds.forEach {
                TestEntity[it]
            }
            assertEquals(initialStatementCount + 1, statementCount)
            entityIds
        }

        transaction(dbNoCache) {
            debug = true
            TestEntity.all().toList()
            assertEquals(1, statementCount)
            val initialStatementCount = statementCount
            entityIds.forEach {
                TestEntity[it]
            }
            assertEquals(initialStatementCount + entitiesCount, statementCount)
            SchemaUtils.drop(TestTable)
        }
    }

    @Test
    fun testPerTransactionEntityCacheLimit() {
        val entitiesCount = 25
        val cacheSize = 10
        withTables(TestTable) {
            entityCache.maxEntitiesToStore = 10

            repeat(entitiesCount) {
                TestEntity.new {
                    value = Random.nextInt()
                }
            }

            val allEntities = TestEntity.all().toList()
            assertEquals(entitiesCount, allEntities.size)
            val allCachedEntities = entityCache.findAll(TestEntity)
            assertEquals(cacheSize, allCachedEntities.size)
            assertEqualCollections(allEntities.drop(entitiesCount - cacheSize), allCachedEntities)
        }
    }

    @Test
    fun `change EntityCache maxEntitiesToStore in the middle of transaction`() {
        withTables(TestTable) {
            repeat(20) {
                TestEntity.new {
                    value = Random.nextInt()
                }
            }
            entityCache.clear()

            TestEntity.all().limit(15).toList()
            assertEquals(15, entityCache.findAll(TestEntity).size)

            entityCache.maxEntitiesToStore = 18
            TestEntity.all().toList()
            assertEquals(18, entityCache.findAll(TestEntity).size)

            // Resize current cache
            entityCache.maxEntitiesToStore = 10
            assertEquals(10, entityCache.findAll(TestEntity).size)

            entityCache.maxEntitiesToStore = 18
            TestEntity.all().toList()
            assertEquals(18, entityCache.findAll(TestEntity).size)

            // Disable cache
            entityCache.maxEntitiesToStore = 0
            assertEquals(0, entityCache.findAll(TestEntity).size)
        }
    }

    @Test
    fun `EntityCache should not be cleaned on explicit commit` () {
        withTables(TestTable) {
            val entity = TestEntity.new {
                value = Random.nextInt()
            }
            assertEquals(entity, TestEntity.testCache(entity.id))
            commit()
            assertEquals(entity, TestEntity.testCache(entity.id))
        }
    }
}
