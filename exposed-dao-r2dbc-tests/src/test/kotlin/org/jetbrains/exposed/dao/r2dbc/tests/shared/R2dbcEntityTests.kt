package org.jetbrains.exposed.dao.r2dbc.tests.shared

import io.r2dbc.spi.IsolationLevel
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.single
import kotlinx.coroutines.flow.toList
import org.jetbrains.exposed.r2dbc.dao.IntR2dbcEntity
import org.jetbrains.exposed.r2dbc.dao.IntR2dbcEntityClass
import org.jetbrains.exposed.r2dbc.dao.LongR2dbcEntity
import org.jetbrains.exposed.r2dbc.dao.LongR2dbcEntityClass
import org.jetbrains.exposed.r2dbc.dao.R2dbcEntity
import org.jetbrains.exposed.r2dbc.dao.R2dbcEntityClass
import org.jetbrains.exposed.r2dbc.dao.exceptions.R2dbcEntityNotFoundException
import org.jetbrains.exposed.r2dbc.dao.flushCache
import org.jetbrains.exposed.r2dbc.dao.relationships.backReferencedOnSuspend
import org.jetbrains.exposed.r2dbc.dao.relationships.optionalBackReferencedOnSuspend
import org.jetbrains.exposed.r2dbc.dao.relationships.optionalReferencedOnSuspend
import org.jetbrains.exposed.r2dbc.dao.relationships.optionalReferrersOnSuspend
import org.jetbrains.exposed.r2dbc.dao.relationships.referencedOnSuspend
import org.jetbrains.exposed.r2dbc.dao.relationships.referrersOnSuspend
import org.jetbrains.exposed.r2dbc.dao.relationships.with
import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.StdOutSqlLogger
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.IdTable
import org.jetbrains.exposed.v1.core.dao.id.IntIdTable
import org.jetbrains.exposed.v1.core.dao.id.LongIdTable
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.r2dbc.R2dbcTransaction
import org.jetbrains.exposed.v1.r2dbc.SchemaUtils
import org.jetbrains.exposed.v1.r2dbc.SizedIterable
import org.jetbrains.exposed.v1.r2dbc.batchUpsert
import org.jetbrains.exposed.v1.r2dbc.deleteAll
import org.jetbrains.exposed.v1.r2dbc.deleteWhere
import org.jetbrains.exposed.v1.r2dbc.selectAll
import org.jetbrains.exposed.v1.r2dbc.tests.R2dbcDatabaseTestsBase
import org.jetbrains.exposed.v1.r2dbc.tests.TestDB
import org.jetbrains.exposed.v1.r2dbc.tests.shared.assertEqualLists
import org.jetbrains.exposed.v1.r2dbc.tests.shared.expectException
import org.jetbrains.exposed.v1.r2dbc.transactions.inTopLevelSuspendTransaction
import org.jetbrains.exposed.v1.r2dbc.update
import org.jetbrains.exposed.v1.r2dbc.upsert
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.assertNull
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertSame

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

    open class AEntity(id: EntityID<Int>) : IntR2dbcEntity(id) {
        var b1 by XTable.b1

        companion object : IntR2dbcEntityClass<AEntity>(XTable) {
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

        companion object : IntR2dbcEntityClass<BEntity>(XTable) {
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
    internal class SingleFieldR2dbcEntity(id: EntityID<Int>) : IntR2dbcEntity(id) {
        companion object : IntR2dbcEntityClass<SingleFieldR2dbcEntity>(OneAutoFieldTable)
    }

    @Test
    fun testOneFieldEntity() {
        withTables(OneAutoFieldTable) {
            val new = SingleFieldR2dbcEntity.new { }
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

    object Items : IntIdTable("items") {
        val name = varchar("name", 255).uniqueIndex()
        val price = double("price")
    }

    class Item(id: EntityID<Int>) : IntR2dbcEntity(id) {
        companion object : IntR2dbcEntityClass<Item>(Items)

        var name by Items.name
        var price by Items.price
    }

    @Test
    fun testCacheInvalidatedOnDSLUpsert() {
        withTables(Items) { testDb ->
            val oldPrice = 20.0
            val itemA = Item.new {
                name = "Item A"
                price = oldPrice
            }
            assertEquals(oldPrice, itemA.price)
            assertNotNull(Item.testCache(itemA.id))

            val newPrice = 50.0
            val conflictKeys = if (testDb in TestDB.ALL_MYSQL_LIKE) emptyArray<Column<*>>() else arrayOf(Items.name)
            Items.upsert(*conflictKeys) {
                it[name] = itemA.name
                it[price] = newPrice
            }
            assertEquals(oldPrice, itemA.price)
            assertNull(Item.testCache(itemA.id))

            itemA.refresh(flush = false)
            assertEquals(newPrice, itemA.price)
            assertNotNull(Item.testCache(itemA.id))

            val newPricePlusExtra = 100.0
            val newItems = List(5) { i -> "Item ${'A' + i}" to newPricePlusExtra }
            Items.batchUpsert(newItems, *conflictKeys, shouldReturnGeneratedValues = false) { (name, price) ->
                this[Items.name] = name
                this[Items.price] = price
            }
            assertEquals(newPrice, itemA.price)
            assertNull(Item.testCache(itemA.id))

            itemA.refresh(flush = false)
            assertEquals(newPricePlusExtra, itemA.price)
            assertNotNull(Item.testCache(itemA.id))
        }
    }

    @Test
    fun testDaoFindByIdAndUpdate() {
        withTables(Items) {
            val oldPrice = 20.0
            val item = Item.new {
                name = "Item A"
                price = oldPrice
            }
            assertEquals(oldPrice, item.price)
            assertNotNull(Item.testCache(item.id))

            val newPrice = 50.0
            flushCache()
            val updatedItem = Item.findByIdAndUpdate(item.id.value) {
                it.price = newPrice
            }

            assertSame(updatedItem, item)

            assertNotNull(updatedItem)
            assertEquals(newPrice, updatedItem.price)
            assertNotNull(Item.testCache(item.id))

            assertEquals(newPrice, item.price)
            item.refresh(flush = false)
            assertEquals(oldPrice, item.price)
            assertNotNull(Item.testCache(item.id))
        }
    }

    @Test
    fun testDaoFindSingleByAndUpdate() {
        withTables(Items) {
            val oldPrice = 20.0
            val item = Item.new {
                name = "Item A"
                price = oldPrice
            }
            assertEquals(oldPrice, item.price)
            assertNotNull(Item.testCache(item.id))

            val newPrice = 50.0
            val updatedItem = Item.findSingleByAndUpdate(Items.name eq "Item A") {
                it.price = newPrice
            }

            assertSame(updatedItem, item)

            assertNotNull(updatedItem)
            assertEquals(newPrice, updatedItem.price)
            assertNotNull(Item.testCache(item.id))

            assertEquals(newPrice, item.price)
            item.refresh(flush = false)
            assertEquals(oldPrice, item.price)
            assertNotNull(Item.testCache(item.id))
        }
    }

    private object SelfReferenceTable : IntIdTable() {
        val parentId = optReference("parent", SelfReferenceTable)
    }

    class SelfReferencedEntity(id: EntityID<Int>) : IntR2dbcEntity(id) {
        var parent by SelfReferenceTable.parentId

        companion object : IntR2dbcEntityClass<SelfReferencedEntity>(SelfReferenceTable)
    }

    @Test
    @Timeout(value = 5000, unit = TimeUnit.MILLISECONDS)
    fun testSelfReferences() {
        withTables(SelfReferenceTable) {
            val ref1 = SelfReferencedEntity.new { }
            ref1.parent = ref1.id
            val refRow = SelfReferenceTable.selectAll().where { SelfReferenceTable.id eq ref1.id }.single()
            assertEquals(ref1.id._value, refRow[SelfReferenceTable.parentId]!!.value)
        }
    }

    @Test
    fun testNonEntityIdReference() {
        withTables(Posts, Boards, Categories) {
            val category1 = Category.new {
                title = "cat1"
            }

            val post1 = Post.new {
                optCategory set category1
                category set Category.new { title = "title" }
            }

            val post2 = Post.new {
                optCategory set category1
                parent set post1
            }

            assertEquals(2L, Post.all().count())
            assertEquals(2, category1.posts().count())
            assertEquals(2L, Posts.selectAll().where { Posts.optCategory eq category1.uniqueId }.count())
        }
    }

    // https://github.com/JetBrains/Exposed/issues/439
    @Test
    fun callLimitOnRelationDoesntMutateTheCachedValue() {
        withTables(Posts, Boards, Categories) {
            addLogger(StdOutSqlLogger) // this is left in on purpose for flaky tests
            val category1 = Category.new {
                title = "cat1"
            }

            Post.new {
                optCategory set category1
                category set Category.new { title = "title" }
            }

            Post.new {
                optCategory set category1
            }
            commit()

            assertEquals(2, category1.posts().count())
            assertEquals(2, category1.posts().toList().size)
            assertEquals(1, category1.posts().limit(1).toList().size)
            assertEquals(1L, category1.posts().limit(1).count())
            assertEquals(2, category1.posts().count())
            assertEquals(2, category1.posts().toList().size)
        }
    }

    @Test
    fun testOrderByOnEntities() {
        withTables(Categories) {
            Categories.deleteAll()
            val category1 = Category.new { title = "Test1" }
            val category3 = Category.new { title = "Test3" }
            val category2 = Category.new { title = "Test2" }

            assertEqualLists(listOf(category1, category3, category2), Category.all().toList())
            assertEqualLists(listOf(category1, category2, category3), Category.all().orderBy(Categories.title to SortOrder.ASC).toList())
            assertEqualLists(listOf(category3, category2, category1), Category.all().orderBy(Categories.title to SortOrder.DESC).toList())
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

    class Board(id: EntityID<Int>) : IntR2dbcEntity(id) {
        companion object : IntR2dbcEntityClass<Board>(Boards)

        var name by Boards.name
        val posts by Post optionalReferrersOnSuspend Posts.board
    }

    class Post(id: EntityID<Long>) : LongR2dbcEntity(id) {
        companion object : LongR2dbcEntityClass<Post>(Posts)

        val board by Board optionalReferencedOnSuspend Posts.board
        val parent by Post optionalReferencedOnSuspend Posts.parent
        val category by Category optionalReferencedOnSuspend Posts.category
        val optCategory by Category optionalReferencedOnSuspend Posts.optCategory
    }

    class Category(id: EntityID<Int>) : IntR2dbcEntity(id) {
        companion object : IntR2dbcEntityClass<Category>(Categories)

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

    @Test
    fun testInsertNonChildWithoutFlush() {
        withTables(Boards, Posts, Categories) {
            val board = Board.new { name = "irrelevant" }
            Post.new { this.board set board } // first flush before referencing
            // In JDBC DAO, setting a reference triggers an implicit flush of the referenced entity
            // (via EntityID.value access). In R2DBC, set is synchronous and cannot trigger a suspend flush,
            // so both entities remain in the insert cache.
            assertEquals(2, flushCache().size)
        }
    }

    @Test
    fun testThatQueriesWithinOtherQueryIteratorWorksFine() {
        withTables(Boards, Posts, Categories) {
            val board1 = Board.new { name = "irrelevant" }
            val board2 = Board.new { name = "relevant" }
            val post1 = Post.new { this.board set board1 }

            Board.all().forEach {
                it.posts().count() to it.posts
                Post.find { Posts.board eq it.id }.toList()
                    .map { post -> post.board()?.name.orEmpty() }
                    .joinToString()
            }
        }
    }

    @Test
    fun testInsertChildWithFlush() {
        withTables(Boards, Posts, Categories) {
            val parent = Post.new { this.category set Category.new { title = "title" } }
            flushCache()
            assertNotNull(parent.id._value)
            Post.new { this.parent set parent }
            assertEquals(1, flushCache().size)
        }
    }

    @Test
    fun testInsertChildWithChild() {
        withTables(Boards, Posts, Categories) {
            val parent = Post.new { this.category set Category.new { title = "title1" } }
            val child1 = Post.new {
                this.parent set parent
                this.category set Category.new { title = "title2" }
            }
            Post.new { this.parent set child1 }
            flushCache()
        }
    }

    @Test
    fun testOptionalReferrersWithDifferentKeys() {
        withTables(Boards, Posts, Categories) {
            val board = Board.new { name = "irrelevant" }
            val post1 = Post.new {
                this.board set board
                this.category set Category.new { title = "title" }
            }
            // In R2DBC, entities must be flushed before referrers queries can find them
            flushCache()
            assertEquals(1, board.posts().count())
            assertEquals(post1, board.posts().single())

            Post.new { this.board set board }
            flushCache()
            assertEquals(2, board.posts().count())
        }
    }

    @Test
    fun testErrorOnSetToDeletedEntity() {
        withTables(Boards) {
            expectException<R2dbcEntityNotFoundException> {
                val board = Board.new { name = "irrelevant" }
                board.delete()
                board.name = "Cool"
            }
        }
    }

    @Test
    fun testCacheInvalidatedOnDSLDelete() {
        withTables(Boards) {
            val board1 = Board.new { name = "irrelevant" }
            assertNotNull(Board.testCache(board1.id))
            board1.delete()
            assertNull(Board.testCache(board1.id))

            val board2 = Board.new { name = "irrelevant" }
            assertNotNull(Board.testCache(board2.id))
            // In R2DBC, entity IDs are not auto-flushed on access (unlike JDBC DaoEntityID),
            // so we must flush before using the ID in DSL operations.
            flushCache()
            Boards.deleteWhere { Boards.id eq board2.id }
            assertNull(Board.testCache(board2.id))
        }
    }

    @Test
    fun testCacheInvalidatedOnDSLUpdate() {
        withTables(Boards) {
            val board1 = Board.new { name = "irrelevant" }
            assertNotNull(Board.testCache(board1.id))
            board1.name = "relevant"
            assertEquals("relevant", board1.name)

            val board2 = Board.new { name = "irrelevant2" }
            assertNotNull(Board.testCache(board2.id))
            // In R2DBC, entity IDs are not auto-flushed on access (unlike JDBC DaoEntityID),
            // so we must flush before using the ID in DSL operations.
            flushCache()
            Boards.update({ Boards.id eq board2.id }) {
                it[name] = "relevant2"
            }
            assertNull(Board.testCache(board2.id))
            board2.refresh(flush = false)
            assertNotNull(Board.testCache(board2.id))
            assertEquals("relevant2", board2.name)
        }
    }

    object Humans : IntIdTable("human") {
        val h = text("h", eagerLoading = true)
    }

    object Users : IdTable<Int>("user") {
        override val id: Column<EntityID<Int>> = reference("id", Humans)
        val name = text("name")
    }

    open class Human(id: EntityID<Int>) : IntR2dbcEntity(id) {
        companion object : IntR2dbcEntityClass<Human>(Humans)

        var h by Humans.h
    }

    class User(id: EntityID<Int>) : IntR2dbcEntity(id) {
        companion object : IntR2dbcEntityClass<User>(Users) {
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

    @Test
    fun testThatUpdateOfInsertedEntitiesGoesBeforeAnInsert() {
        withTables(Categories, Posts, Boards) {
            val category1 = Category.new {
                title = "category1"
            }

            val category2 = Category.new {
                title = "category2"
            }

            val post1 = Post.new {
                category set category1
            }

            flushCache()
            assertEquals(post1.category(), category1)

            post1.category set category2

            val post2 = Post.new {
                category set category1
            }

            flushCache()
            Post.reload(post1)
            Post.reload(post2)

            assertEquals(category2, post1.category())
            assertEquals(category1, post2.category())
        }
    }

    object Parents : LongIdTable() {
        val name = varchar("name", 50)
    }

    class Parent(id: EntityID<Long>) : LongR2dbcEntity(id) {
        companion object : LongR2dbcEntityClass<Parent>(Parents)

        var name by Parents.name
    }

    object Children : LongIdTable() {
        val companyId = reference("company_id", Parents)
        val name = varchar("name", 80)
    }

    class Child(id: EntityID<Long>) : LongR2dbcEntity(id) {
        companion object : LongR2dbcEntityClass<Child>(Children)

        val parent by Parent referencedOnSuspend Children.companyId
        var name by Children.name
    }

    @Test
    fun testNewIdWithGet() {
        // SQL Server doesn't support an explicit id for auto-increment table
        withTables(listOf(TestDB.SQLSERVER), Parents, Children) {
            val parentId = Parent.new {
                name = "parent1"
            }.also {
                flushCache()
            }.id.value

            commit()

            val parent = Parent[parentId]
            val child = Child.new(100L) {
                this.parent set parent
                name = "child1"
            }
            child.flush()

            assertEquals(100L, child.id.value)
            assertEquals(parentId, child.parent().id.value)
        }
    }

    @Test
    fun `newly created entity flushed successfully`() {
        withTables(Boards) {
            val board = Board.new { name = "Board1" }.apply {
                assertEquals(true, flush())
            }

            assertEquals("Board1", board.name)
        }
    }

    private suspend fun <T> newTransaction(statement: suspend R2dbcTransaction.() -> T) =
        inTopLevelSuspendTransaction(null, statement = statement)

    @Test
    fun sharingEntityBetweenTransactions() {
        withTables(Humans) {
            val human1 = newTransaction {
                maxAttempts = 1
                Human.new {
                    this.h = "foo"
                }
            }
            newTransaction {
                maxAttempts = 1
                assertEquals(null, Human.testCache(human1.id))
                assertEquals("foo", Humans.selectAll().single()[Humans.h])
                // Unlike JDBC, R2DBC's Column.setValue cannot suspendingly probe the DB to adopt
                // an entity from another transaction. Explicitly attach it first.
                Human.attach(human1)
                human1.h = "bar"
                assertEquals(human1, Human.testCache(human1.id))
                assertEquals("bar", Humans.selectAll().single()[Humans.h])
            }

            newTransaction {
                maxAttempts = 1
                assertEquals("bar", Humans.selectAll().single()[Humans.h])
            }
        }
    }

    object Regions : IntIdTable(name = "region") {
        val name = varchar("name", 255)
    }

    object Students : LongIdTable(name = "students") {
        val name = varchar("name", 255)
        val school = reference("school_id", Schools)
    }

    object StudentBios : LongIdTable(name = "student_bio") {
        val student = reference("student_id", Students).uniqueIndex()
        val dateOfBirth = varchar("date_of_birth", 25)
    }

    object Notes : LongIdTable(name = "notes") {
        val text = varchar("text", 255)
        val student = reference("student_id", Students)
    }

    object Detentions : LongIdTable(name = "detentions") {
        val reason = varchar("reason", 255)
        val student = optReference("student_id", Students)
    }

    object Holidays : LongIdTable(name = "holidays") {
        val holidayStart = long("holiday_start")
        val holidayEnd = long("holiday_end")
    }

    object SchoolHolidays : Table(name = "school_holidays") {
        val school = reference("school_id", Schools, ReferenceOption.CASCADE, ReferenceOption.CASCADE)
        val holiday = reference("holiday_id", Holidays, ReferenceOption.CASCADE, ReferenceOption.CASCADE)

        override val primaryKey = PrimaryKey(school, holiday)
    }

    object Schools : IntIdTable(name = "school") {
        val name = varchar("name", 255).index(isUnique = true)
        val region = reference("region_id", Regions)
        val secondaryRegion = optReference("secondary_region_id", Regions)
    }

    class Region(id: EntityID<Int>) : IntR2dbcEntity(id) {
        companion object : IntR2dbcEntityClass<Region>(Regions)

        var name by Regions.name

        override fun equals(other: Any?): Boolean {
            return (other as? Region)?.id?.equals(id) ?: false
        }

        override fun hashCode(): Int = id.hashCode()
    }

    abstract class ComparableLongEntity<T : LongR2dbcEntity>(id: EntityID<Long>) : LongR2dbcEntity(id) {
        override fun equals(other: Any?): Boolean {
            return (other as? T)?.id?.equals(id) ?: false
        }

        override fun hashCode(): Int = id.hashCode()
    }

    class Student(id: EntityID<Long>) : ComparableLongEntity<Student>(id) {
        companion object : LongR2dbcEntityClass<Student>(Students)

        var name by Students.name
        val school by School referencedOnSuspend Students.school
        val notes by Note.referrersOnSuspend(Notes.student, true)
        val detentions by Detention.optionalReferrersOnSuspend(Detentions.student, true)
        val bio by StudentBio.optionalBackReferencedOnSuspend(StudentBios.student)
    }

    class StudentBio(id: EntityID<Long>) : ComparableLongEntity<StudentBio>(id) {
        companion object : LongR2dbcEntityClass<StudentBio>(StudentBios)

        val student by Student.referencedOnSuspend(StudentBios.student)
        var dateOfBirth by StudentBios.dateOfBirth
    }

    class Note(id: EntityID<Long>) : ComparableLongEntity<Note>(id) {
        companion object : LongR2dbcEntityClass<Note>(Notes)

        var text by Notes.text
        val student by Student referencedOnSuspend Notes.student
    }

    class Detention(id: EntityID<Long>) : ComparableLongEntity<Detention>(id) {
        companion object : LongR2dbcEntityClass<Detention>(Detentions)

        var reason by Detentions.reason
        val student by Student optionalReferencedOnSuspend Detentions.student
    }

    class Holiday(id: EntityID<Long>) : ComparableLongEntity<Holiday>(id) {
        companion object : LongR2dbcEntityClass<Holiday>(Holidays)

        var holidayStart by Holidays.holidayStart
        var holidayEnd by Holidays.holidayEnd
    }

    class School(id: EntityID<Int>) : IntR2dbcEntity(id) {
        companion object : IntR2dbcEntityClass<School>(Schools)

        var name by Schools.name
        val region by Region referencedOnSuspend Schools.region
        val secondaryRegion by Region optionalReferencedOnSuspend Schools.secondaryRegion
        val students by Student.referrersOnSuspend(Students.school, true)
        val holidays by Holiday viaSuspend SchoolHolidays
    }

    @Test
    fun preloadReferencesOnASizedIterable() {
        withTables(Regions, Schools) {
            val region1 = Region.new {
                name = "United Kingdom"
            }

            val region2 = Region.new {
                name = "England"
            }

            val school1 = School.new {
                name = "Eton"
                region set region1
            }

            val school2 = School.new {
                name = "Harrow"
                region set region1
            }

            val school3 = School.new {
                name = "Winchester"
                region set region2
            }

            commit()

            inTopLevelSuspendTransaction(transactionIsolation = IsolationLevel.SERIALIZABLE) {
                School.all().with(School::region)
                assertNotNull(School.testCache(school1.id))
                assertNotNull(School.testCache(school2.id))
                assertNotNull(School.testCache(school3.id))

                assertEquals(region1, Region.testCache(School.testCache(school1.id)!!.readValues[Schools.region]))
                assertEquals(region1, Region.testCache(School.testCache(school2.id)!!.readValues[Schools.region]))
                assertEquals(region2, Region.testCache(School.testCache(school3.id)!!.readValues[Schools.region]))
            }
        }
    }
}

/**
 * This method is used just to keep tests similar to jdbc alternatives
 * (otherwise it's necessary to replace `forEach` with `collect` in all the tests)
 */
internal suspend fun <T> SizedIterable<T>.forEach(collector: FlowCollector<T>) =
    collect(collector)
