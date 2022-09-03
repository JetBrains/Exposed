package org.jetbrains.exposed.sql.tests.shared.entities

import org.jetbrains.exposed.dao.*
import org.jetbrains.exposed.dao.exceptions.EntityNotFoundException
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IdTable
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.dao.id.LongIdTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.statements.api.ExposedBlob
import org.jetbrains.exposed.sql.tests.DatabaseTestsBase
import org.jetbrains.exposed.sql.tests.TestDB
import org.jetbrains.exposed.sql.tests.shared.*
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.transactions.inTopLevelTransaction
import org.junit.Test
import java.sql.Connection
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

object EntityTestsData {

    object YTable : IdTable<String>("YTable") {
        override val id: Column<EntityID<String>> = varchar("uuid", 36).entityId().clientDefault {
            EntityID(UUID.randomUUID().toString(), YTable)
        }

        val x = bool("x").default(true)
        val blob = blob("content").nullable()

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
        var y by YEntity optionalReferencedOn XTable.y1

        companion object : IntEntityClass<BEntity>(XTable) {
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

class EntityTests : DatabaseTestsBase() {
    @Test fun testDefaults01() {
        withTables(EntityTestsData.YTable, EntityTestsData.XTable) {
            val x = EntityTestsData.XEntity.new { }
            assertEquals(x.b1, true, "b1 mismatched")
            assertEquals(x.b2, false, "b2 mismatched")
        }
    }

    @Test fun testDefaults02() {
        withTables(EntityTestsData.YTable, EntityTestsData.XTable) {
            val a: EntityTestsData.AEntity = EntityTestsData.AEntity.create(false, EntityTestsData.XType.A)
            val b: EntityTestsData.BEntity = EntityTestsData.AEntity.create(false, EntityTestsData.XType.B) as EntityTestsData.BEntity
            val y = EntityTestsData.YEntity.new { x = false }

            assertEquals(a.b1, false, "a.b1 mismatched")
            assertEquals(b.b1, false, "b.b1 mismatched")
            assertEquals(b.b2, false, "b.b2 mismatched")

            b.y = y

            assertFalse(b.y!!.x)
            assertNotNull(y.b)
        }
    }

    @Test fun testBlobField() {
        withTables(EntityTestsData.YTable) {
            val y1 = EntityTestsData.YEntity.new {
                x = false
                content = ExposedBlob("foo".toByteArray())
            }

            flushCache()
            var y2 = EntityTestsData.YEntity.reload(y1)!!
            assertEquals(String(y2.content!!.bytes), "foo")

            y2.content = null
            flushCache()
            y2 = EntityTestsData.YEntity.reload(y1)!!
            assertNull(y2.content)

            y2.content = ExposedBlob("foo2".toByteArray())
            flushCache()
        }
    }

    @Test fun testTextFieldOutsideTheTransaction() {
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

    @Test fun testNewWithIdAndRefresh() {
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
    internal class SingleFieldEntity(id: EntityID<Int>) : IntEntity(id) {
        companion object : IntEntityClass<SingleFieldEntity>(OneAutoFieldTable)
    }

    // GitHub issue #95: Dao new{ } with no values problem "NoSuchElementException: List is empty"
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
            b.y = y
            assertEquals(b, y.b)
        }
    }

    @Test
    fun testBackReference02() {
        withTables(EntityTestsData.YTable, EntityTestsData.XTable) {
            val b = EntityTestsData.BEntity.new { }
            flushCache()
            val y = EntityTestsData.YEntity.new { }
            b.y = y
            assertEquals(b, y.b)
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
        val posts by Post.optionalReferrersOn(Posts.board)
    }

    class Post(id: EntityID<Long>) : LongEntity(id) {
        companion object : LongEntityClass<Post>(Posts)

        var board by Board optionalReferencedOn Posts.board
        var parent by Post optionalReferencedOn Posts.parent
        var category by Category optionalReferencedOn Posts.category
        var optCategory by Category optionalReferencedOn Posts.optCategory
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
        withTables(Boards, Posts) {
            val parent = Post.new { this.category = Category.new { title = "title" } }
            Post.new { this.parent = parent } // first flush before referencing
            assertEquals(2L, Post.all().count())
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
            val parent = Post.new { this.category = Category.new { title = "title" } }
            flushCache()
            assertNotNull(parent.id._value)
            Post.new { this.parent = parent }
            assertEquals(1, flushCache().size)
        }
    }

    @Test
    fun testInsertChildWithChild() {
        withTables(Boards, Posts) {
            val parent = Post.new { this.category = Category.new { title = "title1" } }
            val child1 = Post.new {
                this.parent = parent
                this.category = Category.new { title = "title2" }
            }
            Post.new { this.parent = child1 }
            flushCache()
        }
    }

    @Test
    fun testOptionalReferrersWithDifferentKeys() {
        withTables(Boards, Posts) {
            val board = Board.new { name = "irrelevant" }
            val post1 = Post.new {
                this.board = board
                this.category = Category.new { title = "title" }
            }
            assertEquals(1L, board.posts.count())
            assertEquals(post1, board.posts.single())

            Post.new { this.board = board }
            assertEquals(2L, board.posts.count())
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

    @Test
    fun testCacheInvalidatedOnDSLDelete() {
        withTables(Boards) {
            val board1 = Board.new { name = "irrelevant" }
            assertNotNull(Board.testCache(board1.id))
            board1.delete()
            assertNull(Board.testCache(board1.id))

            val board2 = Board.new { name = "irrelevant" }
            assertNotNull(Board.testCache(board2.id))
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
            Boards.update({ Boards.id eq board2.id }) {
                it[Boards.name] = "relevant2"
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
                optCategory = category1
                category = Category.new { title = "title" }
            }

            val post2 = Post.new {
                optCategory = category1
                parent = post1
            }

            assertEquals(2L, Post.all().count())
            assertEquals(2L, category1.posts.count())
            assertEquals(2L, Posts.select { Posts.optCategory eq category1.uniqueId }.count())
        }
    }

    // https://github.com/JetBrains/Exposed/issues/439
    @Test fun callLimitOnRelationDoesntMutateTheCachedValue() {
        withTables(Posts) {
            val category1 = Category.new {
                title = "cat1"
            }

            Post.new {
                optCategory = category1
                category = Category.new { title = "title" }
            }

            Post.new {
                optCategory = category1
            }
            commit()

            assertEquals(2L, category1.posts.count())
            assertEquals(2, category1.posts.toList().size)
            assertEquals(1, category1.posts.limit(1).toList().size)
            assertEquals(1L, category1.posts.limit(1).count())
            assertEquals(2L, category1.posts.count())
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

    @Test fun `test what update of inserted entities goes before an insert`() {
        withTables(Categories, Posts) {
            val category1 = Category.new {
                title = "category1"
            }

            val category2 = Category.new {
                title = "category2"
            }

            val post1 = Post.new {
                category = category1
            }

            flushCache()
            assertEquals(post1.category, category1)

            post1.category = category2

            val post2 = Post.new {
                category = category1
            }

            flushCache()
            Post.reload(post1)
            Post.reload(post2)

            assertEquals(category2, post1.category)
            assertEquals(category1, post2.category)
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
        var parent by Parent referencedOn Children.companyId
        var name by Children.name
    }

    @Test fun `test new(id) with get`() {
        // SQL Server doesn't support an explicit id for auto-increment table
        withTables(listOf(TestDB.SQLSERVER), Parents, Children) {
            val parentId = Parent.new {
                name = "parent1"
            }.id.value

            commit()

            val child = Child.new(100L) {
                parent = Parent[parentId]
                name = "child1"
            }

            assertEquals(100L, child.id.value)
            assertEquals(parentId, child.parent.id.value)
        }
    }

    @Test fun `newly created entity flushed successfully`() {
        withTables(Boards) {
            val board = Board.new { name = "Board1" }.apply {
                assertEquals(true, flush())
            }

            assertEquals("Board1", board.name)
        }
    }

    private fun <T> newTransaction(statement: Transaction.() -> T) =
        inTopLevelTransaction(TransactionManager.manager.defaultIsolationLevel, 1, false, null, null, statement)

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
        var school by School referencedOn Students.school
        val notes by Note.referrersOn(Notes.student, true)
        val detentions by Detention.optionalReferrersOn(Detentions.student, true)
        val bio by StudentBio.optionalBackReferencedOn(StudentBios.student)
    }

    class StudentBio(id: EntityID<Long>) : ComparableLongEntity<StudentBio>(id) {
        companion object : LongEntityClass<StudentBio>(StudentBios)
        var student by Student.referencedOn(StudentBios.student)
        var dateOfBirth by StudentBios.dateOfBirth
    }

    class Note(id: EntityID<Long>) : ComparableLongEntity<Note>(id) {
        companion object : LongEntityClass<Note>(Notes)
        var text by Notes.text
        var student by Student referencedOn Notes.student
    }

    class Detention(id: EntityID<Long>) : ComparableLongEntity<Detention>(id) {
        companion object : LongEntityClass<Detention>(Detentions)
        var reason by Detentions.reason
        var student by Student optionalReferencedOn Detentions.student
    }

    class Holiday(id: EntityID<Long>) : ComparableLongEntity<Holiday>(id) {
        companion object : LongEntityClass<Holiday>(Holidays)

        var holidayStart by Holidays.holidayStart
        var holidayEnd by Holidays.holidayEnd
    }

    class School(id: EntityID<Int>) : IntEntity(id) {
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
                region = region1
            }

            val school2 = School.new {
                name = "Harrow"
                region = region1
            }

            val school3 = School.new {
                name = "Winchester"
                region = region2
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
                region = region1
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

    @Test fun preloadOptionalReferencesOnASizedIterable() {
        withTables(Regions, Schools) {

            val region1 = Region.new {
                name = "United Kingdom"
            }

            val region2 = Region.new {
                name = "England"
            }

            val school1 = School.new {
                name = "Eton"
                region = region1
                secondaryRegion = region2
            }

            val school2 = School.new {
                name = "Harrow"
                region = region1
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
                region = region1
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
                region = region1
            }

            val school2 = School.new {
                name = "Harrow"
                region = region1
            }

            val school3 = School.new {
                name = "Winchester"
                region = region2
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
                val cache = TransactionManager.current().entityCache

                School.all().with(School::students)

                assertEqualCollections(cache.getReferrers<Student>(school1.id, Students.school)?.toList().orEmpty(), student1)
                assertEqualCollections(cache.getReferrers<Student>(school2.id, Students.school)?.toList().orEmpty(), student2)
                assertEqualCollections(cache.getReferrers<Student>(school3.id, Students.school)?.toList().orEmpty(), student3, student4)
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
                region = region1
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
                val cache = TransactionManager.current().entityCache

                School.find { Schools.id eq school1.id }.first().load(School::students)

                assertEqualCollections(cache.getReferrers<Student>(school1.id, Students.school)?.toList().orEmpty(), student1, student2, student3)
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
                region = region1
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
                val cache = TransactionManager.current().entityCache

                School.all().with(School::students, Student::detentions)

                assertEqualCollections(cache.getReferrers<Student>(school1.id, Students.school)?.toList().orEmpty(), student1, student2)
                assertEqualCollections(cache.getReferrers<Detention>(student1.id, Detentions.student)?.toList().orEmpty(), detention1, detention2)
                assertEqualCollections(cache.getReferrers<Detention>(student2.id, Detentions.student)?.toList().orEmpty(), emptyList())
            }
        }
    }

    @Test fun preloadInnerTableLinkOnASizedIterable() {

        withTables(Regions, Schools, Holidays, SchoolHolidays) {

            val now = System.currentTimeMillis()
            val now10 = now + 10

            val region1 = Region.new {
                name = "United Kingdom"
            }

            val region2 = Region.new {
                name = "England"
            }

            val school1 = School.new {
                name = "Eton"
                region = region1
            }

            val school2 = School.new {
                name = "Harrow"
                region = region1
            }

            val school3 = School.new {
                name = "Winchester"
                region = region2
            }

            val holiday1 = Holiday.new {
                holidayStart = now
                holidayEnd = now10
            }

            val holiday2 = Holiday.new {
                holidayStart = now
                holidayEnd = now10
            }

            val holiday3 = Holiday.new {
                holidayStart = now
                holidayEnd = now10
            }

            school1.holidays = SizedCollection(holiday1, holiday2)
            school2.holidays = SizedCollection(holiday3)

            commit()

            inTopLevelTransaction(Connection.TRANSACTION_SERIALIZABLE, 1) {
                School.all().with(School::holidays)
                val cache = TransactionManager.current().entityCache

                assertEqualCollections(cache.getReferrers<Holiday>(school1.id, SchoolHolidays.school)?.toList().orEmpty(), holiday1, holiday2)
                assertEqualCollections(cache.getReferrers<Holiday>(school2.id, SchoolHolidays.school)?.toList().orEmpty(), holiday3)
                assertEqualCollections(cache.getReferrers<Holiday>(school3.id, SchoolHolidays.school)?.toList().orEmpty(), emptyList())
            }
        }
    }

    @Test fun preloadInnerTableLinkOnAnEntity() {
        withTables(Regions, Schools, Holidays, SchoolHolidays) {

            val now = System.currentTimeMillis()
            val now10 = now + 10

            val region1 = Region.new {
                name = "United Kingdom"
            }

            val school1 = School.new {
                name = "Eton"
                region = region1
            }

            val holiday1 = Holiday.new {
                holidayStart = now
                holidayEnd = now10
            }

            val holiday2 = Holiday.new {
                holidayStart = now
                holidayEnd = now10
            }

            val holiday3 = Holiday.new {
                holidayStart = now
                holidayEnd = now10
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

            commit()

            School.find {
                Schools.id eq school1.id
            }.first().load(School::holidays)

            val cache = TransactionManager.current().entityCache

            assertEquals(true, cache.getReferrers<Holiday>(school1.id, SchoolHolidays.school)?.contains(holiday1))
            assertEquals(true, cache.getReferrers<Holiday>(school1.id, SchoolHolidays.school)?.contains(holiday2))
            assertEquals(true, cache.getReferrers<Holiday>(school1.id, SchoolHolidays.school)?.contains(holiday3))
        }
    }

    @Test fun preloadRelationAtDepth() {

        withTables(Regions, Schools, Holidays, SchoolHolidays, Students, Notes) {

            val region1 = Region.new {
                name = "United Kingdom"
            }

            val school1 = School.new {
                name = "Eton"
                region = region1
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

            val cache = TransactionManager.current().entityCache

            assertEquals(true, cache.getReferrers<Student>(school1.id, Students.school)?.contains(student1))
            assertEquals(true, cache.getReferrers<Student>(school1.id, Students.school)?.contains(student2))
            assertEquals(note1, cache.getReferrers<Note>(student1.id, Notes.student)?.first())
            assertEquals(note2, cache.getReferrers<Note>(student2.id, Notes.student)?.first())
        }
    }

    @Test fun preloadBackReferrenceOnASizedIterable() {

        withTables(Regions, Schools, Students, StudentBios) {
            val region1 = Region.new {
                name = "United States"
            }

            val school1 = School.new {
                name = "Eton"
                region = region1
            }

            val student1 = Student.new {
                name = "James Smith"
                school = school1
            }

            val student2 = Student.new {
                name = "John Smith"
                school = school1
            }

            val bio1 = StudentBio.new {
                student = student1
                dateOfBirth = "01/01/2000"
            }

            val bio2 = StudentBio.new {
                student = student2
                dateOfBirth = "01/01/2002"
            }

            commit()

            inTopLevelTransaction(Connection.TRANSACTION_SERIALIZABLE, 1) {
                Student.all().with(Student::bio)
                val cache = TransactionManager.current().entityCache

                assertEqualCollections(cache.getReferrers<StudentBio>(student1.id, StudentBios.student)?.toList().orEmpty(), bio1)
                assertEqualCollections(cache.getReferrers<StudentBio>(student2.id, StudentBios.student)?.toList().orEmpty(), bio2)
            }
        }
    }

    @Test fun preloadBackReferrenceOnAnEntity() {

        withTables(Regions, Schools, Students, StudentBios) {
            val region1 = Region.new {
                name = "United States"
            }

            val school1 = School.new {
                name = "Eton"
                region = region1
            }

            val student1 = Student.new {
                name = "James Smith"
                school = school1
            }

            val student2 = Student.new {
                name = "John Smith"
                school = school1
            }

            val bio1 = StudentBio.new {
                student = student1
                dateOfBirth = "01/01/2000"
            }

            val bio2 = StudentBio.new {
                student = student2
                dateOfBirth = "01/01/2002"
            }

            commit()

            inTopLevelTransaction(Connection.TRANSACTION_SERIALIZABLE, 1) {
                Student.all().first().load(Student::bio)
                val cache = TransactionManager.current().entityCache

                assertEqualCollections(cache.getReferrers<StudentBio>(student1.id, StudentBios.student)?.toList().orEmpty(), bio1)
            }
        }
    }

    @Test fun `test reference cache doesn't fully invalidated on set entity reference`() {
        withTables(Regions, Schools, Students, StudentBios) {
            val region1 = Region.new {
                name = "United States"
            }

            val school1 = School.new {
                name = "Eton"
                region = region1
            }

            val student1 = Student.new {
                name = "James Smith"
                school = school1
            }

            val student2 = Student.new {
                name = "John Smith"
                school = school1
            }

            val bio1 = StudentBio.new {
                student = student1
                dateOfBirth = "01/01/2000"
            }

            kotlin.test.assertEquals(bio1, student1.bio)
            kotlin.test.assertEquals(bio1.student, student1)
        }
    }

    @Test fun `test nested entity initialization`() {
        withTables(Posts, Categories, Boards) {
            val post = Post.new {
                parent = Post.new {
                    board = Board.new {
                        name = "Parent Board"
                    }
                    category = Category.new {
                        title = "Parent Category"
                    }
                }
                category = Category.new {
                    title = "Child Category"
                }

                optCategory = parent!!.category
            }

            assertEquals("Parent Board", post.parent?.board?.name)
            assertEquals("Parent Category", post.parent?.category?.title)
            assertEquals("Parent Category", post.optCategory?.title)
            assertEquals("Child Category", post.category?.title)
        }
    }

    @Test fun `test explicit entity constructor`() {
        var createBoardCalled = false
        fun createBoard(id: EntityID<Int>): Board {
            createBoardCalled = true
            return Board(id)
        }
        val boardEntityClass = object : IntEntityClass<Board>(Boards, entityCtor = ::createBoard) { }

        withTables(Boards) {
            val board = boardEntityClass.new {
                name = "Test Board"
            }

            assertEquals("Test Board", board.name)
            assertTrue(
                createBoardCalled,
                "Expected createBoardCalled to be called"
            )
        }
    }
}
