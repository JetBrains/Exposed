package org.jetbrains.exposed.v1.tests.shared.entities

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.IdTable
import org.jetbrains.exposed.v1.core.dao.id.IntIdTable
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.exposedLogger
import org.jetbrains.exposed.v1.dao.IntEntity
import org.jetbrains.exposed.v1.dao.IntEntityClass
import org.jetbrains.exposed.v1.dao.entityCache
import org.jetbrains.exposed.v1.dao.flushCache
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.deleteAll
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.tests.DatabaseTestsBase
import org.jetbrains.exposed.v1.tests.MISSING_R2DBC_TEST
import org.jetbrains.exposed.v1.tests.TestDB
import org.jetbrains.exposed.v1.tests.shared.assertEqualCollections
import org.jetbrains.exposed.v1.tests.shared.assertEquals
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.sql.Connection.TRANSACTION_SERIALIZABLE
import java.sql.SQLException
import java.util.concurrent.atomic.AtomicInteger
import kotlin.random.Random

@Tag(MISSING_R2DBC_TEST)
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
        Assumptions.assumeTrue(TestDB.H2_V2 in TestDB.enabledDialects())
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
        Assumptions.assumeTrue(TestDB.H2_V2 in TestDB.enabledDialects())
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

    /**
     * EXPOSED-886 Changes made to DAO (entity) can be lost on serializable transaction retry (Postgres)
     */
    @Test
    fun testConcurrentSerializableAccessWithTransactionsRetry() = runBlocking(Dispatchers.IO) {
        val testSize = 10

        // Only SQLite complains that the TestTable doesn't exists
        if (dialect in listOf(TestDB.SQLITE)) {
            Assumptions.assumeFalse(true)
        }

        val db1 = dialect.connect()
        try {
            transaction(transactionIsolation = TRANSACTION_SERIALIZABLE, db = db1) {
                SchemaUtils.create(TestTable)
                TestTable.deleteAll()

                repeat(testSize) {
                    TestTable.insert {
                        it[value] = 0
                    }
                }
            }

            val entities = transaction(transactionIsolation = TRANSACTION_SERIALIZABLE, db = db1) {
                TestEntity
                    .find { TestTable.value eq 0 }
                    .toList()
            }
            exposedLogger.info("total entities {}", entities.size)

            List(entities.size) { index ->
                async {
                    val statementInvocationNumber = AtomicInteger(0)
                    transaction(transactionIsolation = TRANSACTION_SERIALIZABLE, db = db1) {
                        maxAttempts = 50

                        val entity = entities[index]
                        entity.value = 1

                        exposedLogger.info(
                            "Updating entity id={} invocation={}  writeValuesSize={}",
                            entities[index].id,
                            statementInvocationNumber.incrementAndGet(),
                            entities[index].writeValues.size
                        )
                    }
                }
            }.awaitAll()

            entities.forEach {
                transaction(transactionIsolation = TRANSACTION_SERIALIZABLE, db = db1) {
                    exposedLogger.info("DAO state after update: {} value={} writeValuesSize={}", it.id, it.value, it.writeValues.size)
                }
            }

            val db2 = dialect.connect()

            val notUpdated = transaction(transactionIsolation = TRANSACTION_SERIALIZABLE, db = db2) {
                TestTable
                    .selectAll()
                    .where { TestTable.value eq 0 }
                    .toList()
            }

            notUpdated.forEach {
                exposedLogger.info("not updated: {} value={}", it[TestTable.id], it[TestTable.value])
            }

            if (notUpdated.isNotEmpty()) {
                error("Not all entries updated, wrong value for ${notUpdated.size}")
            }
        } finally {
            transaction(db1) {
                SchemaUtils.drop(TestTable)
            }
        }
    }

    @Test
    fun testEntityRestoresStateOnTransactionRestart() {
        withConnection(dialect) { database, testDb ->
            try {
                val entity = transaction {
                    SchemaUtils.create(TestTable)

                    TestEntity.new { value = 1 }
                }

                transaction {
                    maxAttempts = 5

                    assertEquals(1, entity.value)
                    entity.value += 1

                    throw SQLException("force transaction rollback and restart")
                }
            } catch (_: SQLException) {
                // do nothing
            } finally {
                transaction {
                    SchemaUtils.drop(TestTable)
                }
            }
        }
    }
}
