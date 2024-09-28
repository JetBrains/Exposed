package org.jetbrains.exposed.sql.tests.shared.entities

import org.jetbrains.exposed.dao.*
import org.jetbrains.exposed.dao.id.CompositeID
import org.jetbrains.exposed.dao.id.CompositeIdTable
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.tests.DatabaseTestsBase
import org.jetbrains.exposed.sql.tests.TestDB
import org.jetbrains.exposed.sql.tests.currentTestDB
import org.jetbrains.exposed.sql.tests.shared.assertEqualCollections
import org.jetbrains.exposed.sql.tests.shared.assertEqualLists
import org.jetbrains.exposed.sql.tests.shared.assertEquals
import org.jetbrains.exposed.sql.tests.shared.assertTrue
import org.jetbrains.exposed.sql.tests.shared.expectException
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.transactions.inTopLevelTransaction
import org.junit.Test
import java.sql.Connection
import java.util.*
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull

// SQLite excluded from most tests as it only allows auto-increment on single column PKs.
// SQL Server is sometimes excluded because it doesn't allow inserting explicit values for identity columns.
class CompositeIdTableEntityTest : DatabaseTestsBase() {
    // CompositeIdTable with 2 key columns - int & uuid (both db-generated)
    object Publishers : CompositeIdTable("publishers") {
        val pubId = integer("pub_id").autoIncrement().entityId()
        val isbn = uuid("isbn_code").autoGenerate().entityId()
        val name = varchar("publisher_name", 32)

        override val primaryKey = PrimaryKey(pubId, isbn)
    }

    class Publisher(id: EntityID<CompositeID>) : CompositeEntity(id) {
        companion object : CompositeEntityClass<Publisher>(Publishers)

        var name by Publishers.name
        val authors by Author referrersOn Authors
        val office by Office optionalBackReferencedOn Offices
        val allOffices by Office optionalReferrersOn Offices
    }

    // IntIdTable with 1 key columns - int (db-generated)
    object Authors : IntIdTable("authors") {
        val publisherId = integer("publisher_id")
        val publisherIsbn = uuid("publisher_isbn")
        val penName = varchar("pen_name", 32)

        // FK constraint with multiple columns is created as a table-level constraint
        init {
            foreignKey(publisherId, publisherIsbn, target = Publishers.primaryKey)
        }
    }

    class Author(id: EntityID<Int>) : IntEntity(id) {
        companion object : IntEntityClass<Author>(Authors)

        var publisher by Publisher referencedOn Authors
        var penName by Authors.penName
    }

    // CompositeIdTable with 1 key column - int (db-generated)
    object Books : CompositeIdTable("books") {
        val bookId = integer("book_id").autoIncrement().entityId()
        val title = varchar("title", 32)
        val author = optReference("author_id", Authors)

        override val primaryKey = PrimaryKey(bookId)
    }

    class Book(id: EntityID<CompositeID>) : CompositeEntity(id) {
        companion object : CompositeEntityClass<Book>(Books)

        var title by Books.title
        var author by Author optionalReferencedOn Books.author
        val review by Review backReferencedOn Reviews
    }

    // CompositeIdTable with 2 key columns - string & long (neither db-generated)
    object Reviews : CompositeIdTable("reviews") {
        val content = varchar("code", 8).entityId()
        val rank = long("rank").entityId()
        val book = integer("book_id")

        override val primaryKey = PrimaryKey(content, rank)

        init {
            foreignKey(book, target = Books.primaryKey)
        }
    }

    class Review(id: EntityID<CompositeID>) : CompositeEntity(id) {
        companion object : CompositeEntityClass<Review>(Reviews)

        var book by Book referencedOn Reviews
    }

    // CompositeIdTable with 3 key columns - string, string, & int (none db-generated)
    object Offices : CompositeIdTable("offices") {
        val zipCode = varchar("zip_code", 8).entityId()
        val name = varchar("name", 64).entityId()
        val areaCode = integer("area_code").entityId()
        val staff = long("staff").nullable()
        val publisherId = integer("publisher_id").nullable()
        val publisherIsbn = uuid("publisher_isbn").nullable()

        override val primaryKey = PrimaryKey(zipCode, name, areaCode)
        init {
            foreignKey(publisherId, publisherIsbn, target = Publishers.primaryKey)
        }
    }

    class Office(id: EntityID<CompositeID>) : CompositeEntity(id) {
        companion object : CompositeEntityClass<Office>(Offices)

