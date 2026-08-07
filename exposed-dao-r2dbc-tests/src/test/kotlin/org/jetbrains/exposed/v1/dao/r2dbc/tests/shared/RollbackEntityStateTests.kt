package org.jetbrains.exposed.v1.dao.r2dbc.tests.shared

import kotlinx.coroutines.flow.single
import kotlinx.coroutines.flow.toList
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.IntIdTable
import org.jetbrains.exposed.v1.dao.r2dbc.IntEntity
import org.jetbrains.exposed.v1.dao.r2dbc.IntEntityClass
import org.jetbrains.exposed.v1.dao.r2dbc.flushCache
import org.jetbrains.exposed.v1.r2dbc.R2dbcTransaction
import org.jetbrains.exposed.v1.r2dbc.insert
import org.jetbrains.exposed.v1.r2dbc.selectAll
import org.jetbrains.exposed.v1.r2dbc.tests.MISSING_JDBC_TEST
import org.jetbrains.exposed.v1.r2dbc.tests.R2dbcDatabaseTestsBase
import org.jetbrains.exposed.v1.r2dbc.tests.shared.expectException
import org.jetbrains.exposed.v1.r2dbc.transactions.inTopLevelSuspendTransaction
import org.jetbrains.exposed.v1.r2dbc.transactions.suspendTransaction
import org.junit.jupiter.api.Tag
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@Tag(MISSING_JDBC_TEST)
class RollbackEntityStateTests : R2dbcDatabaseTestsBase() {

    object Items : IntIdTable("rollback_state_items") {
        val name = varchar("name", 255)
        val note = varchar("note", 255)
    }

    class Item(id: EntityID<Int>) : IntEntity(id) {
        companion object : IntEntityClass<Item>(Items)

        var name by Items.name
        var note by Items.note
    }

    object Codes : IntIdTable("rollback_state_codes") {
        val code = varchar("code", 50).uniqueIndex()
    }

    private class Boom : RuntimeException("boom")

    private suspend fun <T> newTransaction(statement: suspend R2dbcTransaction.() -> T) =
        inTopLevelSuspendTransaction(null, statement = statement)

    private suspend fun newItem(name: String = "original", note: String = "note0") = newTransaction {
        maxAttempts = 1
        Item.new {
            this.name = name
            this.note = note
        }
    }

    @Test
    fun testEntityReadInRolledBackTransactionStaysReadable() {
        withTables(Items) {
            val id = newItem().id

            var handle: Item? = null
            runCatching {
                newTransaction {
                    maxAttempts = 1
                    handle = assertNotNull(Item.findById(id))
                    throw Boom()
                }
            }

            newTransaction {
                maxAttempts = 1
                assertEquals("original", assertNotNull(handle).name)
            }
        }
    }

    @Test
    fun testReadCarriedOverEntityWithoutAttach() {
        withTables(Items) {
            val item = newItem()

            newTransaction {
                maxAttempts = 1
                assertEquals("original", item.name)
            }

            newTransaction {
                maxAttempts = 1
                suspendTransaction {
                    assertEquals("original", item.name)
                }
            }
        }
    }

    @Test
    fun testRefreshRestoresEntityAfterRollback() {
        withTables(Items) {
            val item = newItem()

            runCatching {
                newTransaction {
                    maxAttempts = 1
                    Item.attach(item)
                    item.name = "changed"
                    flushCache()
                    throw Boom()
                }
            }

            newTransaction {
                maxAttempts = 1
                Item.attach(item)
                item.refresh()
                assertEquals("original", item.name)
            }
        }
    }

    @Test
    fun testEntityModifiedInRolledBackTransactionIsRestoredByAttach() {
        withTables(Items) {
            val item = newItem()

            runCatching {
                newTransaction {
                    maxAttempts = 1
                    Item.attach(item)
                    item.name = "changed"
                    flushCache()
                    throw Boom()
                }
            }

            newTransaction {
                maxAttempts = 1
                Item.attach(item)
                assertEquals("original", item.name)
            }

            newTransaction {
                maxAttempts = 1
                assertEquals("original", Items.selectAll().single()[Items.name])
            }
        }
    }

    /**
     * An uncommitted flush must not survive the rollback as if it had been committed.
     */
    @Test
    fun testUncommittedValuesAreNotCarriedOverAfterRollback() {
        withTables(Items) {
            val item = newItem()

            runCatching {
                newTransaction {
                    maxAttempts = 1
                    Item.attach(item)
                    item.name = "changed"
                    flushCache()
                    assertEquals("changed", item.name)
                    throw Boom()
                }
            }

            newTransaction {
                maxAttempts = 1
                assertEquals("original", item.name)
            }
        }
    }

