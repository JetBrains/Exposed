package org.jetbrains.exposed.v1.spring.data

import org.jetbrains.exposed.v1.core.Expression
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.IdTable
import org.jetbrains.exposed.v1.core.dao.id.LongIdTable
import org.jetbrains.exposed.v1.spring.data.mapping.EntityMetadata
import org.jetbrains.exposed.v1.spring.data.mapping.ExposedMappingContext
import org.jetbrains.exposed.v1.spring.data.mapping.ExposedPersistentEntity
import org.jetbrains.exposed.v1.spring.data.mapping.ProjectionResolver
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.data.annotation.Id
import org.springframework.data.annotation.PersistenceCreator
import org.springframework.data.projection.SpelAwareProxyProjectionFactory
import org.springframework.data.repository.core.support.PersistentEntityInformation
import java.util.UUID

/** The class model from [ExposedMappingContext] and how [EntityMetadata] connects it to the table, without a Spring context. */
class ExposedMappingContextTest {
    private val context = ExposedMappingContext()

    @Suppress("UNCHECKED_CAST")
    private fun <T : Any> entity(type: Class<T>) = context.getRequiredPersistentEntity(type) as ExposedPersistentEntity<T>

    @Suppress("UNCHECKED_CAST")
    private fun <T : Any> metadata(table: IdTable<*>, type: Class<T>) = EntityMetadata(table as IdTable<Any>, entity(type))

    private fun <T : Any> resolver(table: IdTable<*>, type: Class<T>) =
        ProjectionResolver(metadata(table, type), SpelAwareProxyProjectionFactory(), context.instantiators)

    @Test
    fun testPropertyNamedIdIsTheIdProperty() {
        assertEquals("id", entity(Customer::class.java).idProperty?.name)
        assertEquals("id", metadata(Customers, Customer::class.java).idProperty)
    }

    @Test
    fun testIdAnnotationWinsOverPropertyNamedId() {
        assertEquals("key", entity(ExplicitIdItem::class.java).idProperty?.name)
        // the id column of an IdTable is the property `id`, so a different @Id property can't be mapped
        val error = assertThrows<IllegalStateException> { metadata(Items, ExplicitIdItem::class.java) }
        assertTrue(error.message!!.contains("'key'")) { error.message }
    }

    @Test
    fun testValueTypesAreNotDescribedAsEntities() {
        entity(Note::class.java)
        assertFalse(context.hasPersistentEntityFor(UUID::class.java))
    }

    @Test
    fun testIsNewFollowsSpringDataRules() {
        val customers = PersistentEntityInformation<Customer, Long>(entity(Customer::class.java))
        assertTrue(customers.isNew(Customer(name = "Ann", email = "ann@x.io")))
        assertFalse(customers.isNew(Customer(id = 1, name = "Ann", email = "ann@x.io")))

        // the version decides, not the id
        val documents = PersistentEntityInformation<Document, Long>(entity(Document::class.java))
        assertTrue(documents.isNew(Document(id = 1, title = "A")))
        assertFalse(documents.isNew(Document(id = 1, title = "A", version = 0)))

        val notes = PersistentEntityInformation<Note, UUID>(entity(Note::class.java))
        assertTrue(notes.isNew(Note(id = UUID.randomUUID(), text = "A", version = 0)))
        assertFalse(notes.isNew(Note(id = UUID.randomUUID(), text = "A", version = 1)))

        // a Persistable decides itself, and its private id is read through getId()
        val articles = PersistentEntityInformation<Article, UUID>(entity(Article::class.java))
        val article = Article.create("A")
        assertTrue(articles.isNew(article))
        assertFalse(articles.isNew(article.copy(newEntity = false)))
        assertEquals(article.getId(), articles.getId(article))
    }

    @Test
    fun testVersionMetadata() {
        val documents = metadata(Documents, Document::class.java)
        assertEquals("version", documents.versionProperty)
        assertEquals(0L, documents.initialVersion())
        assertEquals(4L, documents.nextVersion(3L))

        val notes = metadata(Notes, Note::class.java)
        assertEquals(1, notes.initialVersion())
        assertEquals(4, notes.nextVersion(3))
    }

    @Test
    fun testWithValueCopiesImmutableEntities() {
        val customers = metadata(Customers, Customer::class.java)
        val ann = Customer(name = "Ann", email = "ann@x.io")

        val renamed = customers.withValue(ann, "name", "Anna")

        assertNotSame(ann, renamed)
        assertEquals("Ann", ann.name)
        assertEquals(ann.copy(name = "Anna"), renamed)
    }

    @Test
    fun testValueOfReadsPrivateProperties() {
        val article = Article.create("A")
        assertEquals(article.getId(), metadata(Articles, Article::class.java).valueOf(article, "id"))
    }

    @Test
    fun testEntityConverterUsesPersistenceCreatorAndPopulatesOtherProperties() {
        val converter = resolver(Items, CreatedItem::class.java).entityConverter
        val row = ResultRow.createAndFillValues(
            mapOf<Expression<*>, Any?>(Items.id to EntityID(7L, Items), Items.name to "Seven", Items.note to "lucky")
        )

        val item = converter.convert(row)

        assertEquals(7L, item.id)
        assertEquals("Seven", item.name)
        assertTrue(item.createdWithAnnotatedConstructor)
        assertEquals("lucky", item.note)
    }

    @Test
    fun testEntityConverterNeedsAColumnForEveryRequiredConstructorParameter() {
        val error = assertThrows<IllegalStateException> { resolver(Items, UnmappedItem::class.java) }
        assertTrue(error.message!!.contains("unmapped")) { error.message }
    }
}

object Items : LongIdTable("items") {
    val name = varchar("name", 255)
    val note = varchar("note", 255).nullable()
}

data class ExplicitIdItem(@Id val key: Long?, val id: Long, val name: String)

class CreatedItem(val id: Long?, val name: String, val createdWithAnnotatedConstructor: Boolean) {
    var note: String? = null

    @PersistenceCreator
    constructor(id: Long?, name: String) : this(id, name, createdWithAnnotatedConstructor = true)
}

data class UnmappedItem(val id: Long?, val name: String, val unmapped: String)
