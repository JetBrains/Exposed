package org.jetbrains.exposed.v1.dao.r2dbc.tests.shared

import kotlinx.coroutines.flow.single
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.IntIdTable
import org.jetbrains.exposed.v1.dao.r2dbc.IntEntity
import org.jetbrains.exposed.v1.dao.r2dbc.IntEntityClass
import org.jetbrains.exposed.v1.dao.r2dbc.exceptions.EntityNotFoundException
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

    object Owners : IntIdTable("attach_test_owners") {
        val name = varchar("name", 255)
    }

    class Owner(id: EntityID<Int>) : IntEntity(id) {
        companion object : IntEntityClass<Owner>(Owners)

        var name by Owners.name
    }

    object OwnedItems : IntIdTable("attach_test_owned_items") {
        val owner = reference("owner", Owners)
        val optionalOwner = optReference("optional_owner", Owners)
    }

    class OwnedItem(id: EntityID<Int>) : IntEntity(id) {
        companion object : IntEntityClass<OwnedItem>(OwnedItems)

        val owner by Owner referencedOn OwnedItems.owner
        val optionalOwner by Owner optionalReferencedOn OwnedItems.optionalOwner
    }

    private suspend fun <T> newTransaction(statement: suspend R2dbcTransaction.() -> T) =
        inTopLevelSuspendTransaction(null, statement = statement)

    @Test
    fun testAttachAndModifyInNewTransaction() {
        withTables(Items) {
            val item = newTransaction {
                maxAttempts = 1
                Item.new { name = "foo" }
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
                Item.new { name = "original" }
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
                Item.new { name = "original" }
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
                Item.new { name = "original" }
            }

            newTransaction {
                maxAttempts = 1
                expectException<EntityNotFoundException> {
                    item.name = "boom"
                }
            }
        }
    }

    /**
     * A reference write must reject a detached entity the same way a plain column write does
     * (see [testModifyEntityWithoutAttachThrows]). Reference writes assign `writeValues` directly
     * instead of going through `Entity.setValue`, so without an explicit guard the assignment is
     * dropped silently and the caller believes it succeeded.
     */
    @Test
    fun testSetReferenceWithoutAttachThrows() {
        withTables(Owners, OwnedItems) {
            val (item, firstOwnerId, secondOwner) = newTransaction {
                maxAttempts = 1
                val first = Owner.new { name = "first" }
                val second = Owner.new { name = "second" }
                val owned = OwnedItem.new { owner.set(first) }
                Triple(owned, first.id, second)
            }

            newTransaction {
                maxAttempts = 1
                expectException<EntityNotFoundException> {
                    item.owner.set(secondOwner)
                }
                expectException<EntityNotFoundException> {
                    item.optionalOwner.set(secondOwner)
                }
                expectException<EntityNotFoundException> {
                    item.optionalOwner.set(null)
                }
            }

            newTransaction {
                maxAttempts = 1
                val row = OwnedItems.selectAll().single()
                assertEquals(firstOwnerId, row[OwnedItems.owner])
                assertNull(row[OwnedItems.optionalOwner])
            }
        }
    }

    @Test
    fun testSetReferenceAfterAttachSucceeds() {
        withTables(Owners, OwnedItems) {
            val (item, _, secondOwner) = newTransaction {
                maxAttempts = 1
                val first = Owner.new { name = "first" }
                val second = Owner.new { name = "second" }
                val owned = OwnedItem.new { owner.set(first) }
                Triple(owned, first.id, second)
            }

            newTransaction {
                maxAttempts = 1
                OwnedItem.attach(item)
                Owner.attach(secondOwner)
                item.owner.set(secondOwner)
            }

            newTransaction {
                maxAttempts = 1
                assertEquals(secondOwner.id, OwnedItems.selectAll().single()[OwnedItems.owner])
            }
        }
    }

    @Test
    fun testModifyDeletedEntityThrowsNotFound() {
        withTables(Items) {
            newTransaction {
                maxAttempts = 1
                expectException<EntityNotFoundException> {
                    val item = Item.new { name = "doomed" }
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
                Item.new { name = "doomed" }
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
                Item.new { name = "original" }
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