    @Test
    fun testEntityInsertedInRolledBackTransactionIsInvalidated() {
        withTables(Items) {
            var created: Item? = null
            runCatching {
                newTransaction {
                    maxAttempts = 1
                    created = Item.new {
                        name = "ghost"
                        note = "note0"
                    }
                    throw Boom()
                }
            }

            newTransaction {
                maxAttempts = 1
                assertEquals(0L, Items.selectAll().count())
                expectException<IllegalStateException> { assertNotNull(created).name }
            }
        }
    }

    @Test
    fun testEntityReusedAcrossBuiltInRetry() {
        withTables(Items, Codes) {
            val item = newTransaction {
                maxAttempts = 1
                Codes.insert { it[code] = "taken" }
                Item.new {
                    name = "original"
                    note = "note0"
                }
            }

            var attempt = 0
            newTransaction {
                maxAttempts = 3
                minRetryDelay = 1
                maxRetryDelay = 1
                attempt++
                Item.attach(item)
                assertEquals("original", item.name)
                // Attempt 1 collides on the unique index, forcing a rollback and a retry.
                Codes.insert { it[code] = if (attempt == 1) "taken" else "free" }
            }

            assertEquals(2, attempt)
        }
    }

    @Test
    fun testNestedRollbackKeepsOuterPendingWrite() {
        withTables(Items, configure = { useNestedTransactions = true }) {
            val item = Item.new {
                name = "name0"
                note = "note0"
            }
            flushCache()

            item.name = "name1"

            runCatching {
                suspendTransaction {
                    Item.attach(item)
                    item.note = "note2"
                    flushCache()
                    throw Boom()
                }
            }

            assertEquals("name1", item.name)
            flushCache()
            val row = Items.selectAll().single()
            assertEquals("name1", row[Items.name])
            assertEquals("note0", row[Items.note])
        }
    }

    @Test
    fun testNestedRollbackAfterFlushKeepsOuterFlushedWrite() {
        withTables(Items, configure = { useNestedTransactions = true }) {
            val item = Item.new {
                name = "name0"
                note = "note0"
            }
            flushCache()

            item.name = "name1"
            flushCache()

            runCatching {
                suspendTransaction {
                    Item.attach(item)
                    item.note = "note2"
                    flushCache()
                    throw Boom()
                }
            }

            assertEquals("name1", item.name)
            assertEquals("note0", item.note)

            val row = Items.selectAll().single()
            assertEquals("name1", row[Items.name])
            assertEquals("note0", row[Items.note])
        }
    }

    @Test
    fun testNestedCommitHandsWritesToOuterTransaction() {
        withTables(Items, configure = { useNestedTransactions = true }) {
            val item = Item.new {
                name = "name0"
                note = "note0"
            }
            flushCache()

            suspendTransaction {
                Item.attach(item)
                item.name = "name1"
            }

            assertEquals("name1", item.name)
            assertEquals("name1", Items.selectAll().single()[Items.name])
        }
    }

    @Test
    fun testNestedTransactionSeesOuterPendingWrite() {
        withTables(Items, configure = { useNestedTransactions = true }) {
            val item = Item.new {
                name = "name0"
                note = "note0"
            }
            flushCache()

            item.name = "name1"

            suspendTransaction {
                Item.attach(item)
                assertEquals("name1", item.name)
            }
        }
    }

    @Test
    fun testNestedCommitThenOuterRollbackDiscardsWrite() {
        withTables(Items, configure = { useNestedTransactions = true }) {
            val item = newItem()

            runCatching {
                newTransaction {
                    maxAttempts = 1
                    suspendTransaction {
                        Item.attach(item)
                        item.name = "changed"
                    }
                    assertEquals("changed", item.name)
                    throw Boom()
                }
            }

            newTransaction {
                maxAttempts = 1
                assertEquals("original", item.name)
                assertEquals("original", Items.selectAll().single()[Items.name])
            }
        }
    }

    @Test
    fun testRereadingOwnFlushedWriteDoesNotCommitIt() {
        withTables(Items) {
            val item = newItem()

            runCatching {
                newTransaction {
                    maxAttempts = 1
                    Item.attach(item)
                    item.name = "changed"
                    flushCache()
                    // Re-reads the row into the very instance this transaction has attached.
                    assertEquals("changed", Item.all().toList().single().name)
                    throw Boom()
                }
            }

            newTransaction {
                maxAttempts = 1
                assertEquals("original", item.name)
            }
        }
    }
}
