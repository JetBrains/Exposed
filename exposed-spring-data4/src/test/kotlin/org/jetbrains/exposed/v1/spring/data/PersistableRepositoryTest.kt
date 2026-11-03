package org.jetbrains.exposed.v1.spring.data

import org.jetbrains.exposed.v1.core.dao.id.java.UUIDTable
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.spring7.transaction.SpringTransactionManager
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertNotNull
import org.junit.jupiter.api.assertNull
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.FilterType
import org.springframework.dao.IncorrectUpdateSemanticsDataAccessException
import org.springframework.data.domain.Persistable
import org.springframework.data.repository.findByIdOrNull
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.annotation.EnableTransactionManagement
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate
import java.util.*
import javax.sql.DataSource

/**
 * Entities that implement [Persistable] decide themselves whether they are new, which is needed for ids that are
 * assigned by the application, like the UUIDs of [Articles]. A non-null id must not mean "update" for those.
 *
 * Runs in its own context with a nested repository, so that the shared [SpringDataExposedTestConfiguration] doesn't
 * pick it up and the other test classes are not affected while `Persistable` isn't supported.
 */
@SpringJUnitConfig(PersistableTestConfiguration::class)
@Transactional
open class PersistableRepositoryTest {

    @Autowired
    lateinit var tm: PlatformTransactionManager

    @Autowired
    lateinit var repository: ArticleRepository

    @BeforeEach
    fun createTables() = TransactionTemplate(tm).executeWithoutResult { SchemaUtils.create(Articles) }

    @AfterEach
    fun dropTables() = TransactionTemplate(tm).executeWithoutResult { SchemaUtils.drop(Articles) }

    // ---- insert ----

    @Test
    fun testSaveNewEntityKeepsAssignedUuid() {
        val id = UUID.randomUUID()
        val saved = repository.save(Article.create("Exposed", id = id))

        assertEquals(id, saved.getId())
        assertEquals(1L, repository.count())
        val found = repository.findByIdOrNull(id)
        assertNotNull(found)
        assertEquals("Exposed", found.title)
    }

    @Test
    fun testSaveNewEntityReturnsEntityThatIsNoLongerNew() {
        val saved = repository.save(Article.create("Exposed"))
        assertFalse(saved.isNew)
    }

    @Test
    fun testEntityLoadedFromDatabaseIsNotNew() {
        val id = repository.save(Article.create("Exposed")).getId()!!
        assertFalse(repository.findById(id).get().isNew)
        assertTrue(repository.findAll().none { it.isNew })
    }

    @Test
    fun testSaveNewEntityWithoutIdGeneratesUuid() {
        val saved = repository.save(Article(id = null, title = "Exposed", newEntity = true))

        val id = saved.getId()
        assertNotNull(id)
        assertEquals("Exposed", repository.findByIdOrNull(id)?.title)
    }

    @Test
    fun testSaveNewEntityWithExistingIdFails() {
        val id = repository.save(Article.create("First")).getId()!!
        assertThrows<Exception> { repository.save(Article.create("Second", id = id)) }
    }

    // ---- update ----

    @Test
    fun testSaveEntityThatIsNotNewUpdatesRow() {
        val saved = repository.save(Article.create("Draft"))

        val updated = repository.save(saved.copy(title = "Published", views = 10))

        assertEquals(saved.getId(), updated.getId())
        assertEquals("Published", updated.title)
        assertEquals(1L, repository.count())
        val found = repository.findByIdOrNull(saved.getId()!!)
        assertNotNull(found)
        assertEquals("Published", found.title)
        assertEquals(10, found.views)
    }

    @Test
    fun testSaveEntityThatIsNotNewButMissingFails() {
        assertThrows<IncorrectUpdateSemanticsDataAccessException> {
            repository.save(Article(id = UUID.randomUUID(), title = "Ghost", newEntity = false))
        }
        assertEquals(0L, repository.count())
    }

    @Test
    fun testSaveAllWithNewAndExistingEntities() {
        val existing = repository.save(Article.create("Existing"))
        val newId = UUID.randomUUID()

        val saved = repository.saveAll(listOf(existing.copy(views = 5), Article.create("New", id = newId)))

        assertEquals(listOf(existing.getId(), newId), saved.map { it.getId() })
        assertEquals(2L, repository.count())
        assertEquals(5, repository.findByIdOrNull(existing.getId()!!)?.views)
        assertEquals("New", repository.findByIdOrNull(newId)?.title)
    }

