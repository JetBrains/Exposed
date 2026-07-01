package org.jetbrains.exposed.dao.r2dbc.tests.shared

import io.r2dbc.spi.IsolationLevel
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.single
import kotlinx.coroutines.flow.toList
import org.jetbrains.exposed.r2dbc.dao.Entity
import org.jetbrains.exposed.r2dbc.dao.EntityClass
import org.jetbrains.exposed.r2dbc.dao.IntEntity
import org.jetbrains.exposed.r2dbc.dao.IntEntityClass
import org.jetbrains.exposed.r2dbc.dao.LongEntity
import org.jetbrains.exposed.r2dbc.dao.LongEntityClass
import org.jetbrains.exposed.r2dbc.dao.entityCache
import org.jetbrains.exposed.r2dbc.dao.exceptions.EntityNotFoundException
import org.jetbrains.exposed.r2dbc.dao.flushCache
import org.jetbrains.exposed.r2dbc.dao.relationships.load
import org.jetbrains.exposed.r2dbc.dao.relationships.with
import org.jetbrains.exposed.v1.core.Case
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
import org.jetbrains.exposed.v1.core.idParam
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.core.vendors.OracleDialect
import org.jetbrains.exposed.v1.r2dbc.SchemaUtils
import org.jetbrains.exposed.v1.r2dbc.SizedCollection
import org.jetbrains.exposed.v1.r2dbc.SizedIterable
import org.jetbrains.exposed.v1.r2dbc.batchUpsert
import org.jetbrains.exposed.v1.r2dbc.deleteAll
import org.jetbrains.exposed.v1.r2dbc.deleteWhere
import org.jetbrains.exposed.v1.r2dbc.insert
import org.jetbrains.exposed.v1.r2dbc.insertAndGetId
import org.jetbrains.exposed.v1.r2dbc.select
import org.jetbrains.exposed.v1.r2dbc.selectAll
import org.jetbrains.exposed.v1.r2dbc.tests.R2dbcDatabaseTestsBase
import org.jetbrains.exposed.v1.r2dbc.tests.TestDB
import org.jetbrains.exposed.v1.r2dbc.tests.currentDialectTest
import org.jetbrains.exposed.v1.r2dbc.tests.shared.assertEqualCollections
import org.jetbrains.exposed.v1.r2dbc.tests.shared.assertEqualLists
import org.jetbrains.exposed.v1.r2dbc.tests.shared.assertTrue
import org.jetbrains.exposed.v1.r2dbc.tests.shared.expectException
import org.jetbrains.exposed.v1.r2dbc.transactions.TransactionManager
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

    class XEntity(id: EntityID<Int>) : Entity<Int>(id) {
        var b1 by XTable.b1
        var b2 by XTable.b2

        companion object : EntityClass<Int, XEntity>(XTable)
    }

    enum class XType {
        A, B
    }

    open class AEntity(id: EntityID<Int>) : IntEntity(id) {
        var b1 by XTable.b1

        companion object : IntEntityClass<AEntity>(XTable) {
            suspend fun create(b1: Boolean, type: XType): AEntity {
                val init: AEntity.() -> Unit = {
                    this.b1 = b1
                }
                val answer = when (type) {
                    XType.B -> BEntity.create { init() }
                    else -> new { init() }.flush()
                }
                return answer
            }
        }
    }

    class BEntity(id: EntityID<Int>) : AEntity(id) {
        var b2 by XTable.b2
        val y by YEntity optionalReferencedOn XTable.y1

        companion object : IntEntityClass<BEntity>(XTable) {
            suspend fun create(init: AEntity.() -> Unit): BEntity {
                val answer = new {
                    init()
                }.flush()
                return answer
            }
        }
    }

    class YEntity(id: EntityID<String>) : Entity<String>(id) {
        var x by YTable.x
        val b by BEntity backReferencedOn XTable.y1
        val bOpt by BEntity optionalBackReferencedOn XTable.y1

        companion object : EntityClass<String, YEntity>(YTable)
    }
}

@Suppress("LargeClass")
class EntityTests : R2dbcDatabaseTestsBase() {
    @Test
    fun testDefaults01() {
        withTables(EntityTestsData.YTable, EntityTestsData.XTable) {
            val x = EntityTestsData.XEntity.new { }.flush()
            assertEquals(x.b1, true, "b1 mismatched")
            assertEquals(x.b2, false, "b2 mismatched")
        }
    }

