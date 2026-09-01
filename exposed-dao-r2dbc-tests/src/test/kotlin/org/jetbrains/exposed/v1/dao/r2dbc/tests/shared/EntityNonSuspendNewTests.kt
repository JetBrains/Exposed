package org.jetbrains.exposed.v1.dao.r2dbc.tests.shared

import kotlinx.coroutines.flow.single
import kotlinx.coroutines.flow.toList
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.IntIdTable
import org.jetbrains.exposed.v1.core.dao.id.UuidTable
import org.jetbrains.exposed.v1.core.intLiteral
import org.jetbrains.exposed.v1.core.statements.StatementContext
import org.jetbrains.exposed.v1.core.statements.StatementType
import org.jetbrains.exposed.v1.core.vendors.PostgreSQLDialect
import org.jetbrains.exposed.v1.dao.r2dbc.EntityChangeType
import org.jetbrains.exposed.v1.dao.r2dbc.IntEntity
import org.jetbrains.exposed.v1.dao.r2dbc.IntEntityClass
import org.jetbrains.exposed.v1.dao.r2dbc.UuidEntity
import org.jetbrains.exposed.v1.dao.r2dbc.UuidEntityClass
import org.jetbrains.exposed.v1.dao.r2dbc.flushCache
import org.jetbrains.exposed.v1.dao.r2dbc.registeredChanges
import org.jetbrains.exposed.v1.r2dbc.R2dbcTransaction
import org.jetbrains.exposed.v1.r2dbc.selectAll
import org.jetbrains.exposed.v1.r2dbc.statements.SuspendStatementInterceptor
import org.jetbrains.exposed.v1.r2dbc.statements.api.R2dbcPreparedStatementApi
import org.jetbrains.exposed.v1.r2dbc.tests.NOT_APPLICABLE_TO_JDBC
import org.jetbrains.exposed.v1.r2dbc.tests.R2dbcDatabaseTestsBase
import org.jetbrains.exposed.v1.r2dbc.tests.TestDB
import org.jetbrains.exposed.v1.r2dbc.tests.currentDialectTest
import org.jetbrains.exposed.v1.r2dbc.transactions.suspendTransaction
import org.junit.jupiter.api.Tag
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.uuid.Uuid

@Tag(NOT_APPLICABLE_TO_JDBC)
class EntityNonSuspendNewTests : R2dbcDatabaseTestsBase() {
    object Authors : IntIdTable("authors_nu") {
        val name = varchar("name", 50)
    }

    object Books : IntIdTable("books_nu") {
        val title = varchar("title", 50)
        val author = reference("author_id", Authors)
    }

    object Tokens : UuidTable("tokens_nu") {
        val label = varchar("label", 50)
    }

    /** Has a column that the insert neither sends nor gets back, so the flush has to read it. */
    object Stamped : IntIdTable("stamped_nu") {
        val name = varchar("name", 50)
        val stamp = integer("stamp").defaultExpression(intLiteral(7))
    }

    class Author(id: EntityID<Int>) : IntEntity(id) {
        var name by Authors.name
        val books by Book referrersOn Books.author

        companion object : IntEntityClass<Author>(Authors)
    }

    class StampedEntity(id: EntityID<Int>) : IntEntity(id) {
        var name by Stamped.name
        val stamp by Stamped.stamp

        companion object : IntEntityClass<StampedEntity>(Stamped)
    }

    class Book(id: EntityID<Int>) : IntEntity(id) {
        var title by Books.title
        val author by Author referencedOn Books.author

        companion object : IntEntityClass<Book>(Books)
    }

    class Token(id: EntityID<Uuid>) : UuidEntity(id) {
        var label by Tokens.label

        companion object : UuidEntityClass<Token>(Tokens)
    }

    private class StatementCounter : SuspendStatementInterceptor {
        private val counts = mutableMapOf<StatementType, Int>()

