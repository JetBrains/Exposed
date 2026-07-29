package org.jetbrains.exposed.v1.dao.r2dbc.tests.shared

import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.flattenConcat
import kotlinx.coroutines.flow.single
import kotlinx.coroutines.flow.toList
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.IntIdTable
import org.jetbrains.exposed.v1.core.statements.StatementContext
import org.jetbrains.exposed.v1.core.statements.StatementType
import org.jetbrains.exposed.v1.dao.r2dbc.IntEntity
import org.jetbrains.exposed.v1.dao.r2dbc.IntEntityClass
import org.jetbrains.exposed.v1.r2dbc.R2dbcTransaction
import org.jetbrains.exposed.v1.r2dbc.SchemaUtils
import org.jetbrains.exposed.v1.r2dbc.statements.SuspendStatementInterceptor
import org.jetbrains.exposed.v1.r2dbc.statements.api.R2dbcPreparedStatementApi
import org.jetbrains.exposed.v1.r2dbc.tests.R2dbcDatabaseTestsBase
import org.jetbrains.exposed.v1.r2dbc.transactions.inTopLevelSuspendTransaction
import org.jetbrains.exposed.v1.r2dbc.transactions.suspendTransaction
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

class NewDeferredBatchTest : R2dbcDatabaseTestsBase() {
    object Items : IntIdTable("items_ndb") {
        val name = varchar("name", 50)
    }

    class ItemEntity(id: EntityID<Int>) : IntEntity(id) {
        var name by Items.name
        companion object : IntEntityClass<ItemEntity>(Items)
    }

    private class InsertExecutionCounter : SuspendStatementInterceptor {
        var count = 0
        override suspend fun afterExecution(
            transaction: R2dbcTransaction,
            contexts: List<StatementContext>,
            executedStatement: R2dbcPreparedStatementApi
        ) {
            if (contexts.firstOrNull()?.statement?.type == StatementType.INSERT) count++
        }
    }

    @Test
    fun testBatchInsertOnSingleFlush() {
        withTables(Items) {
            val counter = InsertExecutionCounter()
            registerInterceptor(counter)

            val deferred = (1..5).map { i ->
                ItemEntity.newDeferred { name = "item$i" }
            }
            assertEquals(0, counter.count, "no INSERT should run before collection")

            val entities = deferred.asFlow().flattenConcat().toList()
            assertEquals(1, counter.count, "all 5 entities are persisted by a single batch INSERT")

            entities.forEach { it.name }
            assertEquals(1, counter.count, "no additional INSERT after collection")

            assertEquals(listOf("item1", "item2", "item3", "item4", "item5"), entities.map { it.name })
            entities.forEach { assertNotNull(it.id._value, "id must be populated") }
        }
    }

    @Test
    fun testCollectWithoutTransactionInContextFails() = withConnection { database, _ ->
        suspendTransaction(database) { SchemaUtils.create(Items) }
        try {
            val deferred = suspendTransaction(database) {
                maxAttempts = 1
                ItemEntity.newDeferred { name = "escaped" }
            }

            val failure = assertFailsWith<IllegalStateException> { deferred.toList() }
            assertContains(assertNotNull(failure.message), "no transaction is in context")
        } finally {
            suspendTransaction(database) { SchemaUtils.drop(Items) }
        }
    }

    @Test
    fun testCollectInDifferentTransactionFails() {
        withTables(Items) {
            val deferred = inTopLevelSuspendTransaction(null) {
                maxAttempts = 1
                ItemEntity.newDeferred { name = "escaped" }
            }

            val failure = assertFailsWith<IllegalStateException> { deferred.toList() }
            assertContains(assertNotNull(failure.message), "must be collected inside the transaction")
        }
    }

    @Test
    fun testCollectInNestedTransactionSucceeds() {
        withTables(Items) {
            val deferred = ItemEntity.newDeferred { name = "nested" }

            val entity = suspendTransaction { deferred.single() }

            assertEquals("nested", entity.name)
            assertNotNull(entity.id._value, "id must be populated")
        }
    }
}
