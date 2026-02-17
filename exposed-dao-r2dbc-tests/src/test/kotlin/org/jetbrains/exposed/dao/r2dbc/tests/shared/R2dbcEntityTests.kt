package org.jetbrains.exposed.dao.r2dbc.tests.shared

import org.jetbrains.exposed.r2dbc.dao.R2dbcEntity
import org.jetbrains.exposed.r2dbc.dao.R2dbcEntityClass
import org.jetbrains.exposed.r2dbc.dao.R2dbcIntEntity
import org.jetbrains.exposed.r2dbc.dao.R2dbcIntEntityClass
import org.jetbrains.exposed.r2dbc.dao.R2dbcLongEntity
import org.jetbrains.exposed.r2dbc.dao.R2dbcLongEntityClass
import org.jetbrains.exposed.r2dbc.dao.flushCache
import org.jetbrains.exposed.r2dbc.dao.relationships.backReferencedOnSuspend
import org.jetbrains.exposed.r2dbc.dao.relationships.optionalBackReferencedOnSuspend
import org.jetbrains.exposed.r2dbc.dao.relationships.optionalReferencedOnSuspend
import org.jetbrains.exposed.r2dbc.dao.relationships.optionalReferrersOnSuspend
import org.jetbrains.exposed.r2dbc.dao.relationships.referencedOnSuspend
import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.IdTable
import org.jetbrains.exposed.v1.core.dao.id.IntIdTable
import org.jetbrains.exposed.v1.core.dao.id.LongIdTable
import org.jetbrains.exposed.v1.r2dbc.SchemaUtils
import org.jetbrains.exposed.v1.r2dbc.tests.R2dbcDatabaseTestsBase
import org.jetbrains.exposed.v1.r2dbc.tests.TestDB
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull

object EntityTestsData {

    object YTable : IdTable<String>("YTable") {
        override val id: Column<EntityID<String>> = varchar("uuid", 36).entityId().clientDefault {
            EntityID(UUID.randomUUID().toString(), YTable)
        }

        val x = bool("x").default(true)

        override val primaryKey = PrimaryKey(id)
    }

    object XTable : IntIdTable("XTable") {
        val b1 = bool("b1").default(true)
        val b2 = bool("b2").default(false)
        val y1 = optReference("y1", YTable)
    }

    class XEntity(id: EntityID<Int>) : R2dbcEntity<Int>(id) {
        var b1 by XTable.b1
        var b2 by XTable.b2

        companion object : R2dbcEntityClass<Int, XEntity>(XTable)
    }

    enum class XType {
        A, B
    }

    open class AEntity(id: EntityID<Int>) : R2dbcIntEntity(id) {
        var b1 by XTable.b1

        companion object : R2dbcIntEntityClass<AEntity>(XTable) {
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

    class BEntity(id: EntityID<Int>) : AEntity(id) {
        var b2 by XTable.b2
        val y by YEntity optionalReferencedOnSuspend XTable.y1

        companion object : R2dbcIntEntityClass<BEntity>(XTable) {
            fun create(init: AEntity.() -> Unit): BEntity {
                val answer = new {
                    init()
                }
                return answer
            }
        }
    }

    class YEntity(id: EntityID<String>) : R2dbcEntity<String>(id) {
        var x by YTable.x
        val b by BEntity backReferencedOnSuspend XTable.y1
        val bOpt by BEntity optionalBackReferencedOnSuspend XTable.y1

        companion object : R2dbcEntityClass<String, YEntity>(YTable)
    }
}

class R2dbcEntityTests : R2dbcDatabaseTestsBase() {
    @Test
    fun testDefaults01() {
        withTables(EntityTestsData.YTable, EntityTestsData.XTable) {
            val x = EntityTestsData.XEntity.new { }
            assertEquals(x.b1, true, "b1 mismatched")
            assertEquals(x.b2, false, "b2 mismatched")
        }
    }

    @Test
    fun testDefaults02() {
        withTables(EntityTestsData.YTable, EntityTestsData.XTable) {
            val a: EntityTestsData.AEntity = EntityTestsData.AEntity.create(false, EntityTestsData.XType.A)
            val b: EntityTestsData.BEntity = EntityTestsData.AEntity.create(false, EntityTestsData.XType.B) as EntityTestsData.BEntity
            val y = EntityTestsData.YEntity.new { x = false }

            assertEquals(a.b1, false, "a.b1 mismatched")
            assertEquals(b.b1, false, "b.b1 mismatched")
            assertEquals(b.b2, false, "b.b2 mismatched")

            b.y set y

            assertFalse(b.y()!!.x)
            assertNotNull(y.b())
        }
    }

