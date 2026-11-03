package org.jetbrains.exposed.v1.spring.data

import org.jetbrains.exposed.v1.core.dao.id.LongIdTable
import org.jetbrains.exposed.v1.core.dao.id.java.UUIDTable
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.spring7.transaction.SpringTransactionManager
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertNotNull
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.FilterType
import org.springframework.dao.OptimisticLockingFailureException
import org.springframework.data.annotation.Version
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
 * Optimistic locking with Spring Data's [Version], following the rules of Spring Data JDBC:
 * - an entity is new when its version is `null` (wrapper type) or `0` (primitive type), regardless of its id,
 * - an insert stores the initial version: `0` for wrapper types, `1` for primitive types,
 * - an update only matches the row with the same id **and** version, and increments the version,
 * - an update or `delete(entity)` that matches no row throws [OptimisticLockingFailureException],
 * - `deleteById` ignores the version.
 *
 * [Documents] uses a generated id and a wrapper version (`Long?`), [Notes] an assigned UUID and a primitive version (`Int`).
 *
 * Runs in its own context with nested repositories, so the shared [SpringDataExposedTestConfiguration] doesn't pick them up.
 */
@SpringJUnitConfig(VersionedTestConfiguration::class)
@Transactional
open class VersionedRepositoryTest {

    @Autowired
    lateinit var tm: PlatformTransactionManager

    @Autowired
    lateinit var documents: DocumentRepository

    @Autowired
    lateinit var notes: NoteRepository

    @BeforeEach
    fun createTables() = TransactionTemplate(tm).executeWithoutResult { SchemaUtils.create(Documents, Notes) }

    @AfterEach
    fun dropTables() = TransactionTemplate(tm).executeWithoutResult { SchemaUtils.drop(Documents, Notes) }

    // ---- insert (wrapper version) ----

    @Test
    fun testInsertSetsInitialVersionToZero() {
        val saved = documents.save(Document(title = "Draft"))

        assertNotNull(saved.id)
        assertEquals(0L, saved.version)
        assertEquals(0L, documents.findByIdOrNull(saved.id!!)?.version)
    }

    @Test
    fun testVersionIsReadByAllReadPaths() {
        val saved = documents.save(Document(title = "Draft"))
        documents.save(saved.copy(title = "Published"))

        assertEquals(1L, documents.findById(saved.id!!).get().version)
        assertEquals(listOf(1L), documents.findAll().map { it.version })
        assertEquals(1L, documents.findByTitle("Published")?.version)
    }

    // ---- update (wrapper version) ----

    @Test
    fun testUpdateIncrementsVersion() {
        val v0 = documents.save(Document(title = "v0"))
        val v1 = documents.save(v0.copy(title = "v1"))
        val v2 = documents.save(v1.copy(title = "v2"))

        assertEquals(v0.id, v2.id)
        assertEquals(1L, v1.version)
        assertEquals(2L, v2.version)
        assertEquals(1L, documents.count())
        val found = documents.findByIdOrNull(v0.id!!)
        assertNotNull(found)
        assertEquals("v2", found.title)
        assertEquals(2L, found.version)
    }

    @Test
    fun testSaveDoesNotModifyThePassedEntity() {
        val saved = documents.save(Document(title = "Draft"))
        val toUpdate = saved.copy(title = "Published")

        documents.save(toUpdate)

        assertEquals(0L, toUpdate.version)
    }

    @Test
    fun testUpdateWithStaleVersionFails() {
        val saved = documents.save(Document(title = "Draft"))
        val first = documents.findByIdOrNull(saved.id!!)!!
        val second = documents.findByIdOrNull(saved.id!!)!!

        documents.save(first.copy(title = "First wins"))

        assertThrows<OptimisticLockingFailureException> { documents.save(second.copy(title = "Second loses")) }
        val found = documents.findByIdOrNull(saved.id!!)
        assertNotNull(found)
        assertEquals("First wins", found.title)
        assertEquals(1L, found.version)
    }

    @Test
    fun testUpdateWithVersionAheadOfDatabaseFails() {
        val saved = documents.save(Document(title = "Draft"))

        assertThrows<OptimisticLockingFailureException> { documents.save(saved.copy(title = "Ahead", version = 5L)) }
        assertEquals(0L, documents.findByIdOrNull(saved.id!!)?.version)
    }

    @Test
    fun testUpdateOfMissingRowFailsWithOptimisticLocking() {
        assertThrows<OptimisticLockingFailureException> {
            documents.save(Document(id = 4711L, title = "Ghost", version = 0L))
        }
        assertEquals(0L, documents.count())
    }

    @Test
    fun testEntityWithIdButNullVersionIsNew() {
        val saved = documents.save(Document(title = "Draft"))

        // a null version means "new", even with an id: this is an insert, and the id already exists
        assertThrows<Exception> { documents.save(saved.copy(title = "Duplicate", version = null)) }
    }