        val inserts: Int get() = counts[StatementType.INSERT] ?: 0
        val selects: Int get() = counts[StatementType.SELECT] ?: 0
        val updates: Int get() = counts[StatementType.UPDATE] ?: 0
        val deletes: Int get() = counts[StatementType.DELETE] ?: 0

        override suspend fun afterExecution(
            transaction: R2dbcTransaction,
            contexts: List<StatementContext>,
            executedStatement: R2dbcPreparedStatementApi
        ) {
            val type = contexts.firstOrNull()?.statement?.type ?: return
            counts[type] = (counts[type] ?: 0) + 1
        }
    }

    /**
     * Non-suspending on purpose: the whole point of `new` is that scheduling an entity does not
     * require a coroutine, so this compiles only as long as that stays true.
     */
    private fun buildAuthors(names: List<String>): List<Author> = names.map { author -> Author.new { name = author } }

    @Test
    fun testEntityIsScheduledWithoutBeingInserted() {
        withTables(Authors) {
            val counter = StatementCounter()
            registerInterceptor(counter)

            val author = Author.new { name = "author1" }
            assertEquals(0, counter.inserts, "no INSERT should run before the cache is flushed")

            assertNull(author.id._value)
            assertFailsWith<IllegalStateException> { author.id.value }

            assertEquals("author1", author.name)
            author.name = "author1 renamed"
            assertEquals("author1 renamed", author.name)
            assertEquals(0, counter.inserts)

            flushCache()

            assertEquals(1, counter.inserts)
            assertNotNull(author.id._value, "id must be populated once the insert is issued")
            assertEquals("author1 renamed", Authors.selectAll().single()[Authors.name])
            assertEquals(0, counter.updates)
        }
    }

    @Test
    fun testNoTransactionInContextFails() = withConnection { _, _ ->
        val failure = assertFailsWith<IllegalStateException> { Author.new { name = "author1" } }
        assertContains(assertNotNull(failure.message), "No transaction in context")
    }

    @Test
    fun testPendingInsertsShareASingleBatch() {
        // Oracle's R2DBC driver cannot return generated values from a batched statement, so there the
        // DAO sends one INSERT per entity instead.
        withTables(excludeSettings = listOf(TestDB.ORACLE), Authors) { testDb ->
            val counter = StatementCounter()
            registerInterceptor(counter)

            val authors = buildAuthors(listOf("author1", "author2", "author3"))
            assertEquals(0, counter.inserts)

            flushCache()

            assertEquals(1, counter.inserts, "all three entities are persisted by a single batch INSERT")
            assertEquals(listOf("author1", "author2", "author3"), authors.map { it.name })
            assertEquals(3, Authors.selectAll().toList().size)
        }
    }

    @Test
    fun testQueryFlushesPendingInsert() {
        withTables(Authors) {
            val counter = StatementCounter()
            registerInterceptor(counter)

            val author = Author.new { name = "author1" }

            assertEquals("author1", Authors.selectAll().single()[Authors.name])
            assertEquals(1, counter.inserts)
            assertNotNull(author.id._value)
        }
    }

    @Test
    fun testCommitFlushesPendingInsert() {
        withTables(Authors) {
            val author = Author.new { name = "author1" }

            commit()

            assertNotNull(author.id._value, "the commit issues the pending insert")
            assertEquals("author1", Authors.selectAll().single()[Authors.name])
        }
    }

    @Test
    fun testNewSuspendFlushesPendingInserts() {
        withTables(Authors) {
            val pending = Author.new { name = "author1" }

            val viaNewSuspend = Author.newSuspend { name = "author2" }

            assertNotNull(pending.id._value)
            assertNotNull(viaNewSuspend.id._value)
            val names = Authors.selectAll().orderBy(Authors.id).toList().map { it[Authors.name] }
            assertEquals(listOf("author1", "author2"), names)
        }
    }

    @Test
    fun testNestedTransactionQueryFlushesPendingInsert() {
        withTables(Authors, configure = { useNestedTransactions = true }) {
            val author = Author.new { name = "author1" }

            // A query flushes the whole transaction chain, outermost scope first.
            val rowsSeenInNestedScope = suspendTransaction { Authors.selectAll().toList().size }

            assertEquals(1, rowsSeenInNestedScope)
            assertNotNull(author.id._value)
        }
    }