    @Test
    fun testTextFieldOutsideTheTransaction() {
        val objectsToVerify = arrayListOf<Pair<Human, TestDB>>()
        withTables(Humans) { testDb ->
            val y1 = Human.new {
                h = "foo"
            }

            flushCache()
            y1.refresh(flush = false)

            objectsToVerify.add(y1 to testDb)
        }
        objectsToVerify.forEach { (human, testDb) ->
            assertEquals("foo", human.h, "Failed on ${testDb.name}")
        }
    }

    @Test
    fun testNewWithIdAndRefresh() {
        val objectsToVerify = arrayListOf<Pair<Human, TestDB>>()
        withTables(listOf(TestDB.SQLSERVER), Humans) { testDb ->
            val x = Human.new(2) {
                h = "foo"
            }
            x.refresh(flush = true)
            objectsToVerify.add(x to testDb)
        }
        objectsToVerify.forEach { (human, testDb) ->
            assertEquals("foo", human.h, "Failed on ${testDb.name}")
            assertEquals(2, human.id.value, "Failed on ${testDb.name}")
        }
    }

    internal object OneAutoFieldTable : IntIdTable("single")
    internal class SingleFieldEntity(id: EntityID<Int>) : R2dbcIntEntity(id) {
        companion object : R2dbcIntEntityClass<SingleFieldEntity>(OneAutoFieldTable)
    }

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
            val y = EntityTestsData.YEntity.new { }
            flushCache()
            val b = EntityTestsData.BEntity.new { }
            b.y set y
            assertEquals(b, y.b())
        }
    }

    @Test
    fun testBackReference02() {
        withTables(EntityTestsData.YTable, EntityTestsData.XTable) {
            val b = EntityTestsData.BEntity.new { }
            flushCache()
            val y = EntityTestsData.YEntity.new { }
            b.y set y
            assertEquals(b, y.b())
        }
    }

    object Boards : IntIdTable(name = "board") {
        val name = varchar("name", 255).index(isUnique = true)
    }

    object Posts : LongIdTable(name = "posts") {
        val board = optReference("board", Boards.id)
        val parent = optReference("parent", this)
        val category = optReference("category", Categories.uniqueId).uniqueIndex()
        val optCategory = optReference("optCategory", Categories.uniqueId)
    }

    object Categories : IntIdTable() {
        val uniqueId = uuid("uniqueId").autoGenerate().uniqueIndex()
        val title = varchar("title", 50)
    }

    class Board(id: EntityID<Int>) : R2dbcIntEntity(id) {
        companion object : R2dbcIntEntityClass<Board>(Boards)

        var name by Boards.name
        val posts by Post optionalReferrersOnSuspend Posts.board
    }

    class Post(id: EntityID<Long>) : R2dbcLongEntity(id) {
        companion object : R2dbcLongEntityClass<Post>(Posts)

        val board by Board optionalReferencedOnSuspend Posts.board
        val parent by Post optionalReferencedOnSuspend Posts.parent
        val category by Category optionalReferencedOnSuspend Posts.category
        val optCategory by Category optionalReferencedOnSuspend Posts.optCategory
    }

    class Category(id: EntityID<Int>) : R2dbcIntEntity(id) {
        companion object : R2dbcIntEntityClass<Category>(Categories)

        val uniqueId by Categories.uniqueId
        var title by Categories.title
        val posts by Post optionalReferrersOnSuspend Posts.optCategory

        override fun equals(other: Any?) = (other as? Category)?.id?.equals(id) == true
        override fun hashCode() = id.value.hashCode()
    }

    @Test
    fun tableSelfReferenceTest() {
        assertEquals(listOf(Boards, Categories, Posts), SchemaUtils.sortTablesByReferences(listOf(Posts, Boards, Categories)))
        assertEquals(listOf(Categories, Boards, Posts), SchemaUtils.sortTablesByReferences(listOf(Categories, Posts, Boards)))
        assertEquals(listOf(Boards, Categories, Posts), SchemaUtils.sortTablesByReferences(listOf(Posts)))
    }

    @Test
    fun testInsertChildWithoutFlush() {
        withTables(Boards, Posts, Categories) {
            val parent = Post.new { this.category set Category.new { title = "title" } }
            Post.new { this.parent set parent } // first flush before referencing
            assertEquals(2L, Post.all().count())
        }
    }

    object Humans : IntIdTable("human") {
        val h = text("h", eagerLoading = true)
    }

    object Users : IdTable<Int>("user") {
        override val id: Column<EntityID<Int>> = reference("id", Humans)
        val name = text("name")
    }

    open class Human(id: EntityID<Int>) : R2dbcIntEntity(id) {
        companion object : R2dbcIntEntityClass<Human>(Humans)

        var h by Humans.h
    }

    class User(id: EntityID<Int>) : R2dbcIntEntity(id) {
        companion object : R2dbcIntEntityClass<User>(Users) {
            fun create(name: String): User {
                val h = Human.new { h = name.take(2) }
                return User.new(h.id.value) {
                    this.name = name
                }
            }
        }

        val human by Human referencedOnSuspend Users.id
        var name by Users.name
    }
}
