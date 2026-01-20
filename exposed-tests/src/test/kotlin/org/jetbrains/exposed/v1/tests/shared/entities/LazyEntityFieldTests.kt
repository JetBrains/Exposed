package org.jetbrains.exposed.v1.tests.shared.entities

import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.IntIdTable
import org.jetbrains.exposed.v1.core.statements.api.ExposedBlob
import org.jetbrains.exposed.v1.dao.*
import org.jetbrains.exposed.v1.jdbc.*
import org.jetbrains.exposed.v1.tests.DatabaseTestsBase
import org.jetbrains.exposed.v1.tests.MISSING_R2DBC_TEST
import org.jetbrains.exposed.v1.tests.shared.assertEquals
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@Tag(MISSING_R2DBC_TEST)
class LazyEntityFieldTests : DatabaseTestsBase() {

    object ArticlesTable : IntIdTable("articles") {
        val title = varchar("title", 200)
        val summary = text("summary")
        val content = text("content")
        val rawData = blob("raw_data").nullable()
    }

    class Article(id: EntityID<Int>) : IntEntity(id) {
        companion object : IntEntityClass<Article>(ArticlesTable)

        var title by ArticlesTable.title
        var summary by ArticlesTable.summary
        var content by ArticlesTable.content.lazy() // Lazy field
        var rawData by ArticlesTable.rawData.lazy() // Lazy field
    }

    fun JdbcTransaction.isInCache(id: EntityID<*>, column: Column<*>): Boolean {
        val table = column.table
        val entity = entityCache.data[table]?.get(id.value) ?: return false
        return entity.readValues.hasValue(column)
    }

    fun JdbcTransaction.assertInCache(id: EntityID<*>, vararg columns: Column<*>) {
        columns.forEach { column ->
            kotlin.test.assertTrue(
                isInCache(id, column),
                "Column ${column.name} should be in cache for entity with ID $id"
            )
        }
    }

    fun JdbcTransaction.assertNotInCache(id: EntityID<*>, vararg columns: Column<*>) {
        columns.forEach { column ->
            kotlin.test.assertFalse(
                isInCache(id, column),
                "Column ${column.name} should not be in cache for entity with ID $id"
            )
        }
    }

    @OptIn(InternalApi::class)
    fun Entity<*>.assertInLazyCache(vararg columns: Column<*>) {
        columns.forEach { column ->
            kotlin.test.assertTrue(
                isLazyFieldCached(column),
                "Lazy field ${column.name} should be in lazyFieldCache for entity with ID $id"
            )
        }
    }

    @OptIn(InternalApi::class)
    fun Entity<*>.assertNotInLazyCache(vararg columns: Column<*>) {
        columns.forEach { column ->
            kotlin.test.assertFalse(
                isLazyFieldCached(column),
                "Lazy field ${column.name} should not be in lazyFieldCache for entity with ID $id"
            )
        }
    }

    @Test
    fun testLazyFieldNotLoadedInitially() {
        withTables(ArticlesTable) {
            val article = Article.new {
                title = "Test Article"
                summary = "This is a summary"
                content = "This is the full content"
                rawData = ExposedBlob("raw data".toByteArray())
            }
            val articleId = article.id

            entityCache.clear()

            val reloaded = Article.findById(articleId)!!

            assertInCache(articleId, ArticlesTable.id, ArticlesTable.summary, ArticlesTable.title)
            assertNotInCache(articleId, ArticlesTable.content, ArticlesTable.rawData)

            assertEquals("Test Article", reloaded.title)
            assertEquals("This is a summary", reloaded.summary)

            // Lazy fields should trigger separate query when accessed
            assertEquals("This is the full content", reloaded.content)
            assertEquals("raw data", String(reloaded.rawData!!.bytes))
        }
    }

    @Test
    fun testLazyFieldRefresh() {
        withTables(ArticlesTable) {
            val article = Article.new {
                title = "Test"
                summary = "Summary"
                content = "Original Content"
                rawData = null
            }
            val articleId = article.id

            entityCache.clear()

            assertEquals("Original Content", article.content)

            // Modify directly in database
            ArticlesTable.update({ ArticlesTable.id eq articleId }) {
                it[content] = "Updated Content"
            }

            // Entity knows nothing about direct manipulations in table, so old value here
            assertEquals("Original Content", article.content)
            article.assertInLazyCache(ArticlesTable.content)

            // Refresh should clear lazy cache
            article.refresh()

            article.assertNotInLazyCache(ArticlesTable.content)
            assertEquals("Updated Content", article.content)
            article.assertInLazyCache(ArticlesTable.content)
        }
    }

    @Test
    fun testLazyFieldWithNullValue() {
        withTables(ArticlesTable) {
            val article = Article.new {
                title = "Test"
                summary = "Summary"
                content = "Content"
                rawData = null
            }
            val articleId = article.id

            entityCache.clear()

            val reloaded = Article.findById(articleId)!!

            assertInCache(articleId, ArticlesTable.id, ArticlesTable.title, ArticlesTable.summary)
            assertNotInCache(articleId, ArticlesTable.content, ArticlesTable.rawData)
            reloaded.assertNotInLazyCache(ArticlesTable.rawData)

            assertNull(reloaded.rawData)

            assertNotInCache(articleId, ArticlesTable.rawData)
            reloaded.assertInLazyCache(ArticlesTable.rawData)
        }
    }

    @Test
    fun testLazyFieldNewEntity() {
        withTables(ArticlesTable) {
            val article = Article.new {
                title = "Test"
                summary = "Summary"
                content = "Content"
                rawData = ExposedBlob("data".toByteArray())
            }

            entityCache.clear()

            article.assertNotInLazyCache(ArticlesTable.content, ArticlesTable.rawData)

            assertEquals("Content", article.content)
            assertEquals("data", String(article.rawData!!.bytes))
        }
    }