    // ---- reads and deletes by assigned id ----

    @Test
    fun testFindByIdReturnsNullForUnknownUuid() {
        repository.save(Article.create("Exposed"))
        assertNull(repository.findByIdOrNull(UUID.randomUUID()))
    }

    @Test
    fun testFindAllByIdAndExistsById() {
        val a = repository.save(Article.create("A")).getId()!!
        val b = repository.save(Article.create("B")).getId()!!
        repository.save(Article.create("C"))

        assertEquals(listOf("A", "B"), repository.findAllById(listOf(a, b, UUID.randomUUID())).map { it.title }.sorted())
        assertTrue(repository.existsById(a))
        assertFalse(repository.existsById(UUID.randomUUID()))
    }

    @Test
    fun testDeleteEntityUsesPersistableId() {
        val a = repository.save(Article.create("A"))
        val b = repository.save(Article.create("B"))

        repository.delete(a)

        assertFalse(repository.existsById(a.getId()!!))
        assertTrue(repository.existsById(b.getId()!!))
    }

    @Test
    fun testDeleteByIdAndDeleteAllEntities() {
        val a = repository.save(Article.create("A"))
        val b = repository.save(Article.create("B"))
        val c = repository.save(Article.create("C"))

        repository.deleteById(a.getId()!!)
        repository.deleteAll(listOf(b))

        assertEquals(listOf(c.getId()), repository.findAll().map { it.getId() })
    }

    // ---- derived queries ----

    @Test
    fun testDerivedQueryMapsPersistableEntity() {
        val id = UUID.randomUUID()
        repository.save(Article.create("Exposed", id = id, views = 3))
        repository.save(Article.create("Spring"))

        val found = repository.findByTitle("Exposed")
        assertNotNull(found)
        assertEquals(id, found.getId())
        assertEquals(3, found.views)
        assertFalse(found.isNew)
        assertNull(repository.findByTitle("Unknown"))
    }

    @Test
    fun testDerivedQueryById() {
        val id = repository.save(Article.create("Exposed")).getId()!!
        repository.save(Article.create("Spring"))

        assertEquals(listOf("Exposed"), repository.findByIdIn(listOf(id)).map { it.title })
        assertEquals(1L, repository.countByIdNot(id))
    }

    @Test
    fun testTwoNewEntitiesGetDifferentIds() {
        val a = repository.save(Article(id = null, title = "A", newEntity = true))
        val b = repository.save(Article(id = null, title = "B", newEntity = true))
        assertNotEquals(a.getId(), b.getId())
    }

    @ExposedRepository(table = Articles::class)
    interface ArticleRepository : ExposedCrudRepository<Article, UUID> {
        fun findByTitle(title: String): Article?
        fun findByIdIn(ids: Collection<UUID>): List<Article>
        fun countByIdNot(id: UUID): Long
    }
}

@Configuration
@EnableTransactionManagement
@EnableExposedRepositories(
    considerNestedRepositories = true,
    includeFilters = [
        ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = [PersistableRepositoryTest.ArticleRepository::class])
    ],
)
open class PersistableTestConfiguration {
    @Bean
    open fun dataSource(): DataSource = EmbeddedDatabaseBuilder()
        .setType(EmbeddedDatabaseType.H2)
        .generateUniqueName(true)
        .build()

    @Bean
    open fun transactionManager(dataSource: DataSource): PlatformTransactionManager =
        SpringTransactionManager(dataSource)
}

object Articles : UUIDTable("articles") {
    val title = varchar("title", 255)
    val views = integer("views")
}

/**
 * A [Persistable] with an id assigned by the application.
 *
 * The id is private, because a public `val id` would clash with [Persistable.getId] on the JVM.
 * [newEntity] has no column: it is `true` for entities created with [create] and `false` for entities read from the table.
 */
data class Article(
    private val id: UUID?,
    val title: String,
    val views: Int = 0,
    private val newEntity: Boolean = false,
) : Persistable<UUID> {
    override fun getId(): UUID? = id

    override fun isNew(): Boolean = newEntity

    companion object {
        fun create(title: String, id: UUID = UUID.randomUUID(), views: Int = 0) =
            Article(id = id, title = title, views = views, newEntity = true)
    }
}
