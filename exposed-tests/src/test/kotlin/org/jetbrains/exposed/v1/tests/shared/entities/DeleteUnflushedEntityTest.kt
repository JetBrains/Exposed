package org.jetbrains.exposed.v1.tests.shared.entities

import org.jetbrains.exposed.v1.core.Transaction
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.IntIdTable
import org.jetbrains.exposed.v1.core.statements.StatementContext
import org.jetbrains.exposed.v1.core.statements.StatementInterceptor
import org.jetbrains.exposed.v1.core.statements.StatementType
import org.jetbrains.exposed.v1.core.statements.api.PreparedStatementApi
import org.jetbrains.exposed.v1.dao.IntEntity
import org.jetbrains.exposed.v1.dao.IntEntityClass
import org.jetbrains.exposed.v1.dao.flushCache
import org.jetbrains.exposed.v1.dao.registeredChanges
import org.jetbrains.exposed.v1.exceptions.ExposedSQLException
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.tests.DatabaseTestsBase
import org.jetbrains.exposed.v1.tests.TestDB
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

class DeleteUnflushedEntityTest : DatabaseTestsBase() {
    object Authors : IntIdTable("du_authors") {
        val name = varchar("name", 50)
    }

    object Books : IntIdTable("du_books") {
        val title = varchar("title", 50)
        val author = reference("author_id", Authors)
    }

    class Author(id: EntityID<Int>) : IntEntity(id) {
        companion object : IntEntityClass<Author>(Authors)

        var name by Authors.name
    }

    class Book(id: EntityID<Int>) : IntEntity(id) {
        companion object : IntEntityClass<Book>(Books)

        var title by Books.title
        var author by Author referencedOn Books.author
    }

    private class StatementCounter : StatementInterceptor {
        private val counts = mutableMapOf<StatementType, Int>()

        val inserts: Int get() = counts[StatementType.INSERT] ?: 0
        val deletes: Int get() = counts[StatementType.DELETE] ?: 0

        override fun afterExecution(
            transaction: Transaction,
            contexts: List<StatementContext>,
            executedStatement: PreparedStatementApi
        ) {
            val type = contexts.firstOrNull()?.statement?.type ?: return
            counts[type] = (counts[type] ?: 0) + 1
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

            assertEquals(0, counter.inserts, "a cancelled insert must not reach the database")
            assertEquals(0, counter.deletes, "there is no row to delete")
            assertEquals(0, Authors.selectAll().toList().size)
            assertEquals(
                emptyList(),
                registeredChanges().map { it.changeType },
                "an entity that never reached the database is not observable"
            )
        }
    }

    @Test
    fun testAssigningReferenceFlushesTheReferencedEntity() {
        withTables(Authors, Books) {
            val counter = StatementCounter()
            registerInterceptor(counter)

            val author = Author.new { name = "author1" }
            Book.new {
                title = "book1"
                this.author = author
            }

            assertNotNull(author.id._value, "assigning the reference flushed the author")
            assertEquals(1, counter.inserts, "the author was inserted, the book is still pending")
            assertEquals(1, Authors.selectAll().toList().size)
        }
    }

    @Test
    fun testDeletingNewChildLeavesPersistedParentIntact() {
        withTables(Authors, Books) {
            val author = Author.new { name = "author1" }
            flushCache()

            val counter = StatementCounter()
            registerInterceptor(counter)

            val book = Book.new {
                title = "book1"
                this.author = author
            }
            book.delete()

            flushCache()

            assertEquals(0, counter.inserts, "the book's insert was cancelled")
            assertEquals(0, counter.deletes, "the book never had a row")
            assertEquals(1, Authors.selectAll().toList().size, "the author is untouched")
            assertEquals(0, Books.selectAll().toList().size)
        }
    }

    @Test
    fun testDeletingReferencedAuthorIsRejected() {
        // SQLite ignores foreign keys unless PRAGMA foreign_keys is on, so the delete silently orphans the book
        withTables(excludeSettings = listOf(TestDB.SQLITE), tables = arrayOf(Authors, Books)) {
            val author = Author.new { name = "author1" }
            Book.new {
                title = "book1"
                this.author = author
            }

            assertFailsWith<ExposedSQLException> { author.delete() }
        }
    }
}
