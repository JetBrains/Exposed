package org.jetbrains.exposed.v1.dao.r2dbc.tests.shared

import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.flattenConcat
import kotlinx.coroutines.flow.toList
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.IntIdTable
import org.jetbrains.exposed.v1.core.statements.StatementContext
import org.jetbrains.exposed.v1.core.statements.StatementType
import org.jetbrains.exposed.v1.dao.r2dbc.IntEntity
import org.jetbrains.exposed.v1.dao.r2dbc.IntEntityClass
import org.jetbrains.exposed.v1.r2dbc.R2dbcTransaction
import org.jetbrains.exposed.v1.r2dbc.statements.SuspendStatementInterceptor
import org.jetbrains.exposed.v1.r2dbc.statements.api.R2dbcPreparedStatementApi
import org.jetbrains.exposed.v1.r2dbc.tests.R2dbcDatabaseTestsBase
import kotlin.test.Test
import kotlin.test.assertEquals
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
}
