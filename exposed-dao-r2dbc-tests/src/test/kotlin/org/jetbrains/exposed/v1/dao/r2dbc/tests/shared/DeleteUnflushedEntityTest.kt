package org.jetbrains.exposed.v1.dao.r2dbc.tests.shared

import kotlinx.coroutines.flow.toList
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.IntIdTable
import org.jetbrains.exposed.v1.core.statements.StatementContext
import org.jetbrains.exposed.v1.core.statements.StatementType
import org.jetbrains.exposed.v1.dao.r2dbc.IntEntity
import org.jetbrains.exposed.v1.dao.r2dbc.IntEntityClass
import org.jetbrains.exposed.v1.dao.r2dbc.entityCache
import org.jetbrains.exposed.v1.dao.r2dbc.flushCache
import org.jetbrains.exposed.v1.dao.r2dbc.registeredChanges
import org.jetbrains.exposed.v1.r2dbc.ExposedR2dbcException
import org.jetbrains.exposed.v1.r2dbc.R2dbcTransaction
import org.jetbrains.exposed.v1.r2dbc.selectAll
import org.jetbrains.exposed.v1.r2dbc.statements.SuspendStatementInterceptor
import org.jetbrains.exposed.v1.r2dbc.statements.api.R2dbcPreparedStatementApi
import org.jetbrains.exposed.v1.r2dbc.tests.R2dbcDatabaseTestsBase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class DeleteUnflushedEntityTest : R2dbcDatabaseTestsBase() {
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
        val author by Author referencedOn Books.author
    }

    private class StatementCounter : SuspendStatementInterceptor {
        private val counts = mutableMapOf<StatementType, Int>()

        val inserts: Int get() = counts[StatementType.INSERT] ?: 0
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
    fun testDeletingNewEntityThatIsStillReferencedFailsOnFlush() {
        withTables(Authors, Books) {
            val author = Author.new { name = "author1" }
            Book.new {
                title = "book1"
                this.author.set(author)
            }

            author.delete()

            assertFailsWith<IllegalStateException> { flushCache() }
        }
    }

    @Test
    fun testDeletingNewChildLeavesPersistedParentIntact() {
        withTables(Authors, Books) {
            val author = Author.newSuspend { name = "author1" }

            val counter = StatementCounter()
            registerInterceptor(counter)

            val book = Book.new {
                title = "book1"
                this.author.set(author)
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
        withTables(Authors, Books) {
            val author = Author.newSuspend { name = "author1" }
            Book.new {
                title = "book1"
                this.author.set(author)
            }

            assertFailsWith<ExposedR2dbcException> { author.delete() }
            entityCache.clear(flush = false)
        }
    }
}
