package org.jetbrains.exposed.v1.spring.data

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.dao.id.LongIdTable
import org.jetbrains.exposed.v1.core.dao.id.java.UUIDTable
import org.jetbrains.exposed.v1.core.greater
import org.jetbrains.exposed.v1.javatime.timestamp
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.spring.data.auditing.EnableExposedAuditing
import org.jetbrains.exposed.v1.spring.data.event.BeforeSaveCallback
import org.jetbrains.exposed.v1.spring.data.mapping.ExposedMappingContext
import org.jetbrains.exposed.v1.spring.data.query.query
import org.jetbrains.exposed.v1.spring.data.repository.ExposedRepositoryFactoryBean
import org.jetbrains.exposed.v1.spring7.transaction.SpringTransactionManager
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.aot.AotDetector
import org.springframework.aot.generate.GeneratedFiles
import org.springframework.aot.test.generate.TestGenerationContext
import org.springframework.beans.factory.DisposableBean
import org.springframework.beans.factory.support.RootBeanDefinition
import org.springframework.context.ApplicationContextInitializer
import org.springframework.context.annotation.AnnotationConfigUtils
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.ConfigurationClassPostProcessor
import org.springframework.context.annotation.FilterType
import org.springframework.context.aot.ApplicationContextAotGenerator
import org.springframework.context.support.GenericApplicationContext
import org.springframework.core.SpringProperties
import org.springframework.core.test.tools.TestCompiler
import org.springframework.data.annotation.CreatedBy
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.annotation.LastModifiedBy
import org.springframework.data.annotation.LastModifiedDate
import org.springframework.data.auditing.DateTimeProvider
import org.springframework.data.domain.AuditorAware
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Persistable
import org.springframework.data.domain.Sort
import org.springframework.data.repository.findByIdOrNull
import org.springframework.jdbc.datasource.DelegatingDataSource
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.annotation.EnableTransactionManagement
import org.springframework.transaction.support.TransactionTemplate
import java.sql.Connection
import java.time.Instant
import java.time.temporal.TemporalAccessor
import java.util.*
import java.util.concurrent.atomic.AtomicInteger
import javax.sql.DataSource
import kotlin.test.assertNotNull

