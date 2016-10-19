package org.jetbrains.exposed.sql.tests.shared

import org.jetbrains.exposed.dao.*
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.tests.DatabaseTestsBase
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.junit.Test
import java.util.*
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull

object EntityTestsData {

    object YTable: IdTable<String>("") {
        override val id: Column<EntityID<String>> = varchar("uuid", 36).primaryKey().entityId().clientDefault {
            EntityID(UUID.randomUUID().toString(), YTable)
        }

        val x = bool("x").default(true)
    }

    object XTable: IntIdTable() {
        val b1 = bool("b1").default(true)
        val b2 = bool("b2").default(false)
        val y1 = optReference("y1", YTable)
    }

    class XEntity(id: EntityID<Int>): Entity<Int>(id) {
        var b1 by XTable.b1
        var b2 by XTable.b2

        companion object : EntityClass<Int, XEntity>(XTable)
    }

    object NotAutoIntIdTable : IdTable<Int>("") {
        override val id: Column<EntityID<Int>> = integer("id").entityId()
        val b1 = bool("b1")
    }

    class NotAutoEntity(id: EntityID<Int>) : Entity<Int>(id) {
        var b1 by NotAutoIntIdTable.b1

        companion object : EntityClass<Int, NotAutoEntity>(NotAutoIntIdTable) {
            val lastId = AtomicInteger(0)
            fun new(b: Boolean) = new(lastId.incrementAndGet()) { b1 = b }
        }
    }

    enum class XType {
        A, B
    }

    open class AEntity(id: EntityID<Int>): IntEntity(id) {
        var b1 by XTable.b1

        companion object: IntEntityClass<AEntity>(XTable) {
            fun create(b1: Boolean, type: XType): AEntity {
                val init: AEntity.() -> Unit = {
                    this.b1 = b1
                }
                val answer = when (type) {
                    XType.B -> BEntity.create { init() }
                    else -> new { init() }
                }
                return answer
            }
        }
    }

    class BEntity(id: EntityID<Int>): AEntity(id) {
        var b2 by XTable.b2
        var y by YEntity optionalReferencedOn XTable.y1

        companion object: IntEntityClass<BEntity>(XTable) {
            fun create(init: AEntity.() -> Unit): BEntity {
                val answer = new {
                    init()
                }
                return answer
            }
        }
    }

    class YEntity(id: EntityID<String>) : Entity<String>(id) {
        var x by YTable.x
        val b: BEntity? by BEntity.backReferencedOn(XTable.y1)

        companion object : EntityClass<String, YEntity>(YTable) {

        }
    }
}

class EntityTests: DatabaseTestsBase() {
    @Test fun testDefaults01() {
        withTables(EntityTestsData.YTable, EntityTestsData.XTable) {
            val x = EntityTestsData.XEntity.new {  }
            assertEquals (x.b1, true, "b1 mismatched")
            assertEquals (x.b2, false, "b2 mismatched")
        }
    }


    @Test fun testNotAutoIncTable() {
        withTables(EntityTestsData.NotAutoIntIdTable) {
            val e1 = EntityTestsData.NotAutoEntity.new(true)
            val e2 = EntityTestsData.NotAutoEntity.new(false)

            TransactionManager.current().flushCache()

            val all = EntityTestsData.NotAutoEntity.all()
            assert(all.any { it.id == e1.id })
            assert(all.any { it.id == e2.id })
        }
    }

    @Test fun testDefaults02() {
        withTables(EntityTestsData.YTable, EntityTestsData.XTable) {
            val a: EntityTestsData.AEntity = EntityTestsData.AEntity.create(false, EntityTestsData.XType.A)
            assertEquals (a.b1, false, "a.b1 mismatched")

            val b: EntityTestsData.BEntity = EntityTestsData.AEntity.create(false, EntityTestsData.XType.B) as EntityTestsData.BEntity
            val y = EntityTestsData.YEntity.new { x = false }
            assertEquals (b.b1, false, "a.b1 mismatched")
            assertEquals (b.b2, false, "b.b2 mismatched")

            b.y = y

            assertFalse (b.y!!.x)
            assertNotNull(y.b)

        }
    }

    @Test
    fun testBackReference01() {
        withTables(EntityTestsData.YTable, EntityTestsData.XTable) {
            val y = EntityTestsData.YEntity.new {  }
            flushCache()
            val b = EntityTestsData.BEntity.new {  }
            b.y = y
            assertEquals(b, y.b)
        }
    }

    @Test
    fun testBackReference02() {
        withTables(EntityTestsData.YTable, EntityTestsData.XTable) {
            val b = EntityTestsData.BEntity.new {  }
            flushCache()
            val y = EntityTestsData.YEntity.new {  }
            b.y = y
            assertEquals(b, y.b)
        }
    }

    object Boards : IntIdTable(name = "board") {
        val name = varchar("name", 255).index(isUnique = true)
    }

    object Posts : IntIdTable(name = "posts") {
        val board = optReference("board", Boards)
        val parent = optReference("parent", this)
    }

    class Board(id: EntityID<Int>): IntEntity(id) {
        companion object : IntEntityClass<Board>(Boards)

        var name by Boards.name
    }

    class Post(id: EntityID<Int>): IntEntity(id) {
        companion object : IntEntityClass<Post>(Posts)

        var board by Board optionalReferencedOn Posts.board
        var parent by Post optionalReferencedOn Posts.parent
    }

    @Test
    fun tableSelfReferenceTest() {
        assertEquals<List<Table>>(
                EntityCache.sortTablesByReferences(listOf(Posts, Boards)), listOf(Boards, Posts))
    }

    @Test
    fun testInsertChildWithoutFlush() {
        withTables(Posts) {
            val parent = Post.new {  }
            Post.new { this.parent = parent }
            assertEquals(flushCache().size, 2)
        }
    }

    @Test
    fun testInsertNonChildWithoutFlush() {
        withTables(Boards, Posts) {
            val board = Board.new { name = "irrelevant" }
            Post.new { this.board = board }
            assertEquals(flushCache().size, 2)

        }
    }

    @Test
    fun testInsertChildWithFlush() {
        withTables(Posts) {
            val parent = Post.new {  }
            flushCache()
            assertNotNull(parent.id._value)
            Post.new { this.parent = parent }
            assertEquals(flushCache().size, 1)
        }
    }

    @Test
    fun testInsertChildWithChild() {
        withTables(Posts) {
            val parent = Post.new {  }
            val child1 = Post.new { this.parent = parent }
            Post.new { this.parent = child1 }
            flushCache()
        }
    }

}