        var staff by Offices.staff
        var publisher by Publisher optionalReferencedOn Offices
    }

    private val allTables = arrayOf(Publishers, Authors, Books, Reviews, Offices)

    @Test
    fun testCreateAndDropCompositeIdTable() {
        withDb(excludeSettings = listOf(TestDB.SQLITE)) {
            try {
                SchemaUtils.create(tables = allTables)

                allTables.forEach { assertTrue(it.exists()) }
                assertTrue(SchemaUtils.statementsRequiredToActualizeScheme(tables = allTables).isEmpty())
            } finally {
                SchemaUtils.drop(tables = allTables)
            }
        }
    }

    @Test
    fun testCreateWithMissingIdColumns() {
        val missingIdsTable = object : CompositeIdTable("missing_ids_table") {
            val age = integer("age")
            val name = varchar("name", 50)
            override val primaryKey = PrimaryKey(age, name)
        }

        withDb {
            // table can be created with no issue
            SchemaUtils.create(missingIdsTable)

            expectException<IllegalStateException> {
                // but trying to use id property requires idColumns not being empty
                missingIdsTable.select(missingIdsTable.id).toList()
            }

            SchemaUtils.drop(missingIdsTable)
        }
    }

    @Test
    fun testInsertAndSelectUsingDAO() {
        withTables(excludeSettings = listOf(TestDB.SQLITE), Publishers) {
            val p1 = Publisher.new {
                name = "Publisher A"
            }

            val result1 = Publisher.all().single()
            assertEquals("Publisher A", result1.name)
            // can compare entire entity objects
            assertEquals(p1, result1)
            // or entire entity ids
            assertEquals(p1.id, result1.id)
            // or the value wrapped by entity id
            assertEquals(p1.id.value, result1.id.value)
            // or the composite id components
            assertEquals(p1.id.value[Publishers.pubId], result1.id.value[Publishers.pubId])
            assertEquals(p1.id.value[Publishers.isbn], result1.id.value[Publishers.isbn])

            Publisher.new { name = "Publisher B" }
            Publisher.new { name = "Publisher C" }

            val resul2 = Publisher.all().toList()
            assertEquals(3, resul2.size)
        }
    }

    @Test
    fun testInsertAndSelectUsingDSL() {
        withTables(excludeSettings = listOf(TestDB.SQLITE), Publishers) {
            Publishers.insert {
                it[name] = "Publisher A"
            }

            val result = Publishers.selectAll().single()
            assertEquals("Publisher A", result[Publishers.name])

            // test all id column components are accessible from single ResultRow access
            val idResult = result[Publishers.id]
            assertIs<EntityID<CompositeID>>(idResult)
            val pubIdResult = idResult.value[Publishers.pubId]
            assertEquals(result[Publishers.pubId], pubIdResult)
            assertEquals(result[Publishers.isbn], idResult.value[Publishers.isbn])

            // test that using composite id column in DSL query builder works
            val dslQuery = Publishers
                .select(Publishers.id) // should deconstruct to 2 columns
                .where { Publishers.id eq idResult } // should deconstruct to 2 ops
                .prepareSQL(this)
            val selectClause = dslQuery.substringAfter("SELECT ").substringBefore(" FROM")
            // id column should deconstruct to 2 columns from PK
            assertEquals(2, selectClause.split(", ", ignoreCase = true).size)
            val whereClause = dslQuery.substringAfter("WHERE ")
            // 2 column in composite PK to check, joined by single AND operator
            assertEquals(2, whereClause.split("AND", ignoreCase = true).size)

            // test equality comparison fails if composite columns do not match
            expectException<IllegalStateException> {
                val fake = EntityID(CompositeID { it[Publishers.pubId] = 7 }, Publishers)
                Publishers.selectAll().where { Publishers.id eq fake }
            }

            // test equality comparison succeeds with partial match to composite column unwrapped value
            val pubIdValue: Int = pubIdResult.value
            assertEquals(0, Publishers.selectAll().where { Publishers.pubId neq pubIdValue }.count())
        }
    }

