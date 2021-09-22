package org.jetbrains.exposed.sql.tests.h2

import org.jetbrains.exposed.dao.load
import org.jetbrains.exposed.dao.with
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.SizedCollection
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.tests.DatabaseTestsBase
import org.jetbrains.exposed.sql.tests.TestDB
import org.jetbrains.exposed.sql.tests.shared.assertEqualCollections
import org.jetbrains.exposed.sql.tests.shared.assertEquals
import org.jetbrains.exposed.sql.tests.shared.entities.EntityTests
import org.jetbrains.exposed.sql.tests.shared.entities.EntityTestsData
import org.jetbrains.exposed.sql.tests.shared.entities.VNumber
import org.jetbrains.exposed.sql.tests.shared.entities.VString
import org.jetbrains.exposed.sql.tests.shared.entities.ViaTestData
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.Assume
import org.junit.Test
import kotlin.properties.Delegates
import kotlin.test.assertEquals
import kotlin.test.assertFails

class EntityReferenceCacheTest : DatabaseTestsBase() {

    private val db by lazy {
        TestDB.H2.connect()
    }

    private val dbWithCache by lazy {
        TestDB.H2.connect{
            keepLoadedReferenceOutOfTransaction = true
        }
    }

    private fun executeOnH2(vararg tables: Table, body: ()->Unit) {
        var testWasStarted = false
        transaction (db) {
            SchemaUtils.create(*tables)
            testWasStarted = true
        }
        Assume.assumeTrue(testWasStarted)
        if (testWasStarted) {
            try {
                body()
            } finally {
                transaction(db) {
                    SchemaUtils.drop(*tables)
                }
            }
        }
    }

    @Test
    fun `test referenceOn works out of transaction`() {
        var y1: EntityTestsData.YEntity by Delegates.notNull()
        var b1: EntityTestsData.BEntity by Delegates.notNull()
        executeOnH2(EntityTestsData.XTable, EntityTestsData.YTable) {
            transaction(db) {
                y1 = EntityTestsData.YEntity.new {
                    this.x = true
                }
                b1 = EntityTestsData.BEntity.new {
                    this.b1 = true
                    this.y = y1
                }
            }
            assertFails { y1.b }
            assertFails { b1.y }

            transaction(dbWithCache) {
                y1.refresh()
                b1.refresh()
                assertEquals(b1.id, y1.b?.id)
                assertEquals(y1.id, b1.y?.id)
            }

            assertEquals(b1.id, y1.b?.id)
            assertEquals(y1.id, b1.y?.id)
        }
    }

    @Test
    fun `test referenceOn works out of transaction via with`() {
        var b1: EntityTests.Board by Delegates.notNull()
        var p1: EntityTests.Post by Delegates.notNull()
        var p2: EntityTests.Post by Delegates.notNull()
        executeOnH2(EntityTests.Boards, EntityTests.Posts) {
            transaction(db) {
                b1 = EntityTests.Board.new {
                    name = "test-board"
                }
                p1 = EntityTests.Post.new {
                    board = b1
                }
                p2 = EntityTests.Post.new {
                    board = b1
                }
            }
        assertFails { b1.posts.toList() }
        assertFails { p1.board?.id }
        assertFails { p2.board?.id }

        transaction(dbWithCache) {
            b1.refresh()
            p1.refresh()
            p2.refresh()
            listOf(p1, p2).with(EntityTests.Post::board)
        }

        assertEquals(b1.id, p1.board?.id)
        assertEquals(b1.id, p2.board?.id)
    }
    }

    @Test
    fun `test referrersOn works out of transaction`() {
        var b1: EntityTests.Board by Delegates.notNull()
        var p1: EntityTests.Post by Delegates.notNull()
        var p2: EntityTests.Post by Delegates.notNull()
        executeOnH2(EntityTests.Boards, EntityTests.Posts) {
            transaction(db) {

                b1 = EntityTests.Board.new {
                    name = "test-board"
                }
                p1 = EntityTests.Post.new {
                    board = b1
                }
                p2 = EntityTests.Post.new {
                    board = b1
                }
            }

            assertFails { b1.posts.toList() }
            assertFails { p1.board?.id }
            assertFails { p2.board?.id }

            transaction(dbWithCache) {
                b1.refresh()
                p1.refresh()
                p2.refresh()
                assertEquals(b1.id, p1.board?.id)
                assertEquals(b1.id, p2.board?.id)
                assertEqualCollections(b1.posts.map { it.id }, p1.id, p2.id)
            }

            assertEquals(b1.id, p1.board?.id)
            assertEquals(b1.id, p2.board?.id)
            assertEqualCollections(b1.posts.map { it.id }, p1.id, p2.id)
        }
    }

    @Test
    fun `test referrersOn works out of transaction via warmup`() {
        var b1: EntityTests.Board by Delegates.notNull()
        var p1: EntityTests.Post by Delegates.notNull()
        var p2: EntityTests.Post by Delegates.notNull()
        executeOnH2(EntityTests.Boards, EntityTests.Posts) {
            transaction(db) {
                b1 = EntityTests.Board.new {
                    name = "test-board"
                }
                p1 = EntityTests.Post.new {
                    board = b1
                }
                p2 = EntityTests.Post.new {
                    board = b1
                }
            }
            assertFails { b1.posts.toList() }
            assertFails { p1.board?.id }
            assertFails { p2.board?.id }

            transaction(dbWithCache) {
                b1.refresh()
                p1.refresh()
                p2.refresh()
                b1.load(EntityTests.Board::posts)
                assertEqualCollections(b1.posts.map { it.id }, p1.id, p2.id)
            }

            assertEqualCollections(b1.posts.map { it.id }, p1.id, p2.id)
        }
    }

    @Test
    fun `test via reference out of transaction`() {
        var n: VNumber by Delegates.notNull()
        var s1: VString by Delegates.notNull()
        var s2: VString by Delegates.notNull()
        executeOnH2(*ViaTestData.allTables) {
            transaction(db) {
                n = VNumber.new { number = 10 }
                s1 = VString.new { text = "aaa" }
                s2 = VString.new { text = "bbb" }
                n.connectedStrings = SizedCollection(s1, s2)
            }

            assertFails { n.connectedStrings.toList() }
            transaction(dbWithCache) {
                n.refresh()
                s1.refresh()
                s2.refresh()
                assertEqualCollections(n.connectedStrings.map { it.id }, s1.id, s2.id)
            }
            assertEqualCollections(n.connectedStrings.map { it.id }, s1.id, s2.id)
        }
    }

    @Test
    fun `test via reference load out of transaction`() {
        var n: VNumber by Delegates.notNull()
        var s1: VString by Delegates.notNull()
        var s2: VString by Delegates.notNull()
        executeOnH2(*ViaTestData.allTables) {
            transaction(db) {
                n = VNumber.new { number = 10 }
                s1 = VString.new { text = "aaa" }
                s2 = VString.new { text = "bbb" }
                n.connectedStrings = SizedCollection(s1, s2)
            }

            assertFails { n.connectedStrings.toList() }
            transaction(dbWithCache) {
                n.refresh()
                s1.refresh()
                s2.refresh()
                n.load(VNumber::connectedStrings)
                assertEqualCollections(n.connectedStrings.map { it.id }, s1.id, s2.id)
            }
            assertEqualCollections(n.connectedStrings.map { it.id }, s1.id, s2.id)
        }
    }
}