    @Test
    fun testSaveAllIncrementsEachVersion() {
        val a = documents.save(Document(title = "A"))
        val b = documents.save(documents.save(Document(title = "B")).copy(title = "B1"))

        val saved = documents.saveAll(listOf(a.copy(title = "A1"), b.copy(title = "B2"), Document(title = "C")))

        assertEquals(listOf(1L, 2L, 0L), saved.map { it.version })
        assertEquals(3L, documents.count())
    }

    // ---- delete (wrapper version) ----

    @Test
    fun testDeleteEntityWithCurrentVersion() {
        val saved = documents.save(documents.save(Document(title = "Draft")).copy(title = "Published"))

        documents.delete(saved)

        assertFalse(documents.existsById(saved.id!!))
    }

    @Test
    fun testDeleteEntityWithStaleVersionFails() {
        val stale = documents.save(Document(title = "Draft"))
        documents.save(stale.copy(title = "Published"))

        assertThrows<OptimisticLockingFailureException> { documents.delete(stale) }
        assertTrue(documents.existsById(stale.id!!))
    }

    @Test
    fun testDeleteAllEntitiesWithStaleVersionFails() {
        val a = documents.save(Document(title = "A"))
        val staleB = documents.save(Document(title = "B"))
        documents.save(staleB.copy(title = "B1"))

        assertThrows<OptimisticLockingFailureException> { documents.deleteAll(listOf(a, staleB)) }
        assertTrue(documents.existsById(staleB.id!!))
    }

    @Test
    fun testDeleteByIdIgnoresVersion() {
        val saved = documents.save(documents.save(Document(title = "Draft")).copy(title = "Published"))

        documents.deleteById(saved.id!!)

        assertEquals(0L, documents.count())
    }

    // ---- primitive version with an assigned id ----

    @Test
    fun testPrimitiveVersionZeroMeansNewAndInsertStartsAtOne() {
        val id = UUID.randomUUID()

        // no Persistable needed: the version tells that an entity with an assigned id is new
        val saved = notes.save(Note(id = id, text = "Hello"))

        assertEquals(id, saved.id)
        assertEquals(1, saved.version)
        assertEquals(1, notes.findByIdOrNull(id)?.version)
    }

    @Test
    fun testPrimitiveVersionUpdateIncrements() {
        val saved = notes.save(Note(id = UUID.randomUUID(), text = "Hello"))

        val updated = notes.save(saved.copy(text = "Hello again"))

        assertEquals(2, updated.version)
        assertEquals("Hello again", notes.findByIdOrNull(saved.id)?.text)
        assertEquals(1L, notes.count())
    }

    @Test
    fun testPrimitiveVersionStaleUpdateFails() {
        val stale = notes.save(Note(id = UUID.randomUUID(), text = "Hello"))
        notes.save(stale.copy(text = "First"))

        assertThrows<OptimisticLockingFailureException> { notes.save(stale.copy(text = "Second")) }
        assertEquals("First", notes.findByIdOrNull(stale.id)?.text)
    }

    @Test
    fun testPrimitiveVersionNewEntityWithExistingIdFails() {
        val saved = notes.save(Note(id = UUID.randomUUID(), text = "Hello"))

        assertThrows<Exception> { notes.save(Note(id = saved.id, text = "Duplicate")) }
        assertEquals("Hello", notes.findByIdOrNull(saved.id)?.text)
    }

    @ExposedRepository(table = Documents::class)
    interface DocumentRepository : ExposedCrudRepository<Document, Long> {
        fun findByTitle(title: String): Document?
    }

    @ExposedRepository(table = Notes::class)
    interface NoteRepository : ExposedCrudRepository<Note, UUID>
}

@Configuration
@EnableTransactionManagement
@EnableExposedRepositories(
    considerNestedRepositories = true,
    includeFilters = [
        ComponentScan.Filter(
            type = FilterType.ASSIGNABLE_TYPE,
            classes = [VersionedRepositoryTest.DocumentRepository::class, VersionedRepositoryTest.NoteRepository::class],
        )
    ],
)
open class VersionedTestConfiguration {
    @Bean
    open fun dataSource(): DataSource = EmbeddedDatabaseBuilder()
        .setType(EmbeddedDatabaseType.H2)
        .generateUniqueName(true)
        .build()

    @Bean
    open fun transactionManager(dataSource: DataSource): PlatformTransactionManager =
        SpringTransactionManager(dataSource)
}

object Documents : LongIdTable("documents") {
    val title = varchar("title", 255)
    val version = long("version")
}

/** Generated id, wrapper version: `null` means new. `@Version` lands on the backing field (its Java target). */
data class Document(
    val id: Long? = null,
    val title: String,
    @Version val version: Long? = null,
)

object Notes : UUIDTable("notes") {
    val text = varchar("text", 255)
    val version = integer("version")
}

/** Assigned id, primitive version: `0` means new. */
data class Note(
    val id: UUID,
    val text: String,
    @Version val version: Int = 0,
)
