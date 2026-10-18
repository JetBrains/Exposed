package org.jetbrains.exposed.samples.springdata.nativeimage

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.ApplicationContext
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.data.repository.findByIdOrNull
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.time.Instant
import java.util.UUID
import kotlin.test.assertNotNull

/**
 * Runs on the JVM with `./gradlew test` (regular context) and in a native image with `./gradlew nativeTest`, where the
 * context is started from the bean definitions and runtime hints generated ahead of time by `processTestAot`.
 */
@SpringBootTest
class NativeSampleApplicationTests @Autowired constructor(
    private val context: ApplicationContext,
    private val customers: CustomerRepository,
    private val notes: NoteRepository,
    private val articles: ArticleRepository,
) {
    private val transactions = TransactionTemplate(context.getBean(PlatformTransactionManager::class.java))
    private val auditor = context.getBean(SampleAuditor::class.java)
    private val clock = context.getBean(SampleClock::class.java)
    private val callback = context.getBean(EmailNormalizingCallback::class.java)

    private fun <T> inTransaction(block: () -> T): T = transactions.execute { block() }

    @BeforeEach
    fun reset() {
        inTransaction {
            customers.deleteAll()
            notes.deleteAll()
            articles.deleteAll()
        }
        auditor.current = ALICE
        clock.now = T1
        callback.invocations.set(0)
    }

    @Test
    fun testRepositoriesComeFromTheExposedFactoryBean() {
        for (type in listOf(CustomerRepository::class.java, NoteRepository::class.java, ArticleRepository::class.java)) {
            val name = context.getBeanNamesForType(type).single()
            // the factory bean class is internal to exposed-spring-data
            assertEquals("ExposedRepositoryFactoryBean", context.getBean("&$name").javaClass.simpleName, "factory bean of $name")
        }
    }

    @Test
    fun testSaveAssignsIdAndFindByIdReturnsTheEntity() {
        val saved = inTransaction { customers.save(Customer(name = "Ann", email = "ann@x.io", age = 25)) }

        val id = assertNotNull(saved.id)
        assertEquals(saved, inTransaction { customers.findByIdOrNull(id) })
        assertEquals(1L, inTransaction { customers.count() })

        inTransaction { customers.deleteById(id) }
        assertNull(inTransaction { customers.findByIdOrNull(id) })
    }

    @Test
    fun testDerivedQuery() {
        seed()

        assertEquals("Bob", inTransaction { customers.findByEmail("bob@x.io") }?.name)
        assertNull(inTransaction { customers.findByEmail("nobody@x.io") })
    }

    @Test
    fun testPagedDerivedQuery() {
        seed()

        val page = inTransaction { customers.findByAgeGreaterThan(20, PageRequest.of(1, 2, Sort.by("age"))) }

        assertEquals(listOf("Carla", "Dan"), page.content.map { it.name })
        assertEquals(4L, page.totalElements)
        assertEquals(2, page.totalPages)
    }

    @Test
    fun testDtoProjection() {
        seed()

        assertEquals(listOf(CustomerName("Carla"), CustomerName("Dan")), inTransaction { customers.findByAgeGreaterThanOrderByAgeAsc(30) })
    }

    @Test
    fun testInterfaceProjection() {
        seed()

        val view = assertNotNull(inTransaction { customers.findViewByEmail("ann@x.io") })
        assertEquals("Ann", view.name)
        assertEquals(25, view.age)
    }

    @Test
    fun testDynamicProjectionRegisteredWithRegisterReflectionForBinding() {
        seed()

        assertEquals(CustomerAge(35), inTransaction { customers.findByEmail("carla@x.io", CustomerAge::class.java) })
    }

    @Test
    fun testQueryReturningEntities() {
        seed()

        assertEquals(listOf("Carla", "Dan"), inTransaction { customers.olderThan(30) }.map { it.name })
    }

    @Test
    fun testQueryReturningDto() {
        seed()

        assertEquals(listOf("Ann", "Bob", "Carla", "Dan").map(::CustomerName), inTransaction { customers.namesByAge() })
    }

    @Test
    fun testPersistableEntityWithPrivateId() {
        val id = UUID.randomUUID()

        val saved = inTransaction { articles.save(Article.create("Exposed", id)) }

        assertEquals(id, saved.getId())
        assertFalse(saved.isNew)
        val found = assertNotNull(inTransaction { articles.findByTitle("Exposed") })
        assertEquals(id, found.getId())
        assertFalse(found.isNew)
    }

    @Test
    fun testAuditingUsesTheNamedAuditorAndClock() {
        val inserted = inTransaction { notes.save(Note(text = "Hello")) }

        assertEquals(T1, inserted.createdAt)
        assertEquals(ALICE, inserted.createdBy)
        assertEquals(T1, inserted.modifiedAt)
        assertEquals(ALICE, inserted.modifiedBy)

        clock.now = T2
        auditor.current = BOB
        inTransaction { notes.save(inserted.copy(text = "Hello again")) }

        val found = assertNotNull(inTransaction { notes.findByIdOrNull(inserted.id!!) })
        assertEquals("Hello again", found.text)
        assertEquals(T1, found.createdAt)
        assertEquals(ALICE, found.createdBy)
        assertEquals(T2, found.modifiedAt)
        assertEquals(BOB, found.modifiedBy)
    }

    @Test
    fun testEntityCallbackIsInvoked() {
        val saved = inTransaction { customers.save(Customer(name = "Eve", email = "EVE@X.IO", age = 50)) }

        assertEquals("eve@x.io", saved.email)
        assertEquals("eve@x.io", inTransaction { customers.findByIdOrNull(saved.id!!) }?.email)
        assertEquals(1, callback.invocations.get())
    }

    private fun seed() {
        inTransaction {
            customers.saveAll(
                listOf(
                    Customer(name = "Ann", email = "ann@x.io", age = 25),
                    Customer(name = "Bob", email = "bob@x.io", age = 30),
                    Customer(name = "Carla", email = "carla@x.io", age = 35),
                    Customer(name = "Dan", email = "dan@x.io", age = 40),
                )
            )
        }
    }

    companion object {
        val T1: Instant = Instant.parse("2025-03-01T10:15:30.123Z")
        val T2: Instant = Instant.parse("2025-03-02T11:16:31.456Z")
        const val ALICE = "alice"
        const val BOB = "bob"
    }
}
