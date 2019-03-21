package org.jetbrains.exposed.sql.tests.shared

import org.jetbrains.exposed.dao.*
import org.jetbrains.exposed.exceptions.EntityNotFoundException
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.tests.DatabaseTestsBase
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.transactions.inTopLevelTransaction
import org.joda.time.DateTime
import org.junit.Test
import java.sql.Connection
import java.util.*
import java.util.concurrent.atomic.AtomicInteger
import javax.sql.rowset.serial.SerialBlob
import kotlin.test.*

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

    object TableWithDBDefault : IntIdTable() {
        var cIndex = 0
        val field = varchar("field", 100)
        val t1 = datetime("t1").defaultExpression(CurrentDateTime())
        val clientDefault = integer("clientDefault").clientDefault { cIndex++ }
    }

    class DBDefault(id: EntityID<Int>): IntEntity(id) {
        var field by TableWithDBDefault.field
        var t1 by TableWithDBDefault.t1
        val clientDefault by TableWithDBDefault.clientDefault

        override fun equals(other: Any?): Boolean {
            return (other as? DBDefault)?.let { id == it.id && field == it.field && equalDateTime(t1, it.t1) } ?: false
        }

        override fun hashCode(): Int = id.value.hashCode()

        companion object : IntEntityClass<DBDefault>(TableWithDBDefault)
    }

    @Test
    fun testDefaultsWithExplicit01() {
        withTables(TableWithDBDefault) {
            val created = listOf(
                    DBDefault.new { field = "1" },
                    DBDefault.new {
                        field = "2"
                        t1 = DateTime.now().minusDays(5)
                    })
            flushCache()
            created.forEach {
                DBDefault.removeFromCache(it)
            }

            val entities = DBDefault.all().toList()
            assertEqualCollections(created.map { it.id }, entities.map { it.id })
        }
    }

    @Test fun testDefaultsWithExplicit02() {
        withTables(TableWithDBDefault) {
            val created = listOf(
                    DBDefault.new{
                        field = "2"
                        t1 = DateTime.now().minusDays(5)
                    }, DBDefault.new{ field = "1" })

            flushCache()
            created.forEach {
                DBDefault.removeFromCache(it)
            }
            val entities = DBDefault.all().toList()
            assertEqualCollections(created, entities)
        }
    }

    @Test fun testDefaultsInvokedOnlyOncePerEntity() {
        withTables(TableWithDBDefault) {
            TableWithDBDefault.cIndex = 0
            val db1 = DBDefault.new{ field = "1" }
            val db2 = DBDefault.new{ field = "2" }
            flushCache()
            assertEquals(0, db1.clientDefault)
            assertEquals(1, db2.clientDefault)
            assertEquals(2, TableWithDBDefault.cIndex)
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
        val category = optReference("category", Categories.uniqueId)
    }

    object Categories : IntIdTable() {
        val uniqueId = uuid("uniqueId").clientDefault { UUID.randomUUID() }.uniqueIndex()
        val title = varchar("title", 50)
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
        var category by Category optionalReferencedOn Posts.category
    }


    class Category(id: EntityID<Int>) : IntEntity(id) {
        companion object : IntEntityClass<Category>(Categories)

        val uniqueId by Categories.uniqueId
        var title by Categories.title
        val posts by Post optionalReferrersOn Posts.category

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
        withTables(Boards, Posts) {
            val parent = Post.new {  }
            Post.new { this.parent = parent } // first flush before referencing
            assertEquals(2, Post.all().count())
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
    fun testThatQueriesWithinOtherQueryIteratorWorksFine() {
        withTables(Boards, Posts) {
            val board1 = Board.new { name = "irrelevant" }
            val board2 = Board.new { name = "relevant" }
            val post1 = Post.new { board = board1 }

            Board.all().forEach {
                it.posts.count() to it.posts.toList()
                Post.find { Posts.board eq it.id }.joinToString { it.board?.name.orEmpty() }
            }
        }
    }

    @Test
    fun testInsertChildWithFlush() {
        withTables(Boards, Posts) {
            val parent = Post.new {  }
            flushCache()
            assertNotNull(parent.id._value)
            Post.new { this.parent = parent }
            assertEquals(1, flushCache().size)
        }
    }

    @Test
    fun testInsertChildWithChild() {
        withTables(Boards, Posts) {
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

    @Test
    fun testErrorOnSetToDeletedEntity() {
        withTables(Boards) {
            expectException<EntityNotFoundException> {
                val board = Board.new { name = "irrelevant" }
                board.delete()
                board.name = "Cool"
            }
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

    private object SelfReferenceTable : IntIdTable() {
        val parentId = optReference("parent", SelfReferenceTable)
    }

    class SelfReferencedEntity(id: EntityID<Int>) : IntEntity(id) {
        var parent by SelfReferenceTable.parentId

        companion object : IntEntityClass<SelfReferencedEntity>(SelfReferenceTable)
    }

    @Test(timeout = 5000)
    fun testSelfReferences() {
        withTables(SelfReferenceTable) {
            val ref1 = SelfReferencedEntity.new { }
            ref1.parent = ref1.id
            val refRow = SelfReferenceTable.select { SelfReferenceTable.id eq ref1.id }.single()
            assertEquals(ref1.id._value, refRow[SelfReferenceTable.parentId]!!.value)
        }
    }

    @Test
    fun testNonEntityIdReference() {
        withTables(Posts) {
            val category1 = Category.new {
                title = "cat1"
            }

            val post1 = Post.new {
                category = category1
            }

            val post2 = Post.new {
                category = category1
                parent = post1
            }

            assertEquals(2, Post.all().count())
            assertEquals(2, category1.posts.count())
            assertEquals(2, Posts.select { Posts.category eq category1.uniqueId }.count())
        }
    }

    // https://github.com/JetBrains/Exposed/issues/439
    @Test fun callLimitOnRelationDoesntMutateTheCachedValue() {
        withTables(Posts) {
            val category1 = Category.new {
                title = "cat1"
            }

            Post.new {
                category = category1
            }

            Post.new {
                category = category1
            }
            commit()

            assertEquals(2, category1.posts.count())
            assertEquals(2, category1.posts.toList().size)
            assertEquals(1, category1.posts.limit(1).toList().size)
            assertEquals(1, category1.posts.limit(1).count())
            assertEquals(2, category1.posts.count())
            assertEquals(2, category1.posts.toList().size)
        }
    }

    @Test fun testOrderByOnEntities() {
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

    private fun <T> newTransaction(statement: Transaction.() -> T) =
            inTopLevelTransaction(TransactionManager.current().db.metadata.defaultTransactionIsolation, 1, null, statement)

    @Test fun sharingEntityBetweenTransactions() {
        withTables(Humans) {
            val human1 = newTransaction {
                Human.new {
                    this.h = "foo"
                }
            }
            newTransaction {
                assertEquals(null, Human.testCache(human1.id))
                assertEquals("foo", Humans.selectAll().single()[Humans.h])
                human1.h = "bar"
                assertEquals(human1, Human.testCache(human1.id))
                assertEquals("bar", Humans.selectAll().single()[Humans.h])
            }

            newTransaction {
                assertEquals("bar", Humans.selectAll().single()[Humans.h])
            }
        }
    }

    object Regions : IntIdTable(name = "region") {
        val name = varchar("name", 255)
    }

    object Students : LongIdTable(name = "students") {
        val name    = varchar("name", 255)
        val school  = reference("school_id", Schools)
    }

    object Notes : LongIdTable(name = "notes") {
        val text        = varchar("text", 255)
        val student     = reference("student_id", Students)
    }

    object Detentions : LongIdTable(name = "detentions") {
        val reason          = varchar("reason", 255)
        val student         = optReference("student_id", Students)
    }

    object Holidays : LongIdTable(name = "holidays") {
        val holidayStart     = datetime("holiday_start")
        val holidayEnd       = datetime("holiday_end")
    }

    object SchoolHolidays : Table(name = "school_holidays") {
        val school          = reference("school_id", Schools, ReferenceOption.CASCADE, ReferenceOption.CASCADE).primaryKey(0)
        val holiday         = reference("holiday_id", Holidays, ReferenceOption.CASCADE, ReferenceOption.CASCADE).primaryKey(1)
    }

    object Schools : IntIdTable(name = "school") {
        val name                = varchar("name", 255).index(isUnique = true)
        val region              = reference("region_id", Regions)
        val secondaryRegion     = optReference("secondary_region_id", Regions)
    }

    class Region(id: EntityID<Int>): IntEntity(id) {
        companion object : IntEntityClass<Region>(Regions)

        var name by Regions.name

        override fun equals(other: Any?): Boolean {
            return (other as? Region)?.id?.equals(id) ?: false
        }

        override fun hashCode(): Int = id.hashCode()
    }

    abstract class ComparableLongEntity<T:LongEntity>(id: EntityID<Long>) : LongEntity(id) {
        override fun equals(other: Any?): Boolean {
            return (other as? T)?.id?.equals(id) ?: false
        }

        override fun hashCode(): Int = id.hashCode()
    }
    class Student(id: EntityID<Long>): ComparableLongEntity<Student>(id) {
        companion object : LongEntityClass<Student>(Students)
        var name        by Students.name
        var school      by School referencedOn Students.school
        val notes       by Note.referrersOn(Notes.student, true)
        val detentions  by Detention.optionalReferrersOn(Detentions.student, true)
    }

    class Note(id: EntityID<Long>): ComparableLongEntity<Note>(id) {
        companion object : LongEntityClass<Note>(Notes)
        var text by Notes.text
        var student by Student referencedOn Notes.student
    }


    class Detention(id: EntityID<Long>): ComparableLongEntity<Detention>(id) {
        companion object : LongEntityClass<Detention>(Detentions)
        var reason        by Detentions.reason
        var student       by Student optionalReferencedOn  Detentions.student
    }


    class Holiday(id: EntityID<Long>) : ComparableLongEntity<Holiday>(id) {
        companion object : LongEntityClass<Holiday>(Holidays)

        var holidayStart by Holidays.holidayStart
        var holidayEnd by Holidays.holidayEnd
    }

    class School(id: EntityID<Int>): IntEntity(id) {
        companion object : IntEntityClass<School>(Schools)

        var name by Schools.name
        var region by Region referencedOn Schools.region
        var secondaryRegion by Region optionalReferencedOn Schools.secondaryRegion
        val students by Student.referrersOn(Students.school, true)
        var holidays by Holiday via SchoolHolidays
    }

    @Test fun preloadReferencesOnASizedIterable() {

        withTables(Regions, Schools) {

            val region1 = Region.new {
                name = "United Kingdom"
            }

            val region2 = Region.new {
                name = "England"
            }

            val school1 = School.new {
                name = "Eton"
                region          = region1
            }

            val school2 = School.new {
                name = "Harrow"
                region          = region1
            }

            val school3 = School.new {
                name    = "Winchester"
                region  = region2
            }

            commit()

            inTopLevelTransaction(Connection.TRANSACTION_SERIALIZABLE, 1) {
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

    @Test fun preloadReferencesOnAnEntity() {

        withTables(Regions, Schools) {

            val region1 = Region.new {
                name = "United Kingdom"
            }

            val school1 = School.new {
                name = "Eton"
                region          = region1
            }

            commit()

            inTopLevelTransaction(Connection.TRANSACTION_SERIALIZABLE, 1) {
                School.find {
                    Schools.id eq school1.id
                }.first().load(School::region)

                assertNotNull(School.testCache(school1.id))
                assertEquals(region1, Region.testCache(School.testCache(school1.id)!!.readValues[Schools.region]))

//                val cache = TransactionManager.current().entityCache
//                val schoolReferences = cache.referrers[school1.id]
//                assertEquals(1, schoolReferences?.size)
//                assertEquals(1, schoolReferences?.get(Schools.region)?.count())
//                assertEquals(region1, schoolReferences?.get(Schools.region)?.single())
            }
        }
    }

    @Test fun  preloadOptionalReferencesOnASizedIterable() {
        withTables(Regions, Schools) {

            val region1 = Region.new {
                name = "United Kingdom"
            }

            val region2 = Region.new {
                name = "England"
            }

            val school1 = School.new {
                name = "Eton"
                region          = region1
                secondaryRegion = region2
            }

            val school2 = School.new {
                name = "Harrow"
                region          = region1
            }

            commit()

            inTopLevelTransaction(Connection.TRANSACTION_SERIALIZABLE, 1) {
                School.all().with(School::region, School::secondaryRegion)
                assertNotNull(School.testCache(school1.id))
                assertNotNull(School.testCache(school2.id))

                assertEquals(region1, Region.testCache(School.testCache(school1.id)!!.readValues[Schools.region]))
                assertEquals(region2, Region.testCache(School.testCache(school1.id)!!.readValues[Schools.secondaryRegion]!!))
                assertEquals(null, School.testCache(school2.id)!!.readValues[Schools.secondaryRegion])
            }
        }
    }

    @Test fun preloadOptionalReferencesOnAnEntity() {

        withTables(Regions, Schools) {

            val region1 = Region.new {
                name = "United Kingdom"
            }
            val region2 = Region.new {
                name = "England"
            }

            val school1 = School.new {
                name = "Eton"
                region          = region1
                secondaryRegion = region2
            }

            commit()

            inTopLevelTransaction(Connection.TRANSACTION_SERIALIZABLE, 1) {
                val school2 = School.find {
                    Schools.id eq school1.id
                }.first().load(School::secondaryRegion)

                assertEquals(null, Region.testCache(school2.readValues[Schools.region]))
                assertEquals(region2, Region.testCache(school2.readValues[Schools.secondaryRegion]!!))
            }
        }
    }

    @Test fun preloadReferrersOnASizedIterable() {

        withTables(Regions, Schools, Students) {

            val region1 = Region.new {
                name = "United Kingdom"
            }

            val region2 = Region.new {
                name = "England"
            }

            val school1 = School.new {
                name = "Eton"
                region          = region1
            }

            val school2 = School.new {
                name = "Harrow"
                region          = region1
            }

            val school3 = School.new {
                name    = "Winchester"
                region  = region2
            }

            val student1 = Student.new {
                name = "James Smith"
                school = school1
            }

            val student2 = Student.new {
                name = "Jack Smith"
                school = school2
            }

            val student3 = Student.new {
                name = "Henry Smith"
                school = school3
            }

            val student4 = Student.new {
                name = "Peter Smith"
                school = school3
            }

            commit()

            inTopLevelTransaction(Connection.TRANSACTION_SERIALIZABLE, 1) {
                val cache           = TransactionManager.current().entityCache

                School.all().with(School::students)
                assertEquals(true, cache.referrers.containsKey(school1.id))
                assertEquals(true, cache.referrers.containsKey(school2.id))
                assertEquals(true, cache.referrers.containsKey(school3.id))

                assertEqualCollections(cache.referrers[school1.id]?.get(Students.school)?.toList().orEmpty(), student1)
                assertEqualCollections(cache.referrers[school2.id]?.get(Students.school)?.toList().orEmpty(), student2)
                assertEqualCollections(cache.referrers[school3.id]?.get(Students.school)?.toList().orEmpty(), student3, student4)
            }
        }
    }

    @Test fun preloadReferrersOnAnEntity() {
        withTables(Regions, Schools, Students) {

            val region1 = Region.new {
                name = "United Kingdom"
            }


            val school1 = School.new {
                name = "Eton"
                region          = region1
            }

            val student1 = Student.new {
                name = "James Smith"
                school = school1
            }

            val student2 = Student.new {
                name = "Jack Smith"
                school = school1
            }

            val student3 = Student.new {
                name = "Henry Smith"
                school = school1
            }

            commit()

            inTopLevelTransaction(Connection.TRANSACTION_SERIALIZABLE, 1) {
                val cache           = TransactionManager.current().entityCache

                School.find { Schools.id eq school1.id }.first().load(School::students)
                assertEquals(true, cache.referrers.containsKey(school1.id))

                assertEqualCollections(cache.referrers[school1.id]?.get(Students.school)?.toList().orEmpty(), student1, student2, student3)
            }
        }
    }

    @Test fun preloadOptionalReferrersOnASizedIterable() {

        withTables(Regions, Schools, Students, Detentions) {

            val region1 = Region.new {
                name = "United Kingdom"
            }

            val school1 = School.new {
                name = "Eton"
                region          = region1
            }

            val student1 = Student.new {
                name = "James Smith"
                school = school1
            }

            val student2 = Student.new {
                name = "Jack Smith"
                school = school1
            }

            val detention1 = Detention.new {
                reason = "Poor Behaviour"
                student = student1
            }

            val detention2 = Detention.new {
                reason = "Poor Behaviour"
                student = student1
            }


            commit()

            inTopLevelTransaction(Connection.TRANSACTION_SERIALIZABLE, 1) {
                School.all().with(School::students, Student::detentions)
                val cache           = TransactionManager.current().entityCache

                School.all().with(School::students, Student::detentions)
                assertEquals(true, cache.referrers.containsKey(school1.id))
                assertEquals(true, cache.referrers.containsKey(student1.id))
                assertEquals(true, cache.referrers.containsKey(student2.id))

                assertEqualCollections(cache.referrers[school1.id]?.get(Students.school)?.toList().orEmpty(), student1, student2)
                assertEqualCollections(cache.referrers[student1.id]?.get(Detentions.student)?.toList().orEmpty(), detention1, detention2)
                assertEqualCollections(cache.referrers[student2.id]?.get(Detentions.student)?.toList().orEmpty(), emptyList())
            }
        }
    }

    @Test fun preloadInnerTableLinkOnASizedIterable() {

        withTables(Regions, Schools, Holidays, SchoolHolidays) {

            val region1 = Region.new {
                name = "United Kingdom"
            }

            val region2 = Region.new {
                name = "England"
            }

            val school1 = School.new {
                name = "Eton"
                region          = region1
            }

            val school2 = School.new {
                name = "Harrow"
                region          = region1
            }

            val school3 = School.new {
                name    = "Winchester"
                region  = region2
            }

            val holiday1 = Holiday.new {
                holidayStart = DateTime.now()
                holidayEnd = DateTime.now().plus(10)
            }

            val holiday2 = Holiday.new {
                holidayStart = DateTime.now()
                holidayEnd = DateTime.now().plus(10)
            }

            val holiday3 = Holiday.new {
                holidayStart = DateTime.now()
                holidayEnd = DateTime.now().plus(10)
            }

            school1.holidays = SizedCollection(holiday1, holiday2)
            school2.holidays = SizedCollection(holiday3)

            commit()

            inTopLevelTransaction(Connection.TRANSACTION_SERIALIZABLE, 1) {
                School.all().with(School::holidays)
                val cache = TransactionManager.current().entityCache
                assertEquals(true, cache.referrers.containsKey(school1.id))
                assertEquals(true, cache.referrers.containsKey(school2.id))
                assertEquals(true, cache.referrers.containsKey(school3.id))

                assertEqualCollections(cache.referrers[school1.id]?.get(SchoolHolidays.school)?.toList().orEmpty(), holiday1, holiday2)
                assertEqualCollections(cache.referrers[school2.id]?.get(SchoolHolidays.school)?.toList().orEmpty(), holiday3)
                assertEqualCollections(cache.referrers[school3.id]?.get(SchoolHolidays.school)?.toList().orEmpty(), emptyList())
            }
        }
    }

    @Test fun preloadInnerTableLinkOnAnEntity() {
        withTables(Regions, Schools, Holidays, SchoolHolidays) {

            val region1 = Region.new {
                name = "United Kingdom"
            }

            val school1 = School.new {
                name = "Eton"
                region          = region1
            }

            val holiday1 = Holiday.new {
                holidayStart = DateTime.now()
                holidayEnd = DateTime.now().plus(10)
            }

            val holiday2 = Holiday.new {
                holidayStart = DateTime.now()
                holidayEnd = DateTime.now().plus(10)
            }

            val holiday3 = Holiday.new {
                holidayStart = DateTime.now()
                holidayEnd = DateTime.now().plus(10)
            }

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

            School.find {
                Schools.id eq school1.id
            }.first().load(School::holidays)

            commit()

            val cache           = TransactionManager.current().entityCache
            assertEquals(true, cache.referrers.containsKey(school1.id))

            assertEquals(true, cache.referrers[school1.id]?.get(SchoolHolidays.school)?.contains(holiday1))
            assertEquals(true, cache.referrers[school1.id]?.get(SchoolHolidays.school)?.contains(holiday2))
            assertEquals(true, cache.referrers[school1.id]?.get(SchoolHolidays.school)?.contains(holiday3))
        }
    }

    @Test fun preloadRelationAtDepth() {

        withTables(Regions, Schools, Holidays, SchoolHolidays, Students, Notes) {

            val region1 = Region.new {
                name = "United Kingdom"
            }

            val school1 = School.new {
                name = "Eton"
                region          = region1
            }

            val student1 = Student.new {
                name = "James Smith"
                school = school1
            }

            val student2 = Student.new {
                name = "Jack Smith"
                school = school1
            }

            val note1 = Note.new {
                text = "Note text"
                student = student1
            }

            val note2 = Note.new {
                text = "Note text"
                student = student2
            }


            School.all().with(School::students, Student::notes)

            val cache           = TransactionManager.current().entityCache
            assertEquals(true, cache.referrers.containsKey(school1.id))
            assertEquals(true, cache.referrers.containsKey(student1.id))
            assertEquals(true, cache.referrers.containsKey(student2.id))

            assertEquals(true, cache.referrers[school1.id]?.get(Students.school)?.contains(student1))
            assertEquals(true, cache.referrers[school1.id]?.get(Students.school)?.contains(student2))
            assertEquals(note1, cache.referrers[student1.id]?.get(Notes.student)?.first())
            assertEquals(note2, cache.referrers[student2.id]?.get(Notes.student)?.first())
        }
    }
}