    @Test
    fun testInsertAndGetCompositeIds() {
        withTables(excludeSettings = listOf(TestDB.SQLITE, TestDB.SQLSERVER), Publishers) {
            // insert individual components
            val id1: EntityID<CompositeID> = Publishers.insertAndGetId {
                it[pubId] = 725
                it[isbn] = UUID.randomUUID()
                it[name] = "Publisher A"
            }
            assertEquals(725, id1.value[Publishers.pubId].value)

            val id2: EntityID<CompositeID> = Publishers.insertAndGetId {
                it[name] = "Publisher B"
            }
            val expectedNextVal = if (currentTestDB in TestDB.ALL_MYSQL_LIKE || currentTestDB == TestDB.H2_V1) 726 else 1
            assertEquals(expectedNextVal, id2.value[Publishers.pubId].value)

            // insert as composite ID
            val id3: EntityID<CompositeID> = Publishers.insertAndGetId {
                it[id] = CompositeID { id ->
                    id[pubId] = 999
                    id[isbn] = UUID.randomUUID()
                }
                it[name] = "Publisher C"
            }
            assertEquals(999, id3.value[Publishers.pubId].value)

            // insert as EntityID<CompositeID>
            val id4: EntityID<CompositeID> = Publishers.insertAndGetId {
                it[id] = EntityID(
                    CompositeID { id ->
                        id[pubId] = 111
                        id[isbn] = UUID.randomUUID()
                    },
                    Publishers
                )
                it[name] = "Publisher C"
            }
            assertEquals(111, id4.value[Publishers.pubId].value)
        }
    }

    @Test
    fun testInsertUsingManualCompositeIds() {
        withTables(excludeSettings = listOf(TestDB.SQLITE, TestDB.SQLSERVER), Publishers) {
            // manual using DSL
            Publishers.insert {
                it[pubId] = 725
                it[isbn] = UUID.randomUUID()
                it[name] = "Publisher A"
            }

            assertEquals(725, Publishers.selectAll().single()[Publishers.pubId].value)

            // manual using DAO - all PK columns
            val fullId = CompositeID {
                it[Publishers.pubId] = 611
                it[Publishers.isbn] = UUID.randomUUID()
            }
            val p2Id = Publisher.new(fullId) {
                name = "Publisher B"
            }.id
            assertEquals(611, p2Id.value[Publishers.pubId].value)
            assertEquals(611, Publisher.findById(p2Id)?.id?.value?.get(Publishers.pubId)?.value)
        }
    }

    @Test
    fun testFindByCompositeId() {
        withTables(excludeSettings = listOf(TestDB.SQLITE, TestDB.SQLSERVER), Publishers) {
            val id1: EntityID<CompositeID> = Publishers.insertAndGetId {
                it[pubId] = 725
                it[isbn] = UUID.randomUUID()
                it[name] = "Publisher A"
            }

            val p1 = Publisher.findById(id1)
            assertNotNull(p1)
            assertEquals(725, p1.id.value[Publishers.pubId].value)

            val id2: EntityID<CompositeID> = Publisher.new {
                name = "Publisher B"
            }.id

            val p2 = Publisher.findById(id2)
            assertNotNull(p2)
            assertEquals("Publisher B", p2.name)
            assertEquals(id2.value[Publishers.pubId], p2.id.value[Publishers.pubId])

            // test findById() using CompositeID value
            val compositeId1: CompositeID = id1.value
            val p3 = Publisher.findById(compositeId1)
            assertNotNull(p3)
            assertEquals(p1, p3)
        }
    }

    @Test
    fun testFindWithDSLBuilder() {
        withTables(excludeSettings = listOf(TestDB.SQLITE), Publishers) {
            val p1 = Publisher.new {
                name = "Publisher A"
            }

            assertEquals(p1.id, Publisher.find { Publishers.name like "% A" }.single().id)

            val p2 = Publisher.find { Publishers.id eq p1.id }.single()
            assertEquals(p1, p2)

            // test select using partial match to composite column unwrapped value
            val existingIsbnValue: UUID = p1.id.value[Publishers.isbn].value
            val p3 = Publisher.find { Publishers.isbn eq existingIsbnValue }.single()
            assertEquals(p1, p3)
        }
    }

    @Test
    fun testUpdateCompositeEntity() {
        withTables(excludeSettings = listOf(TestDB.SQLITE), Publishers) {
            val p1 = Publisher.new {
                name = "Publisher A"
            }

            p1.name = "Publisher B"

            assertEquals("Publisher B", Publisher.all().single().name)
        }
    }

    @Test
    fun testDeleteCompositeEntity() {
        withTables(excludeSettings = listOf(TestDB.SQLITE), Publishers) {
            val p1 = Publisher.new {
                name = "Publisher A"
            }
            val p2 = Publisher.new {
                name = "Publisher B"
            }

            assertEquals(2, Publisher.all().count())

            p1.delete()

            val result = Publisher.all().single()
            assertEquals("Publisher B", result.name)
            assertEquals(p2.id, result.id)

            // test delete using partial match to composite column unwrapped value
            val existingPubIdValue: Int = p2.id.value[Publishers.pubId].value
            Publishers.deleteWhere { pubId eq existingPubIdValue }
            assertEquals(0, Publisher.all().count())
        }
    }