    @Test
    fun testDefaults02() {
        withTables(EntityTestsData.YTable, EntityTestsData.XTable) {
            val a: EntityTestsData.AEntity = EntityTestsData.AEntity.create(false, EntityTestsData.XType.A)
            val b: EntityTestsData.BEntity = EntityTestsData.AEntity.create(false, EntityTestsData.XType.B) as EntityTestsData.BEntity
            val y = EntityTestsData.YEntity.new { x = false }.flush()

            assertEquals(a.b1, false, "a.b1 mismatched")
            assertEquals(b.b1, false, "b.b1 mismatched")
            assertEquals(b.b2, false, "b.b2 mismatched")

            b.y.set(y)

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
            }.flush()

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
            }.flush()
            x.refresh(flush = true)
            objectsToVerify.add(x to testDb)
        }
        objectsToVerify.forEach { (human, testDb) ->
            assertEquals("foo", human.h, "Failed on ${testDb.name}")
            assertEquals(2, human.id.value, "Failed on ${testDb.name}")
        }
    }

    internal object OneAutoFieldTable : IntIdTable("single")
    internal class SingleFieldEntity(id: EntityID<Int>) : IntEntity(id) {
        companion object : IntEntityClass<SingleFieldEntity>(OneAutoFieldTable)
    }

    @Test
    fun testOneFieldEntity() {
        withTables(OneAutoFieldTable) {
            val new = SingleFieldEntity.new { }.flush()
            commit()
        }
    }

    @Test
    fun testBackReference01() {
        withTables(EntityTestsData.YTable, EntityTestsData.XTable) {
            val y = EntityTestsData.YEntity.new { }.flush()
            val b = EntityTestsData.BEntity.new { }.flush()
            b.y.set(y)
            assertEquals(b, y.b())
        }
    }

    @Test
    fun testBackReference02() {
        withTables(EntityTestsData.YTable, EntityTestsData.XTable) {
            val b = EntityTestsData.BEntity.new { }.flush()
            val y = EntityTestsData.YEntity.new { }.flush()
            b.y.set(y)
            assertEquals(b, y.b())
        }
    }

    object Items : IntIdTable("items") {
        val name = varchar("name", 255).uniqueIndex()
        val price = double("price")
    }

    class Item(id: EntityID<Int>) : IntEntity(id) {
        companion object : IntEntityClass<Item>(Items)

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
            }.flush()
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
            }.flush()
            assertEquals(oldPrice, item.price)
            assertNotNull(Item.testCache(item.id))

            val newPrice = 50.0
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
            }.flush()
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

    class SelfReferencedEntity(id: EntityID<Int>) : IntEntity(id) {
        var parent by SelfReferenceTable.parentId

        companion object : IntEntityClass<SelfReferencedEntity>(SelfReferenceTable)
    }

    @Test
    @Timeout(value = 5000, unit = TimeUnit.MILLISECONDS)
    fun testSelfReferences() {
        withTables(SelfReferenceTable) {
            val ref1 = SelfReferencedEntity.new { }.flush()
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
            }.flush()

            val post1 = Post.new {
                optCategory.set(category1)
                category.set(Category.new { title = "title" }.initializedEntity)
            }.flush()

            val post2 = Post.new {
                optCategory.set(category1)
                parent.set(post1)
            }.flush()

            assertEquals(2L, Post.all().count())
            assertEquals(2, category1.posts.count())
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
            }.flush()

            Post.new {
                optCategory.set(category1)
                category.set(Category.new { title = "title" }.initializedEntity)
            }.flush()

            Post.new {
                optCategory.set(category1)
            }.flush()
            commit()

            assertEquals(2, category1.posts.count())
            assertEquals(2, category1.posts.toList().size)
            assertEquals(1, category1.posts.limit(1).toList().size)
            assertEquals(1L, category1.posts.limit(1).count())
            assertEquals(2, category1.posts.count())
            assertEquals(2, category1.posts.toList().size)
        }
    }

    @Test
    fun testOrderByOnEntities() {
        withTables(Categories) {
            Categories.deleteAll()
            val category1 = Category.new { title = "Test1" }.flush()
            val category3 = Category.new { title = "Test3" }.flush()
            val category2 = Category.new { title = "Test2" }.flush()

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

    class Board(id: EntityID<Int>) : IntEntity(id) {
        companion object : IntEntityClass<Board>(Boards)

        var name by Boards.name
        val posts by Post optionalReferrersOn Posts.board
    }

    class Post(id: EntityID<Long>) : LongEntity(id) {
        companion object : LongEntityClass<Post>(Posts)

        val board by Board optionalReferencedOn Posts.board
        val parent by Post optionalReferencedOn Posts.parent
        val category by Category optionalReferencedOn Posts.category
        val optCategory by Category optionalReferencedOn Posts.optCategory
    }

    class Category(id: EntityID<Int>) : IntEntity(id) {
        companion object : IntEntityClass<Category>(Categories)

        val uniqueId by Categories.uniqueId
        var title by Categories.title
        val posts by Post optionalReferrersOn Posts.optCategory

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
            val parent = Post.new { this.category.set(Category.new { title = "title" }.initializedEntity) }.flush()
            Post.new { this.parent.set(parent) }.flush() // first flush before referencing
            assertEquals(2L, Post.all().count())
        }
    }

    @Test
    fun testInsertNonChildWithoutFlush() {
        withTables(Boards, Posts, Categories) {
            val board = Board.new { name = "irrelevant" }.flush()
            Post.new { this.board.set(board) }.flush()
            assertEquals(0, flushCache().size)
        }
    }

    @Test
    fun testThatQueriesWithinOtherQueryIteratorWorksFine() {
        withTables(Boards, Posts, Categories) {
            val board1 = Board.new { name = "irrelevant" }.flush()
            val board2 = Board.new { name = "relevant" }.flush()
            val post1 = Post.new { this.board.set(board1) }.flush()

            Board.all().forEach {
                it.posts.count() to it.posts
                Post.find { Posts.board eq it.id }.toList()
                    .map { post -> post.board()?.name.orEmpty() }
                    .joinToString()
            }
        }
    }

    @Test
    fun testInsertChildWithFlush() {
        withTables(Boards, Posts, Categories) {
            val parent = Post.new { this.category.set(Category.new { title = "title" }.initializedEntity) }.flush()
            assertNotNull(parent.id._value)
            Post.new { this.parent.set(parent) }.flush()
            assertEquals(0, flushCache().size)
        }
    }

    @Test
    fun testInsertChildWithChild() {
        withTables(Boards, Posts, Categories) {
            val parent = Post.new { this.category.set(Category.new { title = "title1" }.initializedEntity) }.flush()
            val child1 = Post.new {
                this.parent.set(parent)
                this.category.set(Category.new { title = "title2" }.initializedEntity)
            }.flush()
            Post.new { this.parent.set(child1) }.flush()
        }
    }

    @Test
    fun testOptionalReferrersWithDifferentKeys() {
        withTables(Boards, Posts, Categories) {
            val board = Board.new { name = "irrelevant" }.flush()
            val post1 = Post.new {
                this.board.set(board)
                this.category.set(Category.new { title = "title" }.initializedEntity)
            }.flush()
            assertEquals(1, board.posts.count())
            assertEquals(post1, board.posts.single())

            Post.new { this.board.set(board) }.flush()
            assertEquals(2, board.posts.count())
        }
    }

    @Test
    fun testErrorOnSetToDeletedEntity() {
        withTables(Boards) {
            expectException<EntityNotFoundException> {
                val board = Board.new { name = "irrelevant" }.flush()
                board.delete()
                board.name = "Cool"
            }
        }
    }

    @Test
    fun testCacheInvalidatedOnDSLDelete() {
        withTables(Boards) {
            val board1 = Board.new { name = "irrelevant" }.flush()
            assertNotNull(Board.testCache(board1.id))
            board1.delete()
            assertNull(Board.testCache(board1.id))

            val board2 = Board.new { name = "irrelevant" }.flush()
            assertNotNull(Board.testCache(board2.id))
            Boards.deleteWhere { Boards.id eq board2.id }
            assertNull(Board.testCache(board2.id))
        }
    }

    @Test
    fun testCacheInvalidatedOnDSLUpdate() {
        withTables(Boards) {
            val board1 = Board.new { name = "irrelevant" }.flush()
            assertNotNull(Board.testCache(board1.id))
            board1.name = "relevant"
            assertEquals("relevant", board1.name)

            val board2 = Board.new { name = "irrelevant2" }.flush()
            assertNotNull(Board.testCache(board2.id))
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

    open class Human(id: EntityID<Int>) : IntEntity(id) {
        companion object : IntEntityClass<Human>(Humans)

        var h by Humans.h
    }

    class User(id: EntityID<Int>) : IntEntity(id) {
        companion object : IntEntityClass<User>(Users) {
            suspend fun create(name: String): User {
                val h = Human.new { h = name.take(2) }.flush()
                return User.new(h.id.value) {
                    this.name = name
                }.flush()
            }
        }

        val human by Human referencedOn Users.id
        var name by Users.name
    }

    @Test
    fun testThatUpdateOfInsertedEntitiesGoesBeforeAnInsert() {
        withTables(Categories, Posts, Boards) {
            val category1 = Category.new {
                title = "category1"
            }.flush()

            val category2 = Category.new {
                title = "category2"
            }.flush()

            val post1 = Post.new {
                category.set(category1)
            }.flush()

            assertEquals(post1.category(), category1)

            post1.category.set(category2)

            val post2 = Post.new {
                category.set(category1)
            }.flush()

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

    class Parent(id: EntityID<Long>) : LongEntity(id) {
        companion object : LongEntityClass<Parent>(Parents)

        var name by Parents.name
    }

    object Children : LongIdTable() {
        val companyId = reference("company_id", Parents)
        val name = varchar("name", 80)
    }

    class Child(id: EntityID<Long>) : LongEntity(id) {
        companion object : LongEntityClass<Child>(Children)

        val parent by Parent referencedOn Children.companyId
        var name by Children.name
    }

    @Test
    fun testNewIdWithGet() {
        // SQL Server doesn't support an explicit id for auto-increment table
        withTables(listOf(TestDB.SQLSERVER), Parents, Children) {
            val parentId = Parent.new {
                name = "parent1"
            }.flush().id.value

            commit()

            val parent = Parent[parentId]
            val child = Child.new(100L) {
                this.parent.set(parent)
                name = "child1"
            }.flush()
            child.flush()

            assertEquals(100L, child.id.value)
            assertEquals(parentId, child.parent().id.value)
        }
    }

    @Test
    fun `newly created entity flushed successfully`() {
        withTables(Boards) {
            val board = Board.new { name = "Board1" }.flush()
            assertEquals(false, board.flush())

            assertEquals("Board1", board.name)
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

    class Region(id: EntityID<Int>) : IntEntity(id) {
        companion object : IntEntityClass<Region>(Regions)

        var name by Regions.name

        override fun equals(other: Any?): Boolean {
            return (other as? Region)?.id?.equals(id) ?: false
        }

        override fun hashCode(): Int = id.hashCode()
    }

    abstract class ComparableLongEntity<T : LongEntity>(id: EntityID<Long>) : LongEntity(id) {
        override fun equals(other: Any?): Boolean {
            return (other as? T)?.id?.equals(id) ?: false
        }

        override fun hashCode(): Int = id.hashCode()
    }

    class Student(id: EntityID<Long>) : ComparableLongEntity<Student>(id) {
        companion object : LongEntityClass<Student>(Students)

        var name by Students.name
        val school by School referencedOn Students.school
        val notes by Note.referrersOn(Notes.student, true)
        val detentions by Detention.optionalReferrersOn(Detentions.student, true)
        val bio by StudentBio.optionalBackReferencedOn(StudentBios.student)
    }

    class StudentBio(id: EntityID<Long>) : ComparableLongEntity<StudentBio>(id) {
        companion object : LongEntityClass<StudentBio>(StudentBios)

        val student by Student.referencedOn(StudentBios.student)
        var dateOfBirth by StudentBios.dateOfBirth
    }

    class Note(id: EntityID<Long>) : ComparableLongEntity<Note>(id) {
        companion object : LongEntityClass<Note>(Notes)

        var text by Notes.text
        val student by Student referencedOn Notes.student
    }

    class Detention(id: EntityID<Long>) : ComparableLongEntity<Detention>(id) {
        companion object : LongEntityClass<Detention>(Detentions)

        var reason by Detentions.reason
        val student by Student optionalReferencedOn Detentions.student
    }

    class Holiday(id: EntityID<Long>) : ComparableLongEntity<Holiday>(id) {
        companion object : LongEntityClass<Holiday>(Holidays)

        var holidayStart by Holidays.holidayStart
        var holidayEnd by Holidays.holidayEnd
    }

    class School(id: EntityID<Int>) : IntEntity(id) {
        companion object : IntEntityClass<School>(Schools)

        var name by Schools.name
        val region by Region referencedOn Schools.region
        val secondaryRegion by Region optionalReferencedOn Schools.secondaryRegion
        val students by Student.referrersOn(Students.school, true)
        var holidays by Holiday via SchoolHolidays
    }

    @Test
    fun preloadReferencesOnASizedIterable() {
        withTables(Regions, Schools) {
            val region1 = Region.new {
                name = "United Kingdom"
            }.flush()

            val region2 = Region.new {
                name = "England"
            }.flush()

            val school1 = School.new {
                name = "Eton"
                region.set(region1)
            }.flush()

            val school2 = School.new {
                name = "Harrow"
                region.set(region1)
            }.flush()

            val school3 = School.new {
                name = "Winchester"
                region.set(region2)
            }.flush()

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

    @Test
    fun testIterationOverSizedIterableWithPreload() {
        fun HashMap<String, Pair<Int, Long>>.assertEachQueryExecutedOnlyOnce() {
            forEach { (statement, stats) ->
                val executionCount = stats.first
                assertEquals(1, executionCount, "Statement executed more than once: $statement")
            }
        }

        withTables(Regions, Schools) {
            val region1 = Region.new {
                name = "United Kingdom"
            }.flush()
            School.new {
                name = "Eton"
                region.set(region1)
            }.flush()
            School.new {
                name = "Harrow"
                region.set(region1)
            }.flush()

            commit()

            inTopLevelSuspendTransaction(transactionIsolation = IsolationLevel.SERIALIZABLE) {
                debug = true // enables tracking of executed statements in this transaction

                val allSchools = School.all().with(School::region).toList()

                assertEquals(2, allSchools.size)
                // expected: 1 query to select all School, and 1 query to select referenced Regions
                assertEquals(2, statementCount)
                assertEquals(statementCount, statementStats.size)
                statementStats.assertEachQueryExecutedOnlyOnce()

                // reset tracker
                statementCount = 0
                statementStats.clear()

                val oneSchool = School.all().limit(1).with(School::region).toList()

                assertEquals(1, oneSchool.size)
                assertEquals(2, statementCount)
                assertEquals(statementCount, statementStats.size)
                statementStats.assertEachQueryExecutedOnlyOnce()

                debug = false
            }

            // test that cached result doesn't propagate when SizedIterable query changes after loading
            inTopLevelSuspendTransaction(transactionIsolation = IsolationLevel.SERIALIZABLE) {
                debug = true

                val oneSchool = School.all().with(School::region).limit(1).toList()

                assertEquals(1, oneSchool.size)
                // expected: 1 query to select all School, 1 query to select the referenced Regions,
                // then 1 new query to select only first School
                assertEquals(3, statementCount)
                assertEquals(statementCount, statementStats.size)
                statementStats.assertEachQueryExecutedOnlyOnce()

                debug = false
            }
        }
    }

    @Test
    fun preloadReferencesOnAnEntity() {
        withTables(Regions, Schools) {
            val region1 = Region.new {
                name = "United Kingdom"
            }.flush()

            val school1 = School.new {
                name = "Eton"
                region.set(region1)
            }.flush()

            commit()

            inTopLevelSuspendTransaction(transactionIsolation = IsolationLevel.SERIALIZABLE) {
                maxAttempts = 1
                School.find {
                    Schools.id eq school1.id
                }.first().load(School::region)

                assertNotNull(School.testCache(school1.id))
                assertEquals(region1, Region.testCache(School.testCache(school1.id)!!.readValues[Schools.region]))
            }
        }
    }

    @Test
    fun preloadOptionalReferencesOnASizedIterable() {
        withTables(Regions, Schools) {
            val region1 = Region.new {
                name = "United Kingdom"
            }.flush()

            val region2 = Region.new {
                name = "England"
            }.flush()

            val school1 = School.new {
                name = "Eton"
                region.set(region1)
                secondaryRegion.set(region2)
            }.flush().apply {
                // otherwise Oracle provides school1.id = 0 to testCache(), which returns null
                if (currentDialectTest is OracleDialect) flush()
            }

            val school2 = School.new {
                name = "Harrow"
                region.set(region1)
            }.flush()

            commit()

            inTopLevelSuspendTransaction(transactionIsolation = IsolationLevel.SERIALIZABLE) {
                maxAttempts = 1
                School.all().with(School::region, School::secondaryRegion)
                assertNotNull(School.testCache(school1.id))
                assertNotNull(School.testCache(school2.id))

                assertEquals(region1, Region.testCache(School.testCache(school1.id)!!.readValues[Schools.region]))
                assertEquals(region2, Region.testCache(School.testCache(school1.id)!!.readValues[Schools.secondaryRegion]!!))
                assertEquals(null, School.testCache(school2.id)!!.readValues[Schools.secondaryRegion])
            }
        }
    }

    @Test
    fun preloadOptionalReferencesOnAnEntity() {
        withTables(Regions, Schools) {
            val region1 = Region.new {
                name = "United Kingdom"
            }.flush()
            val region2 = Region.new {
                name = "England"
            }.flush()

            val school1 = School.new {
                name = "Eton"
                region.set(region1)
                secondaryRegion.set(region2)
            }.flush()

            commit()

            inTopLevelSuspendTransaction(transactionIsolation = IsolationLevel.SERIALIZABLE) {
                maxAttempts = 1
                val school2 = School.find {
                    Schools.id eq school1.id
                }.first().load(School::secondaryRegion)

                assertEquals(null, Region.testCache(school2.readValues[Schools.region]))
                assertEquals(region2, Region.testCache(school2.readValues[Schools.secondaryRegion]!!))
            }
        }
    }

    @Test
    fun preloadReferrersOnASizedIterable() {
        withTables(Regions, Schools, Students) {
            val region1 = Region.new {
                name = "United Kingdom"
            }.flush()

            val region2 = Region.new {
                name = "England"
            }.flush()

            val school1 = School.new {
                name = "Eton"
                region.set(region1)
            }.flush()

            val school2 = School.new {
                name = "Harrow"
                region.set(region1)
            }.flush()

            val school3 = School.new {
                name = "Winchester"
                region.set(region2)
            }.flush()

            val student1 = Student.new {
                name = "James Smith"
                school.set(school1)
            }.flush()

            val student2 = Student.new {
                name = "Jack Smith"
                school.set(school2)
            }.flush()

            val student3 = Student.new {
                name = "Henry Smith"
                school.set(school3)
            }.flush()

            val student4 = Student.new {
                name = "Peter Smith"
                school.set(school3)
            }.flush()

            commit()

            inTopLevelSuspendTransaction(transactionIsolation = IsolationLevel.SERIALIZABLE) {
                maxAttempts = 1
                val cache = TransactionManager.current().entityCache

                School.all().with(School::students)

                assertEqualCollections(cache.getReferrers<Student>(school1.id, Students.school)?.toList().orEmpty(), student1)
                assertEqualCollections(cache.getReferrers<Student>(school2.id, Students.school)?.toList().orEmpty(), student2)
                assertEqualCollections(cache.getReferrers<Student>(school3.id, Students.school)?.toList().orEmpty(), student3, student4)
            }
        }
    }

    @Test
    fun preloadReferrersOnAnEntity() {
        withTables(Regions, Schools, Students) {
            val region1 = Region.new {
                name = "United Kingdom"
            }.flush()

            val school1 = School.new {
                name = "Eton"
                region.set(region1)
            }.flush()

            val student1 = Student.new {
                name = "James Smith"
                school.set(school1)
            }.flush()

            val student2 = Student.new {
                name = "Jack Smith"
                school.set(school1)
            }.flush()

            val student3 = Student.new {
                name = "Henry Smith"
                school.set(school1)
            }.flush()

            commit()

            inTopLevelSuspendTransaction(transactionIsolation = IsolationLevel.SERIALIZABLE) {
                maxAttempts = 1
                val cache = TransactionManager.current().entityCache

                School.find { Schools.id eq school1.id }.first().load(School::students)

                assertEqualCollections(cache.getReferrers<Student>(school1.id, Students.school)?.toList().orEmpty(), student1, student2, student3)
            }
        }
    }

    @Test
    fun preloadOptionalReferrersOnASizedIterable() {
        withTables(Regions, Schools, Students, Detentions) {
            val region1 = Region.new {
                name = "United Kingdom"
            }.flush()

            val school1 = School.new {
                name = "Eton"
                region.set(region1)
            }.flush()

            val student1 = Student.new {
                name = "James Smith"
                school.set(school1)
            }.flush()

            val student2 = Student.new {
                name = "Jack Smith"
                school.set(school1)
            }.flush()

            val detention1 = Detention.new {
                reason = "Poor Behaviour"
                student.set(student1)
            }.flush()

            val detention2 = Detention.new {
                reason = "Poor Behaviour"
                student.set(student1)
            }.flush()

            commit()

            inTopLevelSuspendTransaction(transactionIsolation = IsolationLevel.SERIALIZABLE) {
                maxAttempts = 1
                School.all().with(School::students, Student::detentions)
                val cache = TransactionManager.current().entityCache

                School.all().with(School::students, Student::detentions)

                assertEqualCollections(cache.getReferrers<Student>(school1.id, Students.school)?.toList().orEmpty(), student1, student2)
                assertEqualCollections(cache.getReferrers<Detention>(student1.id, Detentions.student)?.toList().orEmpty(), detention1, detention2)
                assertEqualCollections(cache.getReferrers<Detention>(student2.id, Detentions.student)?.toList().orEmpty(), emptyList())
            }
        }
    }

    @Test
    fun preloadInnerTableLinkOnASizedIterable() {
        withTables(Regions, Schools, Holidays, SchoolHolidays) {
            val now = System.currentTimeMillis()
            val now10 = now + 10

            val region1 = Region.new {
                name = "United Kingdom"
            }.flush()

            val region2 = Region.new {
                name = "England"
            }.flush()

            val school1 = School.new {
                name = "Eton"
                region.set(region1)
            }.flush()

            val school2 = School.new {
                name = "Harrow"
                region.set(region1)
            }.flush()

            val school3 = School.new {
                name = "Winchester"
                region.set(region2)
            }.flush()

            val holiday1 = Holiday.new {
                holidayStart = now
                holidayEnd = now10
            }.flush()

            val holiday2 = Holiday.new {
                holidayStart = now
                holidayEnd = now10
            }.flush()

            val holiday3 = Holiday.new {
                holidayStart = now
                holidayEnd = now10
            }.flush()

            school1.holidays = SizedCollection(listOf(holiday1, holiday2))
            school2.holidays = SizedCollection(listOf(holiday3))

            commit()

            inTopLevelSuspendTransaction(transactionIsolation = IsolationLevel.SERIALIZABLE) {
                maxAttempts = 1
                School.all().with(School::holidays)
                val cache = TransactionManager.current().entityCache

                assertEqualCollections(cache.getReferrers<Holiday>(school1.id, SchoolHolidays.school)?.toList().orEmpty(), holiday1, holiday2)
                assertEqualCollections(cache.getReferrers<Holiday>(school2.id, SchoolHolidays.school)?.toList().orEmpty(), holiday3)
                assertEqualCollections(cache.getReferrers<Holiday>(school3.id, SchoolHolidays.school)?.toList().orEmpty(), emptyList())
            }
        }
    }

    @Test
    fun preloadInnerTableLinkOnAnEntity() {
        withTables(Regions, Schools, Holidays, SchoolHolidays) {
            val now = System.currentTimeMillis()
            val now10 = now + 10

            val region1 = Region.new {
                name = "United Kingdom"
            }.flush()

            val school1 = School.new {
                name = "Eton"
                region.set(region1)
            }.flush()

            val holiday1 = Holiday.new {
                holidayStart = now
                holidayEnd = now10
            }.flush()

            val holiday2 = Holiday.new {
                holidayStart = now
                holidayEnd = now10
            }.flush()

            val holiday3 = Holiday.new {
                holidayStart = now
                holidayEnd = now10
            }.flush()

            SchoolHolidays.insert {
                it[school] = school1.id
                it[holiday] = holiday1.id
            }

            SchoolHolidays.insert {
                it[school] = school1.id
                it[holiday] = holiday2.id
            }

            SchoolHolidays.insert {
                it[school] = school1.id
                it[holiday] = holiday3.id
            }

            commit()

            School.find {
                Schools.id eq school1.id
            }.first().load(School::holidays)

            val cache = TransactionManager.current().entityCache

            assertEquals(true, cache.getReferrers<Holiday>(school1.id, SchoolHolidays.school)?.toList()?.contains(holiday1))
            assertEquals(true, cache.getReferrers<Holiday>(school1.id, SchoolHolidays.school)?.toList()?.contains(holiday2))
            assertEquals(true, cache.getReferrers<Holiday>(school1.id, SchoolHolidays.school)?.toList()?.contains(holiday3))
        }
    }

    @Test
    fun preloadRelationAtDepth() {
        withTables(Regions, Schools, Holidays, SchoolHolidays, Students, Notes) {
            val region1 = Region.new {
                name = "United Kingdom"
            }.flush()

            val school1 = School.new {
                name = "Eton"
                region.set(region1)
            }.flush()

            val student1 = Student.new {
                name = "James Smith"
                school.set(school1)
            }.flush()

            val student2 = Student.new {
                name = "Jack Smith"
                school.set(school1)
            }.flush()

            val note1 = Note.new {
                text = "Note text"
                student.set(student1)
            }.flush()

            val note2 = Note.new {
                text = "Note text"
                student.set(student2)
            }.flush()

            School.all().with(School::students, Student::notes)

            val cache = TransactionManager.current().entityCache

            assertEquals(true, cache.getReferrers<Student>(school1.id, Students.school)?.toList()?.contains(student1))
            assertEquals(true, cache.getReferrers<Student>(school1.id, Students.school)?.toList()?.contains(student2))
            assertEquals(note1, cache.getReferrers<Note>(student1.id, Notes.student)?.first())
            assertEquals(note2, cache.getReferrers<Note>(student2.id, Notes.student)?.first())
        }
    }

    @Test
    fun preloadBackReferrenceOnASizedIterable() {
        withTables(Regions, Schools, Students, StudentBios) {
            val region1 = Region.new {
                name = "United States"
            }.flush()

            val school1 = School.new {
                name = "Eton"
                region.set(region1)
            }.flush()

            val student1 = Student.new {
                name = "James Smith"
                school.set(school1)
            }.flush()

            val student2 = Student.new {
                name = "John Smith"
                school.set(school1)
            }.flush()

            val bio1 = StudentBio.new {
                student.set(student1)
                dateOfBirth = "01/01/2000"
            }.flush()

            val bio2 = StudentBio.new {
                student.set(student2)
                dateOfBirth = "01/01/2002"
            }.flush()

            commit()

            inTopLevelSuspendTransaction(transactionIsolation = IsolationLevel.SERIALIZABLE) {
                maxAttempts = 1
                Student.all().with(Student::bio)
                val cache = TransactionManager.current().entityCache

                assertEqualCollections(cache.getReferrers<StudentBio>(student1.id, StudentBios.student)?.toList().orEmpty(), bio1)
                assertEqualCollections(cache.getReferrers<StudentBio>(student2.id, StudentBios.student)?.toList().orEmpty(), bio2)
            }
        }
    }

    @Test
    fun preloadBackReferrenceOnAnEntity() {
        withTables(Regions, Schools, Students, StudentBios) {
            val region1 = Region.new {
                name = "United States"
            }.flush()

            val school1 = School.new {
                name = "Eton"
                region.set(region1)
            }.flush()

            val student1 = Student.new {
                name = "James Smith"
                school.set(school1)
            }.flush()

            val student2 = Student.new {
                name = "John Smith"
                school.set(school1)
            }.flush()

            val bio1 = StudentBio.new {
                student.set(student1)
                dateOfBirth = "01/01/2000"
            }.flush()

            val bio2 = StudentBio.new {
                student.set(student2)
                dateOfBirth = "01/01/2002"
            }.flush()

            commit()

            inTopLevelSuspendTransaction(transactionIsolation = IsolationLevel.SERIALIZABLE) {
                maxAttempts = 1
                Student.all().first().load(Student::bio)
                val cache = TransactionManager.current().entityCache

                assertEqualCollections(cache.getReferrers<StudentBio>(student1.id, StudentBios.student)?.toList().orEmpty(), bio1)
            }
        }
    }

    @Test
    fun `test reference cache doesn't fully invalidated on set entity reference`() {
        withTables(Regions, Schools, Students, StudentBios) {
            val region1 = Region.new {
                name = "United States"
            }.flush()

            val school1 = School.new {
                name = "Eton"
                region.set(region1)
            }.flush()

            val student1 = Student.new {
                name = "James Smith"
                school.set(school1)
            }.flush()

            val student2 = Student.new {
                name = "John Smith"
                school.set(school1)
            }.flush()

            val bio1 = StudentBio.new {
                student.set(student1)
                dateOfBirth = "01/01/2000"
            }.flush()

            assertEquals(bio1, student1.bio())
            assertEquals(bio1.student(), student1)
        }
    }

    @Test
    fun `test nested entity initialization`() {
        withTables(Posts, Categories, Boards) {
            val parent1 = Post.new {
                board.set(
                    Board.new {
                        name = "Parent Board"
                    }.initializedEntity
                )
                category.set(
                    Category.new {
                        title = "Parent Category"
                    }.initializedEntity
                )
            }.flush()

            val category1 = parent1.category()

            val post = Post.new {
                parent.set(parent1)

                category.set(
                    Category.new {
                        title = "Child Category"
                    }.initializedEntity
                )

                optCategory.set(category1)
            }.flush()

            assertEquals("Parent Board", post.parent()?.board()?.name)
            assertEquals("Parent Category", post.parent()?.category()?.title)
            assertEquals("Parent Category", post.optCategory()?.title)
            assertEquals("Child Category", post.category()?.title)
        }
    }

    @Test
    fun testExplicitEntityConstructor() {
        var createBoardCalled = false
        fun createBoard(id: EntityID<Int>): Board {
            createBoardCalled = true
            return Board(id)
        }

        val boardEntityClass = object : IntEntityClass<Board>(Boards, entityCtor = ::createBoard) {}

        withTables(Boards) {
            val board = boardEntityClass.new {
                name = "Test Board"
            }.flush()

            assertEquals("Test Board", board.name)
            assertTrue(
                createBoardCalled
            )
        }
    }

    object RequestsTable : IdTable<String>() {
        val requestId: Column<String> = varchar("requestId", 256)
        override val primaryKey = PrimaryKey(requestId)
        override val id: Column<EntityID<String>> = requestId.entityId()
    }

    class Request(id: EntityID<String>) : Entity<String>(id) {
        companion object : EntityClass<String, Request>(RequestsTable)

        var requestId by RequestsTable.requestId
    }

    @Test
    fun testSelectFromStringIdTableWithPrimaryKeyByColumn() {
        withTables(RequestsTable) {
            Request.new {
                requestId = "123"
            }.flush()

            val count = Request.all().count()
            assertEquals(1, count)
        }
    }

    object CreditCards : IntIdTable("CreditCards") {
        val number = varchar("number", 16)
        val spendingLimit = ulong("spendingLimit").databaseGenerated()
    }

    class CreditCard(id: EntityID<Int>) : IntEntity(id) {
        companion object : IntEntityClass<CreditCard>(CreditCards)

        var number by CreditCards.number
        var spendingLimit by CreditCards.spendingLimit
    }

    @Test
    fun testDatabaseGeneratedValues() {
        withTables(CreditCards) { testDb ->
            when (testDb) {
                TestDB.POSTGRESQL -> {
                    // The value can also be set using a SQL trigger
                    exec(
                        """
                        CREATE OR REPLACE FUNCTION set_spending_limit()
                          RETURNS TRIGGER
                          LANGUAGE PLPGSQL
                          AS
                        $$
                        BEGIN
                            NEW."spendingLimit" := 10000;
                            RETURN NEW;
                        END;
                        $$;
                        """.trimIndent()
                    )
                    exec(
                        """
                        CREATE TRIGGER set_spending_limit
                        BEFORE INSERT
                        ON CreditCards
                        FOR EACH ROW
                        EXECUTE PROCEDURE set_spending_limit();
                        """.trimIndent()
                    )
                }
                else -> {
                    // This table is only used to get the statement that adds the DEFAULT value, and use it with exec
                    val creditCards2 = object : IntIdTable("CreditCards") {
                        val spendingLimit = ulong("spendingLimit").default(10000uL)
                    }
                    val missingStatements = SchemaUtils.addMissingColumnsStatements(creditCards2)
                    missingStatements.forEach {
                        exec(it)
                    }
                }
            }

            val creditCardId = CreditCards.insertAndGetId {
                it[number] = "0000111122223333"
            }.value
            assertEquals(
                10000uL,
                CreditCards.selectAll().where { CreditCards.id eq creditCardId }.single()[CreditCards.spendingLimit]
            )

            val creditCard = CreditCard.new {
                number = "0000111122223333"
            }.flush()

            assertEquals(10000uL, creditCard.spendingLimit)
        }
    }

    @Test
    fun testEntityIdParam() {
        withTables(CreditCards) {
            val newCard = CreditCard.new {
                number = "0000111122223333"
                spendingLimit = 10000uL
            }.flush()

            val conditionalId = Case()
                .When(CreditCards.spendingLimit less 500uL, CreditCards.id)
                .Else(idParam(newCard.id, CreditCards.id))
            assertEquals(newCard.id, CreditCards.select(conditionalId).single()[conditionalId])
            assertEquals(
                10000uL,
                CreditCards.select(CreditCards.spendingLimit)
                    .where { CreditCards.id eq idParam(newCard.id, CreditCards.id) }
                    .single()[CreditCards.spendingLimit]
            )
        }
    }

    object Countries : IdTable<String>("Countries") {
        override val id = varchar("id", 3).uniqueIndex().entityId()
        var name = text("name")
    }

    class Country(id: EntityID<String>) : Entity<String>(id) {
        var name by Countries.name
        val dishes by Dish referencedOn Dishes.country

        companion object : EntityClass<String, Country>(Countries)
    }

    object Dishes : IntIdTable("Dishes") {
        var name = text("name")
        val country = reference("country_id", Countries)
    }

    class Dish(id: EntityID<Int>) : IntEntity(id) {
        var name by Dishes.name
        val country by Country referencedOn Dishes.country

        companion object : IntEntityClass<Dish>(Dishes)
    }

    @Test
    fun testEagerLoadingWithStringParentId() {
        withTables(Countries, Dishes, configure = { keepLoadedReferencesOutOfTransaction = true }) {
            val lebanonId = Countries.insertAndGetId {
                it[id] = "LB"
                it[name] = "Lebanon"
            }
            val lebanon = Country.findById(lebanonId)!!

            Dish.new {
                name = "Kebbeh"
                country.set(lebanon)
            }.flush()

            Dish.new {
                name = "Mjaddara"
                country.set(lebanon)
            }.flush()

            Dish.new {
                name = "Fatteh"
                country.set(lebanon)
            }.flush()

            debug = true

            Country.all().with(Country::dishes)

            statementStats
                .filterKeys { it.startsWith("SELECT ") }
                .forEach { (_, stats) ->
                    val (count, _) = stats
                    assertEquals(1, count)
                }

            debug = false
        }
    }

    object Customers : IntIdTable("Customers") {
        val emailAddress = varchar("emailAddress", 30).uniqueIndex()
        val fullName = text("fullName")
    }

    class Customer(id: EntityID<Int>) : IntEntity(id) {
        var emailAddress by Customers.emailAddress
        var name by Customers.fullName

        val orders by Order referrersOn Orders.customer

        companion object : IntEntityClass<Customer>(Customers)
    }

    object Orders : IntIdTable("Orders") {
        var orderName = text("orderName")
        val customer = reference("customer", Customers.emailAddress)
    }

    class Order(id: EntityID<Int>) : IntEntity(id) {
        var name by Orders.orderName
        val customer by Customer referencedOn Orders.customer

        companion object : IntEntityClass<Order>(Orders)
    }

    @Test
    fun testEagerLoadingWithReferenceDifferentFromParentId() {
        withTables(Customers, Orders, configure = { keepLoadedReferencesOutOfTransaction = true }) {
            val customer1 = Customer.new {
                emailAddress = "customer1@testing.com"
                name = "Customer1"
            }.flush()

            val order1 = Order.new {
                name = "Order1"
                customer.set(customer1)
            }.flush()

            val order2 = Order.new {
                name = "Order2"
                customer.set(customer1)
            }.flush()

            Customer.all().with(Customer::orders)

            val cache = this.entityCache

            assertEquals(true, cache.getReferrers<Order>(customer1.id, Orders.customer)?.toList()?.contains(order1))
            assertEquals(true, cache.getReferrers<Order>(customer1.id, Orders.customer)?.toList()?.contains(order2))
        }
    }

    object TestTable : IntIdTable("TestTable") {
        val value = integer("value")
    }

    class TestEntityA(id: EntityID<Int>) : IntEntity(id) {
        var value by TestTable.value

        companion object : IntEntityClass<TestEntityA>(TestTable)
    }

    class TestEntityB(id: EntityID<Int>) : IntEntity(id) {
        var value by TestTable.value

        companion object : IntEntityClass<TestEntityB>(TestTable)
    }

    @Test
    fun testDifferentEntitiesMappedToTheSameTable() {
        withTables(TestTable) {
            val entityA = TestEntityA.new {
                value = 1
            }.flush()
            val entityB = TestEntityB.new {
                value = 2
            }.flush()

            entityA.value = 3
            entityB.value = 4

            flushCache()
        }
    }

    @Test
    fun testForIds() {
        withTables(Humans) {
            val h1 = Human.newAndFlush { h = "h1" }
            val h2 = Human.newAndFlush { h = "h2" }
            Human.new { h = "h3" }.flush()

            val byIds = Human.forIds(listOf(h1.id.value, h2.id.value)).toList()
            assertEquals(setOf("h1", "h2"), byIds.map { it.h }.toSet())
        }
    }
}

/**
 * This method is used just to keep tests similar to jdbc alternatives
 * (otherwise it's necessary to replace `forEach` with `collect` in all the tests)
 */
internal suspend fun <T> SizedIterable<T>.forEach(collector: FlowCollector<T>) =
    collect(collector)
