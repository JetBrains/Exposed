package org.jetbrains.exposed.v1.dao.r2dbc.tests.shared

import kotlinx.coroutines.flow.single
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.IntIdTable
import org.jetbrains.exposed.v1.dao.r2dbc.IntEntity
import org.jetbrains.exposed.v1.dao.r2dbc.IntEntityClass
import org.jetbrains.exposed.v1.dao.r2dbc.exceptions.EntityNotFoundException
import org.jetbrains.exposed.v1.dao.r2dbc.flushCache
import org.jetbrains.exposed.v1.r2dbc.R2dbcTransaction
import org.jetbrains.exposed.v1.r2dbc.selectAll
import org.jetbrains.exposed.v1.r2dbc.tests.NOT_APPLICABLE_TO_JDBC
import org.jetbrains.exposed.v1.r2dbc.tests.R2dbcDatabaseTestsBase
import org.jetbrains.exposed.v1.r2dbc.tests.shared.expectException
import org.jetbrains.exposed.v1.r2dbc.transactions.inTopLevelSuspendTransaction
import org.jetbrains.exposed.v1.r2dbc.transactions.suspendTransaction
import org.junit.jupiter.api.Tag
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

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
                Item.newSuspend { name = "foo" }
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
                Item.newSuspend { name = "original" }
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
                Item.newSuspend { name = "original" }
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
                Item.newSuspend { name = "original" }
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
                val first = Owner.newSuspend { name = "first" }
                val second = Owner.newSuspend { name = "second" }
                val owned = OwnedItem.newSuspend { owner.set(first) }
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
                val first = Owner.newSuspend { name = "first" }
                val second = Owner.newSuspend { name = "second" }
                val owned = OwnedItem.newSuspend { owner.set(first) }
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
                    val item = Item.newSuspend { name = "doomed" }
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
                Item.newSuspend { name = "doomed" }
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
                Item.newSuspend { name = "original" }
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

    @Test
    fun testAttachReplacesACleanTrackedInstance() {
        withTables(Items) {
            val carriedOver = newTransaction {
                maxAttempts = 1
                Item.newSuspend { name = "original" }
            }

            newTransaction {
                maxAttempts = 1
                val tracked = assertNotNull(Item.findById(carriedOver.id))
                assertTrue(carriedOver !== tracked, "expected two instances of the same row")

                Item.attach(carriedOver)
                carriedOver.name = "changed"
            }

            newTransaction {
                maxAttempts = 1
                assertEquals("changed", Items.selectAll().single()[Items.name])
            }
        }
    }

    @Test
    fun testAttachRejectsReplacingATrackedInstanceWithUnflushedChanges() {
        withTables(Items) {
            val carriedOver = newTransaction {
                maxAttempts = 1
                Item.newSuspend { name = "original" }
            }

            newTransaction {
                maxAttempts = 1
                val tracked = assertNotNull(Item.findById(carriedOver.id))
                tracked.name = "pending"

                expectException<IllegalStateException> {
                    Item.attach(carriedOver)
                }
            }
        }
    }

    @Test
    fun testAttachForceReplacesATrackedInstanceWithUnflushedChanges() {
        withTables(Items) {
            val carriedOver = newTransaction {
                maxAttempts = 1
                Item.newSuspend { name = "original" }
            }

            newTransaction {
                maxAttempts = 1
                val tracked = assertNotNull(Item.findById(carriedOver.id))
                tracked.name = "discarded"

                Item.attach(carriedOver, force = true)
                carriedOver.name = "kept"
            }

            newTransaction {
                maxAttempts = 1
                assertEquals("kept", Items.selectAll().single()[Items.name])
            }
        }
    }

    @Test
    fun testDetachedEntityIsReadableButNotWritable() {
        withTables(Items) {
            val item = newTransaction {
                maxAttempts = 1
                Item.newSuspend { name = "original" }
            }

            newTransaction {
                maxAttempts = 1
                Item.attach(item)
                Item.detach(item)

                assertEquals("original", item.name)
                expectException<EntityNotFoundException> { item.name = "changed" }
            }
        }
    }

    @Test
    fun testReattachAfterDetach() {
        withTables(Items) {
            val item = newTransaction {
                maxAttempts = 1
                Item.newSuspend { name = "original" }
            }

            newTransaction {
                maxAttempts = 1
                Item.attach(item)
                Item.detach(item)
                expectException<EntityNotFoundException> { item.name = "unreachable" }

                Item.attach(item)
                item.name = "changed"
            }

            newTransaction {
                maxAttempts = 1
                assertEquals("changed", Items.selectAll().single()[Items.name])
            }
        }
    }

    @Test
    fun testDetachIsIdempotent() {
        withTables(Items) {
            val item = newTransaction {
                maxAttempts = 1
                Item.newSuspend { name = "original" }
            }

            newTransaction {
                maxAttempts = 1
                Item.attach(item)
                Item.detach(item)
                Item.detach(item)

                assertEquals("original", item.name)
            }
        }
    }

    @Test
    fun testDetachRefusesToDiscardUncommittedValues() {
        withTables(Items) {
            val item = newTransaction {
                maxAttempts = 1
                Item.newSuspend { name = "original" }
            }

            newTransaction {
                maxAttempts = 1
                Item.attach(item)
                item.name = "pending"

                expectException<IllegalStateException> { Item.detach(item) }

                Item.detach(item, force = true)
            }

            newTransaction {
                maxAttempts = 1
                assertEquals("original", Items.selectAll().single()[Items.name])
            }
        }
    }

    @Tag(NOT_APPLICABLE_TO_JDBC)
    @Test
    fun testForceDetachDiscardsOnlyUnissuedValuesFromEnclosingTransaction() {
        withTables(Items, configure = { useNestedTransactions = true }) {
            val item = Item.newSuspend { name = "original" }
            flushCache()
            commit()

            item.name = "issued"
            flushCache()
            item.name = "discarded"

            suspendTransaction {
                maxAttempts = 1
                Item.detach(item, force = true)
            }

            flushCache()
            commit()

            assertEquals("issued", Items.selectAll().single()[Items.name])
        }
    }

    /**
     * A row this transaction created has no committed state to fall back on, so detaching it would leave an
     * unreadable entity behind and an insert nobody owns. Withdrawing it is what `delete()` is for.
     */
    @Test
    fun testDetachRefusesAnEntityThisTransactionCreated() {
        withTables(Items) {
            val item = Item.newSuspend { name = "fresh" }

            expectException<IllegalStateException> { Item.detach(item) }
            expectException<IllegalStateException> { Item.detach(item, force = true) }

            flushCache()
            expectException<IllegalStateException> { Item.detach(item) }
        }
    }
}