    object Towns : CompositeIdTable("towns") {
        val zipCode = varchar("zip_code", 8).entityId()
        val name = varchar("name", 64).entityId()
        val population = long("population").nullable()
        override val primaryKey = PrimaryKey(zipCode, name)
    }

    class Town(id: EntityID<CompositeID>) : CompositeEntity(id) {
        companion object : CompositeEntityClass<Town>(Towns)

        var population by Towns.population
    }

    @Test
    fun testCompositeIdTableWithSQLite() {
        withTables(excludeSettings = TestDB.ALL - TestDB.SQLITE, Towns) {
            val townAId = Towns.insertAndGetId {
                it[zipCode] = "1A2 3B4"
                it[name] = "Town A"
            }
            val townBIdValue = CompositeID {
                it[Towns.zipCode] = "5C6 7D8"
                it[Towns.name] = "Town B"
            }
            Town.new(townBIdValue) {
                population = 123456789
            }

            assertEquals(2, Town.all().count())
            assertEquals(townAId, Town.find { Towns.id neq townBIdValue and Towns.population.isNull() }.single().id)
        }
    }

    @Test
    fun testIsNullAndEqWithAlias() {
        withTables(Towns) {
            val townAValue = CompositeID {
                it[Towns.zipCode] = "1A2 3B4"
                it[Towns.name] = "Town A"
            }
            val townAId = Towns.insertAndGetId { it[id] = townAValue }

            val smallCity = Towns.alias("small_city")

            val result1 = smallCity.selectAll().where {
                smallCity[Towns.id].isNotNull() and (smallCity[Towns.id] eq townAId)
            }.single()
            assertNull(result1[smallCity[Towns.population]])

            val result2 = smallCity.select(smallCity[Towns.name]).where {
                smallCity[Towns.id] eq townAId.value
            }.single()
            assertEquals(townAValue[Towns.name], result2[smallCity[Towns.name]])
        }
    }

    @Test
    fun testIdParamWithCompositeValue() {
        withTables(Towns) {
            val townAValue = CompositeID {
                it[Towns.zipCode] = "1A2 3B4"
                it[Towns.name] = "Town A"
            }
            val townAId = Towns.insertAndGetId {
                it[id] = townAValue
                it[population] = 4
            }

            val query = Towns.selectAll().where { Towns.id eq idParam(townAId, Towns.id) }
            val whereClause = query.prepareSQL(this, prepared = true).substringAfter("WHERE ")
            assertEquals("(${fullIdentity(Towns.zipCode)} = ?) AND (${fullIdentity(Towns.name)} = ?)", whereClause)
            assertEquals(4, query.single()[Towns.population])
        }
    }

    @Test
    fun testFlushingUpdatedEntity() {
        withTables(Towns) {
            val id = CompositeID {
                it[Towns.zipCode] = "1A2 3B4"
                it[Towns.name] = "Town A"
            }

            inTopLevelTransaction(Connection.TRANSACTION_SERIALIZABLE) {
                Town.new(id) {
                    population = 1000
                }
            }
            inTopLevelTransaction(Connection.TRANSACTION_SERIALIZABLE) {
                val town = Town[id]
                town.population = 2000
            }
            inTopLevelTransaction(Connection.TRANSACTION_SERIALIZABLE) {
                val town = Town[id]
                assertEquals(2000, town.population)
            }
        }
    }

