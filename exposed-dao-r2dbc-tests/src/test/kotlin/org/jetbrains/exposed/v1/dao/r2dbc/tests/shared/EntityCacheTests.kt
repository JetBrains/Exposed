package org.jetbrains.exposed.v1.dao.r2dbc.tests.shared

import io.r2dbc.spi.IsolationLevel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.IdTable
import org.jetbrains.exposed.v1.core.dao.id.IntIdTable
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.exposedLogger
import org.jetbrains.exposed.v1.dao.r2dbc.IntEntity
import org.jetbrains.exposed.v1.dao.r2dbc.IntEntityClass
import org.jetbrains.exposed.v1.dao.r2dbc.entityCache
import org.jetbrains.exposed.v1.dao.r2dbc.flushCache
import org.jetbrains.exposed.v1.r2dbc.SchemaUtils
import org.jetbrains.exposed.v1.r2dbc.deleteAll
import org.jetbrains.exposed.v1.r2dbc.insert
import org.jetbrains.exposed.v1.r2dbc.selectAll
import org.jetbrains.exposed.v1.r2dbc.tests.NOT_APPLICABLE_TO_JDBC
import org.jetbrains.exposed.v1.r2dbc.tests.R2dbcDatabaseTestsBase
import org.jetbrains.exposed.v1.r2dbc.tests.TestDB
import org.jetbrains.exposed.v1.r2dbc.tests.shared.assertEqualLists
import org.jetbrains.exposed.v1.r2dbc.tests.shared.assertEquals
import org.jetbrains.exposed.v1.r2dbc.transactions.suspendTransaction
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.sql.SQLException
import java.util.concurrent.atomic.AtomicInteger
import kotlin.random.Random

class EntityCacheTests : R2dbcDatabaseTestsBase() {
    object TestTable : IntIdTable("TestCache") {
        val value = integer("value")
    }

    class TestEntity(id: EntityID<Int>) : IntEntity(id) {
        var value by TestTable.value
        val children by TestChildEntity referrersOn TestChildTable.parent

        companion object : IntEntityClass<TestEntity>(TestTable)
    }

    object TestChildTable : IntIdTable("TestCacheChild") {
        val parent = reference("parent", TestTable)
        val name = varchar("name", 50)
    }

    class TestChildEntity(id: EntityID<Int>) : IntEntity(id) {
        var name by TestChildTable.name
        val parent by TestEntity referencedOn TestChildTable.parent

        companion object : IntEntityClass<TestChildEntity>(TestChildTable)
    }

    @Test
    fun testEntitiesAreServedFromCacheUntilCleared() = runTest {
        Assumptions.assumeTrue(TestDB.H2_V2 in TestDB.enabledDialects())
        val entitiesCount = 25
        val db = TestDB.H2_V2.connect()

        suspendTransaction(db) {
            try {
                SchemaUtils.create(TestTable)

                repeat(entitiesCount) {
                    TestEntity.newSuspend {
                        value = Random.nextInt()
                    }
                }

                val entityIds = TestTable.selectAll().map { it[TestTable.id] }.toList()
                val initialStatementCount = statementCount
                entityIds.forEach {
                    TestEntity[it]
                }
                // All read from cache
                assertEquals(initialStatementCount, statementCount)

                entityCache.clear()
                // One query is enough to load all of them back
                TestEntity.all().toList()

                entityIds.forEach {
                    TestEntity[it]
                }
                assertEquals(initialStatementCount + 1, statementCount)
            } finally {
                SchemaUtils.drop(TestTable)
            }
        }
    }