/**
 * Processes a context with [EnableExposedRepositories] and [EnableExposedAuditing] ahead of time, like Spring Boot's
 * `processAot` does, compiles the generated code and starts a fresh context from the generated
 * [ApplicationContextInitializer] only (no configuration class parsing at runtime). The repositories of that context
 * must behave like the ones of a normally started context: CRUD, derived and paged queries, DTO and interface
 * projections, `query { }` default methods, a [Persistable] entity, auditing with named beans and an entity callback.
 *
 * The AOT processing itself must not open a database connection; [AotTestConfiguration] counts the data sources and
 * connections it creates.
 *
 * The context is generated, compiled and started once for all tests; the tables are created before and dropped after
 * every test.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AotGeneratedContextTest {

    private lateinit var context: GenericApplicationContext
    private lateinit var transactions: TransactionTemplate

    private var dataSourcesCreatedDuringAot = -1
    private var connectionsOpenedDuringAot = -1
    private var singletonsCreatedDuringAot: List<String> = emptyList()
    private var generatedSources: Map<String, String> = emptyMap()
    private var aotModeDuringRefresh = false

    private lateinit var customers: AotCustomerRepository
    private lateinit var notes: AotNoteRepository
    private lateinit var articles: AotArticleRepository

    @BeforeAll
    fun startGeneratedContext() {
        AotTestConfiguration.reset()
        val generationContext = TestGenerationContext()
        val initializerClassName = GenericApplicationContext().use { aotContext ->
            AnnotationConfigUtils.registerAnnotationConfigProcessors(aotContext)
            aotContext.registerBeanDefinition("aotTestConfiguration", RootBeanDefinition(AotTestConfiguration::class.java))
            val className = ApplicationContextAotGenerator().processAheadOfTime(aotContext, generationContext)
            singletonsCreatedDuringAot = aotContext.beanFactory.singletonNames.toList()
            className
        }
        dataSourcesCreatedDuringAot = AotTestConfiguration.dataSourcesCreated.get()
        connectionsOpenedDuringAot = AotTestConfiguration.connectionsOpened.get()
        generationContext.writeGeneratedContent()
        generatedSources = generationContext.generatedFiles.getGeneratedFiles(GeneratedFiles.Kind.SOURCE)
            .mapValues { (_, source) -> source.inputStream.use { String(it.readAllBytes()) } }

        // like `spring.aot.enabled=true` at runtime; only set while the generated context starts, so other test classes
        // in the same JVM are not affected, and restored even if compiling or refreshing fails
        val previousAotEnabled = SpringProperties.getProperty(AOT_ENABLED)
        SpringProperties.setProperty(AOT_ENABLED, "true")
        try {
            TestCompiler.forSystem().with(generationContext).compile { compiled ->
                @Suppress("UNCHECKED_CAST")
                val initializer = compiled.getInstance(ApplicationContextInitializer::class.java, initializerClassName.reflectionName())
                    as ApplicationContextInitializer<GenericApplicationContext>
                val freshContext = GenericApplicationContext()
                freshContext.classLoader = compiled.classLoader
                initializer.initialize(freshContext)
                freshContext.refresh()
                aotModeDuringRefresh = AotDetector.useGeneratedArtifacts()
                context = freshContext
            }
        } finally {
            SpringProperties.setProperty(AOT_ENABLED, previousAotEnabled)
        }

        transactions = TransactionTemplate(context.getBean(PlatformTransactionManager::class.java))
        customers = context.getBean(AotCustomerRepository::class.java)
        notes = context.getBean(AotNoteRepository::class.java)
        articles = context.getBean(AotArticleRepository::class.java)
    }

    @AfterAll
    fun closeGeneratedContext() {
        if (::context.isInitialized) context.close()
    }

    @BeforeEach
    fun createTables() {
        transactions.executeWithoutResult { SchemaUtils.create(AotCustomers, AotNotes, AotArticles) }
        context.getBean(AotAuditor::class.java).current = ALICE
        context.getBean(AotClock::class.java).now = T1
        context.getBean(AotEmailNormalizingCallback::class.java).invocations.set(0)
    }

    @AfterEach
    fun dropTables() = transactions.executeWithoutResult { SchemaUtils.drop(AotCustomers, AotNotes, AotArticles) }

    @Suppress("UNCHECKED_CAST")
    private fun <T> inTransaction(block: () -> T): T = transactions.execute { block() } as T

    // ---- the AOT processing and the generated context ----

    @Test
    fun testAotProcessingNeedsNoDatabaseConnection() {
        assertEquals(0, dataSourcesCreatedDuringAot, "data sources created during AOT processing")
        assertEquals(0, connectionsOpenedDuringAot, "connections opened during AOT processing")
        assertFalse("dataSource" in singletonsCreatedDuringAot, "dataSource singleton created during AOT processing")
    }

    @Test
    fun testBeanDefinitionsComeFromTheGeneratedCode() {
        val generatedCode = generatedSources.values.joinToString("\n")
        assertTrue("ExposedRepositoryFactoryBean" in generatedCode, "generated repository bean definitions")
        assertTrue("exposedMappingContext" in generatedCode, "generated mapping context bean definition")
        assertTrue("exposedAuditingHandler" in generatedCode, "generated auditing handler bean definition")
        // the configuration classes are not parsed again at runtime
        assertTrue(context.getBeanNamesForType(ConfigurationClassPostProcessor::class.java).isEmpty())
    }

    @Test
    fun testGeneratedContextRunsWithAotEnabled() {
        assertTrue(aotModeDuringRefresh, "AOT mode enabled while the generated context was refreshed")
        // Not asserted: that the pre-generated CGLIB configuration class is loaded. TestCompiler's class loader is isolated
        // from the configuration class's loader, so CGLIB may regenerate it at runtime; the native sample validates that.
    }

    @Test
    fun testGeneratedContextHasRepositoriesFromTheExposedFactoryBean() {
        for (type in listOf(AotCustomerRepository::class.java, AotNoteRepository::class.java, AotArticleRepository::class.java)) {
            val name = context.getBeanNamesForType(type).single()
            assertTrue(context.getBean("&$name") is ExposedRepositoryFactoryBean<*, *, *>, "factory bean of $name")
        }
        assertEquals(listOf("exposedMappingContext"), context.getBeanNamesForType(ExposedMappingContext::class.java).toList())
        assertEquals(1, AotTestConfiguration.dataSourcesCreated.get())
    }

    // ---- CRUD ----

    @Test
    fun testSaveAssignsIdAndFindByIdReturnsTheEntity() {
        val saved = inTransaction { customers.save(AotCustomer(name = "Ann", email = "ann@x.io", age = 25)) }

        val id = assertNotNull(saved.id)
        assertEquals(saved, inTransaction { customers.findByIdOrNull(id) })
        assertEquals(1L, inTransaction { customers.count() })

        inTransaction { customers.deleteById(id) }
        assertNull(inTransaction { customers.findByIdOrNull(id) })
    }

    // ---- derived queries ----

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

    // ---- projections ----

    @Test
    fun testDtoProjection() {
        seed()

        assertEquals(
            listOf(AotCustomerName("Carla"), AotCustomerName("Dan")),
            inTransaction { customers.findByAgeGreaterThanOrderByAgeAsc(30) },
        )
    }

    @Test
    fun testInterfaceProjection() {
        seed()

        val view = assertNotNull(inTransaction { customers.findViewByEmail("ann@x.io") })
        assertEquals("Ann", view.name)
        assertEquals(25, view.age)
    }

    // ---- query { } ----

    @Test
    fun testQueryReturningEntities() {
        seed()

        assertEquals(listOf("Carla", "Dan"), inTransaction { customers.olderThan(30) }.map { it.name })
    }

    @Test
    fun testQueryReturningDto() {
        seed()

        assertEquals(listOf("Ann", "Bob", "Carla", "Dan").map(::AotCustomerName), inTransaction { customers.namesByAge() })
    }

    // ---- Persistable ----

    @Test
    fun testPersistableEntityWithPrivateId() {
        val id = UUID.randomUUID()

        val saved = inTransaction { articles.save(AotArticle.create("Exposed", id)) }

        assertEquals(id, saved.getId())
        assertFalse(saved.isNew)
        val found = assertNotNull(inTransaction { articles.findByTitle("Exposed") })
        assertEquals(id, found.getId())
        assertFalse(found.isNew)
    }

    // ---- auditing ----

    @Test
    fun testAuditingUsesTheNamedAuditorAndClock() {
        val inserted = inTransaction { notes.save(AotNote(text = "Hello")) }

        assertEquals(T1, inserted.createdAt)
        assertEquals(ALICE, inserted.createdBy)
        assertEquals(T1, inserted.modifiedAt)
        assertEquals(ALICE, inserted.modifiedBy)

        context.getBean(AotClock::class.java).now = T2
        context.getBean(AotAuditor::class.java).current = BOB
        inTransaction { notes.save(inserted.copy(text = "Hello again")) }

        val found = assertNotNull(inTransaction { notes.findByIdOrNull(inserted.id!!) })
        assertEquals("Hello again", found.text)
        assertEquals(T1, found.createdAt)
        assertEquals(ALICE, found.createdBy)
        assertEquals(T2, found.modifiedAt)
        assertEquals(BOB, found.modifiedBy)
    }

    // ---- entity callbacks ----

    @Test
    fun testEntityCallbackIsInvoked() {
        val saved = inTransaction { customers.save(AotCustomer(name = "Eve", email = "EVE@X.IO", age = 50)) }

        assertEquals("eve@x.io", saved.email)
        assertEquals("eve@x.io", inTransaction { customers.findByIdOrNull(saved.id!!) }?.email)
        assertEquals(1, context.getBean(AotEmailNormalizingCallback::class.java).invocations.get())
    }

    private fun seed(): List<AotCustomer> = inTransaction {
        customers.saveAll(
            listOf(
                AotCustomer(name = "Ann", email = "ann@x.io", age = 25),
                AotCustomer(name = "Bob", email = "bob@x.io", age = 30),
                AotCustomer(name = "Carla", email = "carla@x.io", age = 35),
                AotCustomer(name = "Dan", email = "dan@x.io", age = 40),
            )
        ).toList()
    }

    @ExposedRepository(table = AotCustomers::class)
    interface AotCustomerRepository : ExposedCrudRepository<AotCustomer, Long> {
        fun findByEmail(email: String): AotCustomer?

        fun findByAgeGreaterThan(age: Int, pageable: Pageable): Page<AotCustomer>

        fun findByAgeGreaterThanOrderByAgeAsc(age: Int): List<AotCustomerName>

        fun findViewByEmail(email: String): AotCustomerView?

        fun olderThan(age: Int): List<AotCustomer> = query {
            AotCustomers.selectAll().where { AotCustomers.age greater age }.orderBy(AotCustomers.age).toList()
        }

        fun namesByAge(): List<AotCustomerName> = query {
            AotCustomers.select(AotCustomers.name).orderBy(AotCustomers.age).toList()
        }
    }

    @ExposedRepository(table = AotNotes::class)
    interface AotNoteRepository : ExposedCrudRepository<AotNote, Long>

    @ExposedRepository(table = AotArticles::class)
    interface AotArticleRepository : ExposedCrudRepository<AotArticle, UUID> {
        fun findByTitle(title: String): AotArticle?
    }

    companion object {
        val T1: Instant = Instant.parse("2025-03-01T10:15:30.123Z")
        val T2: Instant = Instant.parse("2025-03-02T11:16:31.456Z")
        const val ALICE = "alice"
        const val BOB = "bob"
        const val AOT_ENABLED = "spring.aot.enabled"
    }
}

/**
 * The configuration processed ahead of time. Besides the named auditor and clock there are other [AuditorAware] and
 * [DateTimeProvider] beans, so auditing only works if the bean names survive the code generation.
 */