    @Test
    fun testInsertAndSelectReferencedEntities() {
        withTables(excludeSettings = listOf(TestDB.SQLITE), tables = allTables) {
            val publisherA = Publisher.new {
                name = "Publisher A"
            }
            val authorA = Author.new {
                publisher = publisherA
                penName = "Author A"
            }
            val authorB = Author.new {
                publisher = publisherA
                penName = "Author B"
            }
            val bookA = Book.new {
                title = "Book A"
                author = authorB
            }
            Book.new {
                title = "Book B"
                author = authorB
            }
            val reviewIdValue = CompositeID {
                it[Reviews.content] = "Not bad"
                it[Reviews.rank] = 12345
            }
            val reviewA: Review = Review.new(reviewIdValue) {
                book = bookA
            }
            val officeAIdValue = CompositeID {
                it[Offices.zipCode] = "1A2 3B4"
                it[Offices.name] = "Office A"
                it[Offices.areaCode] = 789
            }
            val officeA = Office.new(officeAIdValue) {}
            val officeBIdValue = CompositeID {
                it[Offices.zipCode] = "5C6 7D8"
                it[Offices.name] = "Office B"
                it[Offices.areaCode] = 456
            }
            val officeB = Office.new(officeBIdValue) {
                publisher = publisherA
            }

            // child entity references
            assertEquals(publisherA.id.value[Publishers.pubId], authorA.publisher.id.value[Publishers.pubId])
            assertEquals(publisherA, authorA.publisher)
            assertEquals(publisherA, authorB.publisher)
            assertEquals(publisherA, bookA.author?.publisher)
            assertEquals(authorB, bookA.author)
            assertEquals(bookA.id, reviewA.book.id)
            assertEquals(authorB, reviewA.book.author)
            assertNull(officeA.publisher)
            assertEquals(publisherA, officeB.publisher)

            // parent entity references
            assertEquals(reviewA, bookA.review)
            assertEqualCollections(publisherA.authors.toList(), listOf(authorA, authorB))
            assertNotNull(publisherA.office)
            // if multiple children reference parent, backReferencedOn & optBackReferencedOn save last one
            assertEquals(officeB, publisherA.office)
            assertEqualCollections(publisherA.allOffices.toList(), listOf(officeB))
        }
    }

    @Test
    fun testInListWithCompositeIdEntities() {
        withTables(excludeSettings = listOf(TestDB.SQLITE), Publishers) {
            val id1: EntityID<CompositeID> = Publishers.insertAndGetId {
                it[name] = "Publisher A"
            }
            val id2: EntityID<CompositeID> = Publishers.insertAndGetId {
                it[name] = "Publisher B"
            }

            val compositeIds = listOf(id1.value, id2.value)
            val keyColumns = Publishers.idColumns.toList()
            val result1 = Publishers.selectAll().where { keyColumns inList compositeIds }.count()
            assertEquals(2, result1)
            val result2 = Publishers.selectAll().where { keyColumns notInList compositeIds }.count()
            assertEquals(0, result2)

            val result3 = Publishers.selectAll().where { Publishers.id inList compositeIds }.count()
            assertEquals(2, result3)
            val result4 = Publishers.selectAll().where { Publishers.id notInList compositeIds }.count()
            assertEquals(0, result4)
        }
    }

    @Test
    fun testPreloadReferencedOn() {
        withTables(excludeSettings = listOf(TestDB.SQLITE), tables = allTables) {
            val publisherA = Publisher.new {
                name = "Publisher A"
            }
            val authorA = Author.new {
                publisher = publisherA
                penName = "Author A"
            }
            Author.new {
                publisher = publisherA
                penName = "Author B"
            }
            val officeAIdValue = CompositeID {
                it[Offices.zipCode] = "1A2 3B4"
                it[Offices.name] = "Office A"
                it[Offices.areaCode] = 789
            }
            val officeA = Office.new(officeAIdValue) {}
            val officeBIdValue = CompositeID {
                it[Offices.zipCode] = "5C6 7D8"
                it[Offices.name] = "Office B"
                it[Offices.areaCode] = 456
            }
            val officeB = Office.new(officeBIdValue) {
                publisher = publisherA
            }

            commit()

            inTopLevelTransaction(Connection.TRANSACTION_SERIALIZABLE) {
                maxAttempts = 1
                // preload referencedOn - child to single parent
                Author.find { Authors.id eq authorA.id }.first().load(Author::publisher)
                val foundAuthor = Author.testCache(authorA.id)
                assertNotNull(foundAuthor)
                assertEquals(publisherA.id, Publisher.testCache(foundAuthor.readCompositeIDValues(Publishers))?.id)
            }

            inTopLevelTransaction(Connection.TRANSACTION_SERIALIZABLE) {
                maxAttempts = 1
                // preload optionalReferencedOn - child to single parent?
                Office.all().with(Office::publisher)
                val foundOfficeA = Office.testCache(officeA.id)
                assertNotNull(foundOfficeA)
                val foundOfficeB = Office.testCache(officeB.id)
                assertNotNull(foundOfficeB)
                assertNull(foundOfficeA.readValues[Offices.publisherId])
                assertNull(foundOfficeA.readValues[Offices.publisherIsbn])
                assertEquals(publisherA.id, Publisher.testCache(foundOfficeB.readCompositeIDValues(Publishers))?.id)
            }
        }
    }

