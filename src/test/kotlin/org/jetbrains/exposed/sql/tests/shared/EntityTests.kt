package org.jetbrains.exposed.sql.tests.shared

import org.jetbrains.exposed.dao.*
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.tests.DatabaseTestsBase
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.junit.Test
import java.util.*
import java.util.concurrent.atomic.AtomicInteger
import javax.sql.rowset.serial.SerialBlob
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull

object EntityTestsData {

    object YTable: IdTable<String>("YTable") {
        override val id: Column<EntityID<String>> = varchar("uuid", 36).primaryKey().entityId().clientDefault {
            EntityID(UUID.randomUUID().toString(), YTable)
        }

        val x = bool("x").default(true)
        val blob = blob("content").nullable()
    }

    object XTable: IntIdTable("XTable") {
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
        val defaultedInt = integer("i1")
    }

    class NotAutoEntity(id: EntityID<Int>) : Entity<Int>(id) {
        var b1 by NotAutoIntIdTable.b1
        var defaultedInNew by NotAutoIntIdTable.defaultedInt

        companion object : EntityClass<Int, NotAutoEntity>(NotAutoIntIdTable) {
            val lastId = AtomicInteger(0)
            internal const val defaultInt = 42
            fun new(b: Boolean) = new(lastId.incrementAndGet()) { b1 = b }

            override fun new(id: Int?, init: NotAutoEntity.() -> Unit): NotAutoEntity {
                return super.new(id ?: lastId.incrementAndGet()) {
                    defaultedInNew = defaultInt
                    init()
                }
            }
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
        var content by YTable.blob
        companion object : EntityClass<String, YEntity>(YTable)
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

    @Test fun testDefaultsWithOverrideNew() {
        withTables(EntityTestsData.NotAutoIntIdTable) {
            val entity1 = EntityTestsData.NotAutoEntity.new(true)
            assertEquals(true, entity1.b1)
            assertEquals(EntityTestsData.NotAutoEntity.defaultInt, entity1.defaultedInNew)

            val entity2 = EntityTestsData.NotAutoEntity.new {
                b1 = false
                defaultedInNew = 1
            }
            assertEquals(false, entity2.b1)
            assertEquals(1, entity2.defaultedInNew)
        }
    }

    @Test fun testBlobField() {
        withTables(EntityTestsData.YTable) {
            val y1 = EntityTestsData.YEntity.new {
                x = false
                content = SerialBlob("foo".toByteArray())
            }

            flushCache()
            var y2 = EntityTestsData.YEntity.reload(y1)!!
            assertEquals(String(y2.content!!.binaryStream.readBytes()), "foo")

            y2.content = null
            flushCache()
            y2 = EntityTestsData.YEntity.reload(y1)!!
            assertNull(y2.content)

            y2.content = SerialBlob("foo2".toByteArray())
            flushCache()
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

    internal object OneAutoFieldTable : IntIdTable("single")
    internal class SingleFieldEntity(id: EntityID<Int>) : IntEntity(id) {
        companion object : IntEntityClass<SingleFieldEntity>(OneAutoFieldTable)
    }

    //GitHub issue #95: Dao new{ } with no values problem "NoSuchElementException: List is empty"
    @Test
    fun testOneFieldEntity() {
        withTables(OneAutoFieldTable) {
            val new = SingleFieldEntity.new { }
            commit()
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

    object Posts : LongIdTable(name = "posts") {
        val board = optReference("board", Boards)
        val parent = optReference("parent", this)
    }

    class Board(id: EntityID<Int>): IntEntity(id) {
        companion object : IntEntityClass<Board>(Boards)

        var name by Boards.name
        val posts by Post.optionalReferrersOn(Posts.board)
    }

    class Post(id: EntityID<Long>): LongEntity(id) {
        companion object : LongEntityClass<Post>(Posts)

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
            Post.new { this.parent = parent } // first flush before referencing
            assertEquals(1, flushCache().size)
        }
    }

    @Test
    fun testInsertNonChildWithoutFlush() {
        withTables(Boards, Posts) {
            val board = Board.new { name = "irrelevant" }
            Post.new { this.board = board } // first flush before referencing
            assertEquals(1, flushCache().size)
        }
    }

    @Test
    fun testInsertChildWithFlush() {
        withTables(Posts) {
            val parent = Post.new {  }
            flushCache()
            assertNotNull(parent.id._value)
            Post.new { this.parent = parent }
            assertEquals(1, flushCache().size)
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

    @Test
    fun testOptionalReferrersWithDifferentKeys() {
        withTables(Boards, Posts) {
            val board = Board.new { name = "irrelevant" }
            val post1 = Post.new { this.board = board }
            assertEquals(1, board.posts.count())
            assertEquals(post1, board.posts.single())

            Post.new { this.board = board }
            assertEquals(2, board.posts.count())
        }
    }


    object Humans : IntIdTable("human") {
        val h = text("h")
    }

    object Users : IdTable<Int>("user") {
        override val id: Column<EntityID<Int>> = reference("id", Humans)
        val name = text("name")
    }

    open class Human (id: EntityID<Int>) : IntEntity(id) {
        companion object : IntEntityClass<Human>(Humans)
        var h by Humans.h
    }

    class User(id: EntityID<Int>) : IntEntity(id) {
        companion object : IntEntityClass<User>(Users){
            fun create(name: String): User {
                val h = Human.new { h = name.take(2) }
                return User.new(h.id.value) {
                    this.name = name
                }
            }
        }

        var human by Human referencedOn Users.id
        var name by Users.name
    }

    @Test
    fun testOneToOneReference() {
        withTables(Humans, Users) {
            val user = User.create("testUser")
            assertEquals("te", user.human.h)
            assertEquals("testUser", user.name)
            assertEquals(user.id.value, user.human.id.value)
        }
    }

}
                                                            