@Configuration
@EnableTransactionManagement
@EnableExposedRepositories(
    considerNestedRepositories = true,
    includeFilters = [
        ComponentScan.Filter(
            type = FilterType.ASSIGNABLE_TYPE,
            classes = [
                AotGeneratedContextTest.AotCustomerRepository::class,
                AotGeneratedContextTest.AotNoteRepository::class,
                AotGeneratedContextTest.AotArticleRepository::class,
            ],
        )
    ],
)
@EnableExposedAuditing(auditorAwareRef = "aotAuditor", dateTimeProviderRef = "aotClock")
open class AotTestConfiguration {
    @Bean
    open fun dataSource(): DataSource {
        dataSourcesCreated.incrementAndGet()
        val database = EmbeddedDatabaseBuilder()
            .setType(EmbeddedDatabaseType.H2)
            .generateUniqueName(true)
            .build()
        return object : DelegatingDataSource(database), DisposableBean {
            override fun getConnection(): Connection {
                connectionsOpened.incrementAndGet()
                return super.getConnection()
            }

            override fun destroy() {
                database.shutdown()
            }
        }
    }

    @Bean
    open fun transactionManager(dataSource: DataSource): PlatformTransactionManager = SpringTransactionManager(dataSource)

    @Bean
    open fun aotAuditor(): AotAuditor = AotAuditor()

