package org.jetbrains.exposed.dao.r2dbc.tests.shared

import kotlinx.coroutines.flow.single
import org.jetbrains.exposed.r2dbc.dao.IntEntity
import org.jetbrains.exposed.r2dbc.dao.IntEntityClass
import org.jetbrains.exposed.r2dbc.dao.exceptions.EntityNotFoundException
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.IntIdTable
import org.jetbrains.exposed.v1.r2dbc.R2dbcTransaction
import org.jetbrains.exposed.v1.r2dbc.selectAll
import org.jetbrains.exposed.v1.r2dbc.tests.R2dbcDatabaseTestsBase
import org.jetbrains.exposed.v1.r2dbc.tests.shared.expectException
import org.jetbrains.exposed.v1.r2dbc.transactions.inTopLevelSuspendTransaction
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AttachEntityTests : R2dbcDatabaseTestsBase() {

    object Items : IntIdTable("attach_test_items") {
        val name = varchar("name", 255)
    }

    class Item(id: EntityID<Int>) : IntEntity(id) {
        companion object : IntEntityClass<Item>(Items)

        var name by Items.name
    }

    private suspend fun <T> newTransaction(statement: suspend R2dbcTransaction.() -> T) =
        inTopLevelSuspendTransaction(null, statement = statement)

    @Test
    fun testAttachAndModifyInNewTransaction() {
        withTables(Items) {
            val item = newTransaction {
                maxAttempts = 1
                Item.new { name = "foo" }.flush()
            }
            newTransaction {
                maxAttempts = 1
                assertNull(Item.testCache(item.id))
                assertEquals("foo", Items.selectAll().single()[Items.name])
                Item.attach(item)
                item.name = "bar"
                assertEquals(item, Item.testCache(item.id))
                assertEquals("bar", Items.selectAll().single()[Items.name])
            }

            newTransaction {
                maxAttempts = 1
                assertEquals("bar", Items.selectAll().single()[Items.name])
            }
        }
    }

    @Test
    fun testAttachAndModifyIsAutoFlushedOnCommit() {
        withTables(Items) {
            val item = newTransaction {
                maxAttempts = 1
                Item.new { name = "original" }.flush()
            }

            newTransaction {
                maxAttempts = 1
                Item.attach(item)
                item.name = "modified"
                // No explicit flush — auto-flushed by beforeCommit via flushCache()
            }

            newTransaction {
                maxAttempts = 1
                assertEquals("modified", Items.selectAll().single()[Items.name])
            }
        }
    }

    @Test
    fun testAttachPreservesModificationsAcrossTransactionHops() {
        withTables(Items) {
            val item = newTransaction {
                maxAttempts = 1
                Item.new { name = "original" }.flush()
            }

            newTransaction {
                maxAttempts = 1
                Item.attach(item)
                item.name = "from_txA"
            }

            newTransaction {
                maxAttempts = 1
                Item.attach(item)
                assertEquals("from_txA", item.name)
            }
        }
    }

    @Test
    fun testModifyEntityWithoutAttachThrows() {
        withTables(Items) {
            val item = newTransaction {
                maxAttempts = 1
                Item.new { name = "original" }.flush()
            }

            newTransaction {
                maxAttempts = 1
                expectException<EntityNotFoundException> {
                    item.name = "boom"
                }
            }
        }
    }

    @Test
    fun testModifyDeletedEntityThrowsNotFound() {
        withTables(Items) {
            newTransaction {
                maxAttempts = 1
                expectException<EntityNotFoundException> {
                    val item = Item.new { name = "doomed" }.flush()
                    item.delete()
                    item.name = "boom"
                }
            }
        }
    }

    @Test
    fun testAttachDeletedEntityThrows() {
        withTables(Items) {
            val item = newTransaction {
                maxAttempts = 1
                Item.new { name = "doomed" }.flush()
            }

            newTransaction {
                maxAttempts = 1
                Item.attach(item)
                item.delete()
            }

            newTransaction {
                maxAttempts = 1
                expectException<EntityNotFoundException> {
                    Item.attach(item)
                }
            }
        }
    }

    @Test
    fun testAttachIsIdempotentWithinSameTransaction() {
        withTables(Items) {
            val item = newTransaction {
                maxAttempts = 1
                Item.new { name = "original" }.flush()
            }

            newTransaction {
                maxAttempts = 1
                Item.attach(item)
                item.name = "changed"
                Item.attach(item)
                assertEquals("changed", item.name)
            }
        }
    }
}
