package org.jetbrains.exposed.v1.sql.tests.shared.entities

import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.dao.IntEntity
import org.jetbrains.exposed.v1.dao.IntEntityClass
import org.jetbrains.exposed.v1.dao.entityCache
import org.jetbrains.exposed.v1.dao.flushCache
import org.jetbrains.exposed.v1.dao.id.EntityID
import org.jetbrains.exposed.v1.dao.id.IdTable
import org.jetbrains.exposed.v1.dao.id.IntIdTable
import org.jetbrains.exposed.v1.sql.SchemaUtils
import org.jetbrains.exposed.v1.sql.selectAll
import org.jetbrains.exposed.v1.sql.tests.DatabaseTestsBase
import org.jetbrains.exposed.v1.sql.tests.TestDB
import org.jetbrains.exposed.v1.sql.tests.shared.assertEqualCollections
import org.jetbrains.exposed.v1.sql.tests.shared.assertEquals
import org.jetbrains.exposed.v1.sql.transactions.transaction
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
        Assume.assumeTrue(TestDB.H2_V2 in TestDB.enabledDialects())
        val entitiesCount = 25
        val cacheSize = 10
        val db = TestDB.H2_V2.connect {
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
        Assume.assumeTrue(TestDB.H2_V2 in TestDB.enabledDialects())
        val entitiesCount = 25
        val db = TestDB.H2_V2.connect()
        val dbNoCache = TestDB.H2_V2.connect {
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
    fun changeEntityCacheMaxEntitiesToStoreInMiddleOfTransaction() {
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
    fun `EntityCache should not be cleaned on explicit commit`() {
        withTables(TestTable) {
            val entity = TestEntity.new {
                value = Random.nextInt()
            }
            assertEquals(entity, TestEntity.testCache(entity.id))
            commit()
            assertEquals(entity, TestEntity.testCache(entity.id))
        }
    }

    object TableWithDefaultValue : IdTable<Int>() {
        val value = integer("value")
        val valueWithDefault = integer("valueWithDefault")
            .default(10)

        override val id: Column<EntityID<Int>> = integer("id")
            .clientDefault { Random.nextInt() }
            .entityId()

        override val primaryKey: PrimaryKey = PrimaryKey(id)
    }

    class TableWithDefaultValueEntity(id: EntityID<Int>) : IntEntity(id) {
        var value by TableWithDefaultValue.value

        var valueWithDefault by TableWithDefaultValue.valueWithDefault

        companion object : IntEntityClass<TableWithDefaultValueEntity>(TableWithDefaultValue)
    }

    @Test
    fun entitiesWithDifferentAmountOfFieldsCouldBeCreated() {
        withTables(TableWithDefaultValue) {
            TableWithDefaultValueEntity.new {
                value = 1
            }
            TableWithDefaultValueEntity.new {
                value = 2
                valueWithDefault = 1
            }

            // It's the key flush. It must not fail with inconsistent batch insert statement.
            // The table also should have client side default value. Otherwise the `writeValues`
            // would be extended with default values inside `EntityClass::new()` method.
            flushCache()
            entityCache.clear()

            val entity = TableWithDefaultValueEntity.find { TableWithDefaultValue.value eq 1 }.first()
            assertEquals(10, entity.valueWithDefault)
        }
    }
}