    @Bean
    open fun otherAuditor(): AuditorAware<String> = AuditorAware { Optional.of("eve") }

    @Bean
    open fun aotClock(): AotClock = AotClock()

    @Bean
    open fun otherClock(): DateTimeProvider = DateTimeProvider { Optional.of(Instant.parse("2026-01-01T00:00:00Z")) }

    @Bean
    open fun aotEmailNormalizingCallback(): AotEmailNormalizingCallback = AotEmailNormalizingCallback()

    companion object {
        val dataSourcesCreated = AtomicInteger()
        val connectionsOpened = AtomicInteger()

        fun reset() {
            dataSourcesCreated.set(0)
            connectionsOpened.set(0)
        }
    }
}

class AotAuditor : AuditorAware<String> {
    @Volatile
    var current: String? = AotGeneratedContextTest.ALICE

    override fun getCurrentAuditor(): Optional<String> = Optional.ofNullable(current)
}

class AotClock : DateTimeProvider {
    @Volatile
    var now: Instant = AotGeneratedContextTest.T1

    override fun getNow(): Optional<TemporalAccessor> = Optional.of(now)
}

/** Lower-cases the email of every saved customer and counts its invocations. */
class AotEmailNormalizingCallback : BeforeSaveCallback<AotCustomer> {
    val invocations = AtomicInteger()

    override fun onBeforeSave(entity: AotCustomer, table: Table): AotCustomer {
        invocations.incrementAndGet()
        return entity.copy(email = entity.email.lowercase())
    }
}

object AotCustomers : LongIdTable("aot_customers") {
    val name = varchar("name", 255)
    val email = varchar("email", 255).uniqueIndex()
    val age = integer("age")
}

data class AotCustomer(
    val id: Long? = null,
    val name: String,
    val email: String,
    val age: Int,
)

data class AotCustomerName(val name: String)

interface AotCustomerView {
    val name: String
    val age: Int
}

object AotNotes : LongIdTable("aot_notes") {
    val text = varchar("text", 255)
    val createdAt = timestamp("created_at").nullable()
    val modifiedAt = timestamp("modified_at").nullable()
    val createdBy = varchar("created_by", 64).nullable()
    val modifiedBy = varchar("modified_by", 64).nullable()
}

data class AotNote(
    val id: Long? = null,
    val text: String,
    @CreatedDate val createdAt: Instant? = null,
    @LastModifiedDate val modifiedAt: Instant? = null,
    @CreatedBy val createdBy: String? = null,
    @LastModifiedBy val modifiedBy: String? = null,
)

object AotArticles : UUIDTable("aot_articles") {
    val title = varchar("title", 255)
}

/** A [Persistable] with an application-assigned, private id; see [Article]. */
data class AotArticle(
    private val id: UUID?,
    val title: String,
    private val newEntity: Boolean = false,
) : Persistable<UUID> {
    override fun getId(): UUID? = id

    override fun isNew(): Boolean = newEntity

    companion object {
        fun create(title: String, id: UUID = UUID.randomUUID()) = AotArticle(id = id, title = title, newEntity = true)
    }
}