    @Test
    fun `EntityCache should not be cleaned on explicit commit`() {
        withTables(TestTable) {
            val entity = TestEntity.newSuspend {
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
            TableWithDefaultValueEntity.newSuspend {
                value = 1
            }
            TableWithDefaultValueEntity.newSuspend {
                value = 2
                valueWithDefault = 1
            }

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

        val db1 = dialect.connect()
        try {
            suspendTransaction(transactionIsolation = IsolationLevel.SERIALIZABLE, db = db1) {
                SchemaUtils.create(TestTable)
                TestTable.deleteAll()

                repeat(testSize) {
                    TestTable.insert {
                        it[value] = 0
                    }
                }
            }

            val entities = suspendTransaction(transactionIsolation = IsolationLevel.SERIALIZABLE, db = db1) {
                TestEntity
                    .find { TestTable.value eq 0 }
                    .toList()
            }
            exposedLogger.info("total entities {}", entities.size)

            List(entities.size) { index ->
                async {
                    val statementInvocationNumber = AtomicInteger(0)
                    suspendTransaction(transactionIsolation = IsolationLevel.SERIALIZABLE, db = db1) {
                        maxAttempts = 50

                        val entity = entities[index]
                        // R2DBC: entity was loaded in a different transaction — re-attach to
                        // the current transaction's cache before mutating (setValue is non-suspend).
                        TestEntity.attach(entity)
                        entity.value = 1

                        exposedLogger.info(
                            "Updating entity id={} invocation={}",
                            entities[index].id,
                            statementInvocationNumber.incrementAndGet()
                        )
                    }
                }
            }.awaitAll()

            entities.forEach {
                suspendTransaction(transactionIsolation = IsolationLevel.SERIALIZABLE, db = db1) {
                    exposedLogger.info("DAO state after update: {} value={}", it.id, it.value)
                }
            }

            val db2 = dialect.connect()

            val notUpdated = suspendTransaction(transactionIsolation = IsolationLevel.SERIALIZABLE, db = db2) {
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
            suspendTransaction(db1) {
                SchemaUtils.drop(TestTable)
            }
        }
    }

    @Test
    fun testEntityRestoresStateOnTransactionRestart() {
        withConnection(dialect) { database, testDb ->
            try {
                val entity = suspendTransaction {
                    SchemaUtils.create(TestTable)

                    TestEntity.newSuspend { value = 1 }
                }

                suspendTransaction {
                    maxAttempts = 5

                    // R2DBC: an entity loaded in another transaction must be explicitly
                    // re-attached to the current transaction's cache before it can be mutated
                    // (setValue is non-suspend so it cannot auto-load like JDBC does).
                    TestEntity.attach(entity)

                    assertEquals(1, entity.value)
                    entity.value += 1

                    throw SQLException("force transaction rollback and restart")
                }
            } catch (_: SQLException) {
                // do nothing
            } finally {
                suspendTransaction {
                    SchemaUtils.drop(TestTable)
                }
            }
        }
    }

    @Test
    fun testReferenceRepointedToEntityWithPendingInsert() {
        withTables(TestTable, TestChildTable) {
            val existing = TestEntity.newSuspend { value = 1 }
            TestChildEntity.newSuspend {
                name = "c"
                parent.set(existing)
            }
            flushCache()

            // caches the referrer list under `existing.id` in entityCache.referrers[TestChildTable.parent]
            assertEquals(1, existing.children.toList().size)

            val pending = TestEntity.new { value = 2 }
            val child = existing.children.toList().single()

            child.parent.set(pending)
            flushCache()

            val childRow = TestChildTable.selectAll().toList().single()
            assertEquals(pending.id.value, childRow[TestChildTable.parent].value)
            assertEquals(0, existing.children.toList().size)
            assertEquals("c", pending.children.toList().single().name)
        }
    }

    @Test
    fun testDeletingEntityWithPendingInsert() {
        withTables(TestTable, TestChildTable) {
            val existing = TestEntity.newSuspend { value = 1 }
            TestChildEntity.newSuspend {
                name = "c"
                parent.set(existing)
            }
            flushCache()

            // caches a referrer list, so removeFromCache has something to iterate
            assertEquals(1, existing.children.toList().size)

            val pending = TestEntity.new { value = 2 }
            pending.delete()
            flushCache()

            val rows = TestTable.selectAll().toList()
            assertEquals(1, rows.size)
            assertEquals(1, rows.single()[TestTable.value])
            assertEquals(1, existing.children.toList().size)
        }
    }

    /**
     * Several new entities of one table are flushed as a single batch insert, and every one of them has to
     * come back with its generated id. Oracle's R2DBC driver refuses to return generated values from a
     * batched statement, so this also covers the fallback to one statement per row.
     */
    @Tag(NOT_APPLICABLE_TO_JDBC)
    @Test
    fun testFlushingSeveralNewEntitiesAssignsEveryId() {
        withTables(TestTable) {
            val entities = (1..3).map { number -> TestEntity.new { value = number } }
            flushCache()

            assertEquals(3, entities.map { it.id.value }.distinct().size)
            assertEqualLists(TestTable.selectAll().toList().map { it[TestTable.value] }.sorted(), listOf(1, 2, 3))
        }
    }
}