    @Test
    fun testPreloadBackReferencedOn() {
        withTables(excludeSettings = listOf(TestDB.SQLITE), tables = allTables) {
            val publisherA = Publisher.new {
                name = "Publisher A"
            }
            val officeAIdValue = CompositeID {
                it[Offices.zipCode] = "1A2 3B4"
                it[Offices.name] = "Office A"
                it[Offices.areaCode] = 789
            }
            Office.new(officeAIdValue) {}
            val officeBIdValue = CompositeID {
                it[Offices.zipCode] = "5C6 7D8"
                it[Offices.name] = "Office B"
                it[Offices.areaCode] = 456
            }
            val officeB = Office.new(officeBIdValue) {
                publisher = publisherA
            }
            val bookA = Book.new {
                title = "Book A"
            }
            val reviewIdValue = CompositeID {
                it[Reviews.content] = "Not bad"
                it[Reviews.rank] = 12345
            }
            val reviewA: Review = Review.new(reviewIdValue) {
                book = bookA
            }

            commit()

            inTopLevelTransaction(Connection.TRANSACTION_SERIALIZABLE) {
                maxAttempts = 1
                // preload backReferencedOn - parent to single child
                val cache = TransactionManager.current().entityCache
                Book.find { Books.id eq bookA.id }.first().load(Book::review)
                val result = cache.getReferrers<Review>(bookA.id, Reviews.book)?.map { it.id }.orEmpty()
                assertEqualLists(listOf(reviewA.id), result)
            }

            inTopLevelTransaction(Connection.TRANSACTION_SERIALIZABLE) {
                maxAttempts = 1
                // preload optionalBackReferencedOn - parent to single child?
                val cache = TransactionManager.current().entityCache
                Publisher.find { Publishers.id eq publisherA.id }.first().load(Publisher::office)
                val result = cache.getReferrers<Office>(publisherA.id, Offices.publisherId)?.map { it.id }.orEmpty()
                assertEqualLists(listOf(officeB.id), result)
            }
        }
    }

    @Test
    fun testPreloadReferrersOn() {
        withTables(excludeSettings = listOf(TestDB.SQLITE), tables = allTables) {
            val publisherA = Publisher.new {
                name = "Publisher A"
            }
            val authorA = Author.new {
                publisher = publisherA
                penName = "Author A"
            }
            val authorB = Author.new {
                publisher = publisherA
                penName = "Author B"
            }
            val officeAIdValue = CompositeID {
                it[Offices.zipCode] = "1A2 3B4"
                it[Offices.name] = "Office A"
                it[Offices.areaCode] = 789
            }
            Office.new(officeAIdValue) {}
            val officeBIdValue = CompositeID {
                it[Offices.zipCode] = "5C6 7D8"
                it[Offices.name] = "Office B"
                it[Offices.areaCode] = 456
            }
            val officeB = Office.new(officeBIdValue) {
                publisher = publisherA
            }

            commit()

            inTopLevelTransaction(Connection.TRANSACTION_SERIALIZABLE) {
                maxAttempts = 1
                // preload referrersOn - parent to multiple children
                val cache = TransactionManager.current().entityCache
                Publisher.find { Publishers.id eq publisherA.id }.first().load(Publisher::authors)
                val result = cache.getReferrers<Author>(publisherA.id, Authors.publisherId)?.map { it.id }.orEmpty()
                assertEqualLists(listOf(authorA.id, authorB.id), result)
            }

            inTopLevelTransaction(Connection.TRANSACTION_SERIALIZABLE) {
                maxAttempts = 1
                // preload optionalReferrersOn - parent to multiple children?
                val cache = TransactionManager.current().entityCache
                Publisher.all().with(Publisher::allOffices)
                val result = cache.getReferrers<Office>(publisherA.id, Offices.publisherId)?.map { it.id }.orEmpty()
                assertEqualLists(listOf(officeB.id), result)
            }
        }
    }

    private fun Entity<*>.readCompositeIDValues(table: CompositeIdTable): EntityID<CompositeID> {
        val referenceColumns = this.klass.table.foreignKeys.single().references
        return EntityID(
            CompositeID {
                referenceColumns.forEach { (child, parent) ->
                    it[parent as Column<EntityID<Comparable<Any>>>] = this.readValues[child] as Comparable<Any>
                }
            },
            table
        )
    }
}
