package org.jetbrains.exposed.sql.tests.shared.entities

import org.jetbrains.exposed.dao.CompositeEntity
import org.jetbrains.exposed.dao.CompositeEntityClass
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.id.CompositeID
import org.jetbrains.exposed.dao.id.CompositeIdTable
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.tests.DatabaseTestsBase
import org.jetbrains.exposed.sql.tests.TestDB
import org.jetbrains.exposed.sql.tests.currentTestDB
import org.jetbrains.exposed.sql.tests.shared.assertEquals
import org.jetbrains.exposed.sql.tests.shared.assertTrue
import org.jetbrains.exposed.sql.tests.shared.expectException
import org.junit.Test
import java.util.*
import kotlin.test.assertIs
import kotlin.test.assertNotNull

// SQLite excluded from most tests as it only allows auto-increment on single column PKs.
// SQL Server is sometimes excluded because it doesn't allow inserting explicit values for identity columns.
@Suppress("UnusedPrivateProperty")
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
        val review by Review backReferencedOn Reviews.book
    }

    // CompositeIdTable with 2 key columns - string & long (neither db-generated)
    object Reviews : CompositeIdTable("reviews") {
        val content = varchar("code", 8).entityId()
        val rank = long("rank").entityId()

        // FK constraint with single column can be created as a column-level constraint
        val book = reference("book_id", Books.bookId)

        override val primaryKey = PrimaryKey(content, rank)
    }

    class Review(id: EntityID<CompositeID>) : CompositeEntity(id) {
        companion object : CompositeEntityClass<Review>(Reviews)

        var book by Book referencedOn Reviews.book
    }

    private val allTables = arrayOf(Publishers, Authors, Books, Reviews)

    // The tests below will be removed before merging ------------------------------------------------------------
    // They only exist in case the branch is checked out, to view composite PK types, etc.

    @Test
    fun entityIdUseCases() {
        withTables(excludeSettings = listOf(TestDB.SQLITE), tables = allTables) {
            // entities
            val publisherA: Publisher = Publisher.new {
                name = "Publisher A"
            }
            val authorA: Author = Author.new {
                publisher = publisherA
                penName = "Author A"
            }
            val bookA: Book = Book.new {
                title = "Book A"
                author = authorA
            }
            val reviewIdValueOg = CompositeID {
                it[Reviews.content] = "Not bad"
                it[Reviews.rank] = 12345
            }
            val reviewA: Review = Review.new(reviewIdValueOg) {
                book = bookA
            }

            // entity id properties
            val publisherId: EntityID<CompositeID> = publisherA.id
            val authorId: EntityID<Int> = authorA.id
            val bookId: EntityID<CompositeID> = bookA.id
            val reviewId: EntityID<CompositeID> = reviewA.id

            // access wrapped entity id values
            val publisherIdValue: CompositeID = publisherId.value
            val authorIdValue: Int = authorId.value
            val bookIdValue: CompositeID = bookId.value
            val reviewIdValue: CompositeID = reviewId.value
            assertEquals(reviewIdValueOg, reviewId.value)

            // access individual composite entity id values - these are now each wrapped as EntityID
            val publisherIdComponent1: EntityID<Int> = publisherIdValue[Publishers.pubId]
            val publisherIdComponent2: EntityID<UUID> = publisherIdValue[Publishers.isbn]
            val bookIdComponent1: EntityID<Int> = bookIdValue[Books.bookId]
            val reviewIdComponent1: EntityID<String> = reviewIdValue[Reviews.content]
            val reviewIdComponent2: EntityID<Long> = reviewIdValue[Reviews.rank]

            // access individual composite values - requires unwrapping - no type erasure
            val publisherIdComponent1Value: Int = publisherIdValue[Publishers.pubId].value
            val publisherIdComponent2Value: UUID = publisherIdValue[Publishers.isbn].value
            val bookIdComponent1Value: Int = bookIdValue[Books.bookId].value
            val reviewIdComponent1Value: String = reviewIdValue[Reviews.content].value
            val reviewIdComponent2Value: Long = reviewIdValue[Reviews.rank].value

            // find entity by its id property - argument type EntityID<T> must match invoking type EntityClass<T, _>
            val foundPublisherA: Publisher? = Publisher.findById(publisherId)
            val foundAuthorA: Author? = Author.findById(authorId)
            val foundBookA: Book? = Book.findById(bookId)
            val foundReviewA: Review? = Review.findById(reviewId)
        }
    }

    @Test
    fun tableIdColumnUseCases() {
        withTables(excludeSettings = listOf(TestDB.SQLITE), tables = allTables) {
            // id columns
            val publisherIdColumn: Column<EntityID<CompositeID>> = Publishers.id
            val authorIdColumn: Column<EntityID<Int>> = Authors.id
            val bookIdColumn: Column<EntityID<CompositeID>> = Books.id

            // entity id values
            val publisherA: EntityID<CompositeID> = Publishers.insertAndGetId {
                it[name] = "Publisher A"
            }
            val authorA: EntityID<Int> = Authors.insertAndGetId {
                // need to access wrapped value of each component as table-level FK constraint means unwrapped columns
                it[publisherId] = publisherA.value[Publishers.pubId].value
                it[publisherIsbn] = publisherA.value[Publishers.isbn].value
                it[penName] = "Author A"
            }
            val bookA: EntityID<CompositeID> = Books.insertAndGetId {
                it[title] = "Book A"
                it[author] = authorA
            }
            val reviewA: EntityID<CompositeID> = Reviews.insertAndGetId {
                it[content] = "Not bad"
                it[rank] = 12345
                // don't need to access wrapped value as column-level FK constraint means copied EntityID column
                it[book] = bookA.value[Books.bookId]
            }

            // access entity id with single result row access
            val publisherResult: EntityID<CompositeID> = Publishers.selectAll().single()[Publishers.id]
            val authorResult: EntityID<Int> = Authors.selectAll().single()[Authors.id]
            val bookResult: EntityID<CompositeID> = Books.selectAll().single()[Books.id]
            val reviewResult: EntityID<CompositeID> = Reviews.selectAll().single()[Reviews.id]

            // add all id components to query builder with single column op - EntityID<T> == EntityID<T>
            Publishers.selectAll().where { Publishers.id eq publisherResult }.single() // deconstructs to use compound AND
            Authors.selectAll().where { Authors.id eq authorResult }.single()
            Books.selectAll().where { Books.id eq bookResult }.single()
            Reviews.selectAll().where { Reviews.id eq reviewResult }.single()
        }
    }

    @Test
    fun manualEntityIdUseCases() {
        withTables(excludeSettings = listOf(TestDB.SQLITE, TestDB.SQLSERVER), tables = allTables) {
            // manual using DSL
            val code = UUID.randomUUID()
            Publishers.insert {
                it[pubId] = 725
                it[isbn] = code
                it[name] = "Publisher A"
            }
            Authors.insert {
                it[publisherId] = 725
                it[publisherIsbn] = code
                it[penName] = "Author A"
            }
            Books.insert {
                it[title] = "Book A"
                it[author] = 1
            }
            Reviews.insert {
                it[content] = "Not bad"
                it[rank] = 12345
                it[book] = 1
            }

            // manual using DAO
            val publisherIdValue = CompositeID {
                it[Publishers.pubId] = 611
                it[Publishers.isbn] = UUID.randomUUID()
            }
            val publisherB: Publisher = Publisher.new(publisherIdValue) {
                name = "Publisher B"
            }
            val authorB: Author = Author.new {
                publisher = publisherB
                penName = "Author B"
            }
            val bookIdValue = CompositeID { it[Books.bookId] = 2 }
            val bookB: Book = Book.new(bookIdValue) {
                title = "Book B"
                author = authorB
            }
            val reviewIdValue = CompositeID {
                it[Reviews.content] = "Great"
                it[Reviews.rank] = 67890
            }
            Review.new(reviewIdValue) {
                book = bookB
            }

            // equality check - EntityID<T> == T
            Publishers.selectAll().where { Publishers.id eq publisherIdValue }.single()
            Authors.selectAll().where { Authors.id eq authorB.id }.single()
            Books.selectAll().where { Books.id eq bookIdValue }.single()
            Reviews.selectAll().where { Reviews.id eq reviewIdValue }.single()

            // find entity by its id value - argument type T must match invoking type EntityClass<T, _>
            val foundPublisherA: Publisher? = Publisher.findById(publisherIdValue)
            val foundAuthorA: Author? = Author.findById(authorB.id.value)
            val foundBookA: Book? = Book.findById(bookIdValue)
            val foundReviewA: Review? = Review.findById(reviewIdValue)
        }
    }
    // The tests above will be removed before merging ------------------------------------------------------------

    @Test
    fun testCreateAndDropCompositeIdTable() {
        withDb(excludeSettings = listOf(TestDB.SQLITE)) { testDb ->
            try {
                SchemaUtils.create(tables = allTables)

                allTables.forEach { assertTrue(it.exists()) }
                // MariaDB bug (EXPOSED-415) incorrectly generates NULL column
                // Can be confirmed in testCreateMissingTablesAndColumnsChangeCascadeType()
                if (testDb !in TestDB.ALL_MARIADB) {
                    assertTrue(SchemaUtils.statementsRequiredToActualizeScheme(tables = allTables).isEmpty())
                }
            } finally {
                SchemaUtils.drop(tables = allTables)
            }
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
            assertEquals(result[Publishers.pubId], idResult.value[Publishers.pubId])
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
        }
    }

    object Towns : CompositeIdTable("towns") {
        val zipCode = varchar("zip_code", 8).entityId()
        val name = varchar("name", 64).entityId()
        val areaCode = integer("area_code").entityId()
        val population = long("population").nullable()
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
                it[areaCode] = 789
            }
            val townBIdValue = CompositeID {
                it[Towns.zipCode] = "5C6 7D8"
                it[Towns.name] = "Town B"
                it[Towns.areaCode] = 456
            }
            Town.new(townBIdValue) {
                population = 123456789
            }

            assertEquals(2, Town.all().count())
            assertEquals(townAId, Town.find { Towns.id neq townBIdValue and Towns.population.isNull() }.single().id)
        }
    }

    @Test
    fun testInsertAndSelectReferencedEntities() {
        withTables(excludeSettings = listOf(TestDB.SQLITE), Publishers, Authors, Books, Reviews) {
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
            val bookB = Book.new {
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

            // child entity references
            assertEquals(publisherA.id.value[Publishers.pubId], authorA.publisher.id.value[Publishers.pubId])
            assertEquals(publisherA, authorA.publisher)
            assertEquals(publisherA, authorB.publisher)
            assertEquals(publisherA, bookA.author?.publisher)
            assertEquals(authorB, bookA.author)
            assertEquals(bookA.id, reviewA.book.id)
            assertEquals(authorB, reviewA.book.author)

            // parent entity references
            assertEquals(reviewA, bookA.review)
        }
    }
}
