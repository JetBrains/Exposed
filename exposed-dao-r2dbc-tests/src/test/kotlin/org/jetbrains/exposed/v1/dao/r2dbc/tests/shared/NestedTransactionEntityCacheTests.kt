package org.jetbrains.exposed.v1.dao.r2dbc.tests.shared

import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.single
import kotlinx.coroutines.flow.toList
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.IntIdTable
import org.jetbrains.exposed.v1.dao.r2dbc.IntEntity
import org.jetbrains.exposed.v1.dao.r2dbc.IntEntityClass
import org.jetbrains.exposed.v1.dao.r2dbc.flushCache
import org.jetbrains.exposed.v1.r2dbc.R2dbcTransaction
import org.jetbrains.exposed.v1.r2dbc.selectAll
import org.jetbrains.exposed.v1.r2dbc.tests.MISSING_JDBC_TEST
import org.jetbrains.exposed.v1.r2dbc.tests.R2dbcDatabaseTestsBase
import org.jetbrains.exposed.v1.r2dbc.transactions.inTopLevelSuspendTransaction
import org.jetbrains.exposed.v1.r2dbc.transactions.suspendTransaction
import org.junit.jupiter.api.Tag
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertSame

@Tag(MISSING_JDBC_TEST)
class NestedTransactionEntityCacheTests : R2dbcDatabaseTestsBase() {

    object Items : IntIdTable("nested_cache_items") {
        val name = varchar("name", 255)
        val note = varchar("note", 255)
    }

    class Item(id: EntityID<Int>) : IntEntity(id) {
        companion object : IntEntityClass<Item>(Items)

        var name by Items.name
        var note by Items.note
    }

    object Owners : IntIdTable("nested_cache_owners") {
        val name = varchar("name", 255)
    }

    object Pets : IntIdTable("nested_cache_pets") {
        val owner = reference("owner", Owners)
        val name = varchar("name", 255)
    }

    class Owner(id: EntityID<Int>) : IntEntity(id) {
        companion object : IntEntityClass<Owner>(Owners)

        var name by Owners.name
        val pets by Pet referrersOn Pets.owner
    }

    class Pet(id: EntityID<Int>) : IntEntity(id) {
        companion object : IntEntityClass<Pet>(Pets)

        val owner by Owner referencedOn Pets.owner
        var name by Pets.name
    }

    private class Boom : RuntimeException("boom")

    private suspend fun <T> newTransaction(statement: suspend R2dbcTransaction.() -> T) =
        inTopLevelSuspendTransaction(null, statement = statement)

    private suspend fun newItem(name: String = "name0", note: String = "note0") = newTransaction {
        maxAttempts = 1
        Item.new {
            this.name = name
            this.note = note
        }
    }

    @Test
    fun testNestedTransactionSharesParentEntityInstance() {
        withTables(Items, configure = { useNestedTransactions = true }) {
            val outer = Item.new {
                name = "name0"
                note = "note0"
            }
            flushCache()

            suspendTransaction {
                assertSame(outer, assertNotNull(Item.findById(outer.id)))
            }
        }
    }

    @Test
    fun testWriteThroughNestedLookupReachesParentInstance() {
        withTables(Items, configure = { useNestedTransactions = true }) {
            val outer = Item.new {
                name = "name0"
                note = "note0"
            }
            flushCache()

            suspendTransaction {
                assertNotNull(Item.findById(outer.id)).name = "name1"
                flushCache()
            }

            assertEquals("name1", Items.selectAll().single()[Items.name])
            assertEquals("name1", outer.name)
        }
    }

    @Test
    fun testNestedDslQuerySeesOuterPendingWrite() {
        withTables(Items, configure = { useNestedTransactions = true }) {
            val item = Item.new {
                name = "name0"
                note = "note0"
            }
            flushCache()

            item.name = "name1"

            suspendTransaction {
                assertEquals("name1", Items.selectAll().single()[Items.name])
            }
        }
    }

    @Test
    fun testNestedQuerySeesOuterPendingInsert() {
        withTables(Items, configure = { useNestedTransactions = true }) {
            Item.new {
                name = "name0"
                note = "note0"
            }
            // Deliberately no flush: the insert is still queued.

            suspendTransaction {
                assertEquals("name0", Items.selectAll().single()[Items.name])
            }
        }
    }

    @Test
    fun testNestedRollbackKeepsOuterWriteItForcedOut() {
        withTables(Items, configure = { useNestedTransactions = true }) {
            val item = Item.new {
                name = "name0"
                note = "note0"
            }
            flushCache()

            item.name = "name1"

            runCatching {
                suspendTransaction {
                    assertEquals("name1", Items.selectAll().single()[Items.name])
                    throw Boom()
                }
            }

            assertEquals("name1", item.name)
            assertEquals("name1", Items.selectAll().single()[Items.name])
        }
    }

    @Test
    fun testNestedInsertInvalidatesOuterReferrers() {
        withTables(Owners, Pets, configure = { useNestedTransactions = true }) {
            val theOwner = Owner.new { name = "owner" }
            Pet.new {
                owner.set(theOwner)
                name = "pet1"
            }
            flushCache()

            // Caches the referrer list in the enclosing scope.
            assertEquals(listOf("pet1"), theOwner.pets.map { it.name }.toList())

            suspendTransaction {
                Pet.new {
                    owner.set(theOwner)
                    name = "pet2"
                }
                flushCache()
            }

            assertEquals(listOf("pet1", "pet2"), theOwner.pets.map { it.name }.toList())
        }
    }

    @Test
    fun testNestedReadDoesNotShadowOuterPendingWrite() {
        withTables(Items, configure = { useNestedTransactions = true }) {
            val item = newItem()

            newTransaction {
                maxAttempts = 1
                Item.attach(item)
                item.name = "name1"

                suspendTransaction {
                    Item.attach(item)
                    assertEquals("name1", item.name)

                    Item.all().toList().single()

                    assertEquals("name1", item.name)
                }
            }
        }
    }
}