    @Test
    fun testDeleteCancelsPendingInsert() {
        withTables(Authors) {
            val counter = StatementCounter()
            registerInterceptor(counter)

            val author = Author.new { name = "author1" }
            author.delete()

            flushCache()

            assertEquals(0, counter.inserts)
            assertEquals(0, counter.deletes)
            assertEquals(0, Authors.selectAll().toList().size)
        }
    }

    @Test
    fun testEntityChangeIsRegisteredOnFlushOnly() {
        withTables(Authors) {
            Author.new { name = "author1" }
            assertEquals(0, registeredChanges().size, "scheduling alone is not a change anyone can observe")

            flushCache()

            assertEquals(listOf(EntityChangeType.Created), registeredChanges().map { it.changeType })
        }
    }

    @Test
    fun testExplicitIdIsUsableBeforeTheInsert() {
        // SQL Server doesn't allow inserting an explicit value into an auto-increment (identity) column
        withTables(excludeSettings = listOf(TestDB.SQLSERVER), Authors) {
            val counter = StatementCounter()
            registerInterceptor(counter)

            val author = Author.new(42) { name = "author1" }

            assertEquals(42, author.id.value)

            assertEquals(0, counter.inserts)

            flushCache()

            assertEquals(1, counter.inserts)
            assertEquals(42, Authors.selectAll().single()[Authors.id].value)
        }
    }

    @Test
    fun testClientGeneratedIdIsUsableBeforeTheInsert() {
        withTables(Tokens) {
            val counter = StatementCounter()
            registerInterceptor(counter)

            val token = Token.new { label = "token1" }

            // `UuidTable` generates its id on the client, so this one needs no round trip either.
            val id = token.id.value

            assertEquals(0, counter.inserts)

            flushCache()

            assertEquals(1, counter.inserts)

            assertEquals(id, Tokens.selectAll().single()[Tokens.id].value)
        }
    }

    @Test
    fun testReferenceToAPendingEntity() {
        withTables(Authors, Books) {
            val counter = StatementCounter()
            registerInterceptor(counter)

            val author1 = Author.new { name = "author1" }
            val book = Book.new {
                title = "book1"
                // The child stores the parent's EntityID instance, which the parent's insert fills in.
                author.set(author1)
            }
            assertEquals(0, counter.inserts)

            flushCache()

            assertEquals(2, counter.inserts, "one batch per table")
            assertEquals(author1.id.value, Books.selectAll().single()[Books.author].value)
            assertEquals(author1.id, book.author().id)
            assertEquals("book1", author1.books.toList().single().title)
        }
    }

    @Test
    fun testReferrersReadFlushesThePendingParent() {
        withTables(Authors, Books) {
            val counter = StatementCounter()
            registerInterceptor(counter)

            val author1 = Author.new { name = "author1" }

            assertEquals(emptyList(), author1.books.toList().map { it.title })

            assertEquals(1, counter.inserts)
            assertNotNull(author1.id._value)
        }
    }

    @Tag(NOT_APPLICABLE_TO_JDBC)
    @Test
    fun testFlushReadsMissingValuesBackInOneQuery() {
        withTables(Stamped) {
            val counter = StatementCounter()
            registerInterceptor(counter)

            val entities = (1..5).map { number -> StampedEntity.new { name = "n$number" } }
            flushCache()

            // A database-side default is neither sent with the insert nor returned by the driver, so the flush has to
            // read those values back -- with one query for the whole batch, not one per inserted row.
            val insertReturnsAllColumns = currentDialectTest is PostgreSQLDialect
            val expectedSelects = if (insertReturnsAllColumns) 0 else 1
            assertEquals(
                expectedSelects,
                counter.selects,
                "missing values are read back in one query unless the insert already returns all columns"
            )
            assertEquals(5, entities.count { it.stamp == 7 })
        }
    }
}