    @Test
    fun testLazyFieldUpdate() {
        withTables(ArticlesTable) {
            val article = Article.new {
                title = "Test"
                summary = "Summary"
                content = "Original"
                rawData = null
            }
            val articleId = article.id

            entityCache.clear()

            val reloaded = Article.findById(articleId)!!

            assertInCache(articleId, ArticlesTable.id, ArticlesTable.title, ArticlesTable.summary)
            assertNotInCache(articleId, ArticlesTable.content, ArticlesTable.rawData)
            reloaded.assertNotInLazyCache(ArticlesTable.content, ArticlesTable.rawData)

            reloaded.content = "Updated"

            assertEquals("Updated", reloaded.content)

            reloaded.assertNotInLazyCache(ArticlesTable.content)

            assertNotInCache(articleId, ArticlesTable.content)

            entityCache.clear()

            val reloaded2 = Article.findById(articleId)!!

            assertInCache(articleId, ArticlesTable.id, ArticlesTable.title, ArticlesTable.summary)
            assertNotInCache(articleId, ArticlesTable.content)
            reloaded2.assertNotInLazyCache(ArticlesTable.content)

            assertEquals("Updated", reloaded2.content)

            reloaded2.assertInLazyCache(ArticlesTable.content)
        }
    }

    @Test
    fun testMultipleLazyFields() {
        withTables(ArticlesTable) {
            val article = Article.new {
                title = "Test"
                summary = "Summary"
                content = "Content"
                rawData = ExposedBlob("data".toByteArray())
            }
            val articleId = article.id

            entityCache.clear()

            val reloaded = Article.findById(articleId)!!

            assertInCache(articleId, ArticlesTable.id, ArticlesTable.title, ArticlesTable.summary)
            assertNotInCache(articleId, ArticlesTable.content, ArticlesTable.rawData)
            reloaded.assertNotInLazyCache(ArticlesTable.content, ArticlesTable.rawData)

            // Access both lazy fields
            assertEquals("Content", reloaded.content)
            assertEquals("data", String(reloaded.rawData!!.bytes))

            assertNotInCache(articleId, ArticlesTable.content, ArticlesTable.rawData)

            reloaded.assertInLazyCache(ArticlesTable.content, ArticlesTable.rawData)
        }
    }

    @Test
    fun testLazyFieldsExcludedFromQuery() {
        withTables(ArticlesTable) {
            val article1 = Article.new {
                title = "Test1"
                summary = "Summary1"
                content = "Content1"
                rawData = null
            }
            article1.content

            entityCache.clear()

            val article2 = Article.new {
                title = "Test2"
                summary = "Summary2"
                content = "Content2"
                rawData = null
            }
            val article2Id = article2.id

            entityCache.clear()

            val article2Reloaded = Article.findById(article2Id)!!

            assertInCache(article2Id, ArticlesTable.id, ArticlesTable.title, ArticlesTable.summary)
            assertNotInCache(article2Id, ArticlesTable.content, ArticlesTable.rawData)
            article2Reloaded.assertNotInLazyCache(ArticlesTable.content, ArticlesTable.rawData)

            assertEquals("Content2", article2Reloaded.content)

            assertNotInCache(article2Id, ArticlesTable.content)
            article2Reloaded.assertInLazyCache(ArticlesTable.content)
        }
    }

    object ArticlesWithDefaultTable : IntIdTable("articles_with_default") {
        val title = varchar("title", 200)
        val content = text("content").default("DEFAULT_CONTENT")
    }

    class ArticleWithDefault(id: EntityID<Int>) : IntEntity(id) {
        companion object : IntEntityClass<ArticleWithDefault>(ArticlesWithDefaultTable)

        var title by ArticlesWithDefaultTable.title
        var content by ArticlesWithDefaultTable.content.lazy()
    }

    @Test
    fun testLazyFieldWithDatabaseDefault() {
        withTables(ArticlesWithDefaultTable) {
            // Create article without setting lazy field (should use DB default)
            val article = ArticleWithDefault.new {
                title = "Test Article"
            }
            val articleId = article.id

            entityCache.clear()

            val reloaded = ArticleWithDefault.findById(articleId)!!

            // Verify lazy field not in cache initially
            assertInCache(articleId, ArticlesWithDefaultTable.id, ArticlesWithDefaultTable.title)
            assertNotInCache(articleId, ArticlesWithDefaultTable.content)
            reloaded.assertNotInLazyCache(ArticlesWithDefaultTable.content)

            // Access lazy field - should load default value from DB
            assertEquals("DEFAULT_CONTENT", reloaded.content)

            // After accessing, should be cached
            reloaded.assertInLazyCache(ArticlesWithDefaultTable.content)
        }
    }

    @Test
    fun testLazyFieldSetFlushThenAccess() {
        withTables(ArticlesTable) {
            val article = Article.new {
                title = "Test"
                summary = "Summary"
                content = "Original Content"
                rawData = null
            }
            val articleId = article.id

            entityCache.clear()

            val reloaded = Article.findById(articleId)!!

            reloaded.content = "Updated Content"

            reloaded.assertNotInLazyCache(ArticlesTable.content)

            reloaded.flush()

            reloaded.assertNotInLazyCache(ArticlesTable.content)

            entityCache.clear()
            val reloaded2 = Article.findById(articleId)!!

            // Should load updated value without extra DB query for the lazy field
            assertEquals("Updated Content", reloaded2.content)
        }
    }
}
