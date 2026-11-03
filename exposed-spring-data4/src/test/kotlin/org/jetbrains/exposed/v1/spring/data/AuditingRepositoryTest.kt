package org.jetbrains.exposed.v1.spring.data

import org.jetbrains.exposed.v1.core.dao.id.LongIdTable
import org.jetbrains.exposed.v1.javatime.datetime
import org.jetbrains.exposed.v1.javatime.timestamp
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.spring.data.auditing.EnableExposedAuditing
import org.jetbrains.exposed.v1.spring7.transaction.SpringTransactionManager
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertNotNull
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.FilterType
import org.springframework.data.annotation.CreatedBy
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.annotation.LastModifiedBy
import org.springframework.data.annotation.LastModifiedDate
import org.springframework.data.annotation.Version
import org.springframework.data.auditing.DateTimeProvider
import org.springframework.data.domain.AuditorAware
import org.springframework.data.repository.findByIdOrNull
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.annotation.EnableTransactionManagement
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.TemporalAccessor
import java.util.*
import javax.sql.DataSource

/**
 * Auditing with [EnableExposedAuditing], following the rules of Spring Data's `IsNewAwareAuditingHandler`:
 * - on insert, [CreatedDate] and [CreatedBy] are set, and so are [LastModifiedDate] and [LastModifiedBy] unless
 *   `modifyOnCreate = false`,
 * - on update, only [LastModifiedDate] and [LastModifiedBy] change; the stored created values are kept, also when the
 *   passed entity has them `null`,
 * - the auditor comes from the [AuditorAware] bean named by `auditorAwareRef`, the time from the [DateTimeProvider]
 *   named by `dateTimeProviderRef`; `setDates = false` leaves all dates untouched,
 * - `Instant`, `LocalDateTime` and `Long` (epoch millis) date properties are supported,
 * - immutable entities get a new instance with the values, and that instance is what `save` / `saveAll` return,
 * - without [EnableExposedAuditing] nothing is set.
 *
 * Each auditing mode has its own context ([DefaultAuditingConfiguration], [NoModifyOnCreateAuditingConfiguration],
 * [NoDatesAuditingConfiguration], [NoAuditingConfiguration]) with nested repositories, so the shared
 * [SpringDataExposedTestConfiguration] isn't affected. All contexts have the same [TestAuditorAware] and
 * [FixedDateTimeProvider] beans, which the tests move between saves.
 */
class AuditingRepositoryTest {

    @Nested
    @SpringJUnitConfig(DefaultAuditingConfiguration::class)
    @Transactional
    inner class DefaultAuditing : AuditingContextTest() {

        // ---- insert ----

        @Test
        fun testInsertSetsCreatedAndModifiedFields() {
            val saved = documents.save(AuditedDocument(title = "Draft"))

            assertAudited(saved, created = T1 to ALICE, modified = T1 to ALICE)
            assertAudited(documents.findByIdOrNull(saved.id!!), created = T1 to ALICE, modified = T1 to ALICE)
        }

        @Test
        fun testInsertReturnsNewInstanceAndLeavesPassedEntityUnchanged() {
            val toSave = AuditedDocument(title = "Draft")

            val saved = documents.save(toSave)

            assertAudited(toSave, created = null to null, modified = null to null)
            assertAudited(saved, created = T1 to ALICE, modified = T1 to ALICE)
        }

        @Test
        fun testInsertOverwritesValuesSetByTheCaller() {
            val saved = documents.save(
                AuditedDocument(title = "Draft", createdAt = EARLIER, createdBy = "mallory", modifiedAt = EARLIER, modifiedBy = "mallory")
            )

            assertAudited(saved, created = T1 to ALICE, modified = T1 to ALICE)
            assertAudited(documents.findByIdOrNull(saved.id!!), created = T1 to ALICE, modified = T1 to ALICE)
        }

        @Test
        fun testInsertWithoutAuditorSetsOnlyDates() {
            auditor.current = null

            val saved = documents.save(AuditedDocument(title = "Draft"))

            assertAudited(saved, created = T1 to null, modified = T1 to null)
            assertAudited(documents.findByIdOrNull(saved.id!!), created = T1 to null, modified = T1 to null)
        }

        // ---- update ----

        @Test
        fun testUpdateChangesOnlyModifiedFields() {
            val inserted = documents.save(AuditedDocument(title = "Draft"))
            clock.now = T2
            auditor.current = BOB

            val updated = documents.save(inserted.copy(title = "Published"))

            assertEquals(inserted.id, updated.id)
            assertAudited(updated, created = T1 to ALICE, modified = T2 to BOB)
            val found = documents.findByIdOrNull(inserted.id!!)
            assertEquals("Published", found?.title)
            assertAudited(found, created = T1 to ALICE, modified = T2 to BOB)
        }

        @Test
        fun testUpdateDoesNotOverwriteCreatedFieldsWhenPassedEntityHasThemNull() {
            val inserted = documents.save(AuditedDocument(title = "Draft"))
            clock.now = T2
            auditor.current = BOB

            // e.g. an entity built from a request body that doesn't carry the created values
            val updated = documents.save(AuditedDocument(id = inserted.id, title = "Published"))

            assertEquals(T2, updated.modifiedAt)
            assertEquals(BOB, updated.modifiedBy)
            assertAudited(documents.findByIdOrNull(inserted.id!!), created = T1 to ALICE, modified = T2 to BOB)
            assertEquals(1L, documents.count())
        }

        @Test
        fun testRepeatedUpdatesKeepCreatedFields() {
            val inserted = documents.save(AuditedDocument(title = "v0"))
            clock.now = T2
            auditor.current = BOB
            val v1 = documents.save(inserted.copy(title = "v1"))
            clock.now = T3
            auditor.current = CAROL

            val v2 = documents.save(v1.copy(title = "v2"))

            assertAudited(v2, created = T1 to ALICE, modified = T3 to CAROL)
            assertAudited(documents.findByIdOrNull(inserted.id!!), created = T1 to ALICE, modified = T3 to CAROL)
        }

        // ---- saveAll ----

        @Test
        fun testSaveAllAuditsEachEntity() {
            val existing = documents.save(AuditedDocument(title = "Existing"))
            clock.now = T2
            auditor.current = BOB

            val saved = documents.saveAll(listOf(existing.copy(title = "Existing, updated"), AuditedDocument(title = "New")))

            assertEquals(2, saved.size)
            assertAudited(saved[0], created = T1 to ALICE, modified = T2 to BOB)
            assertAudited(saved[1], created = T2 to BOB, modified = T2 to BOB)
            assertAudited(documents.findByIdOrNull(existing.id!!), created = T1 to ALICE, modified = T2 to BOB)
            assertAudited(documents.findByIdOrNull(saved[1].id!!), created = T2 to BOB, modified = T2 to BOB)
        }

        // ---- @Version ----

        @Test
        fun testVersionedInsertSetsVersionAndAuditFields() {
            val saved = notes.save(AuditedNote(text = "Hello"))

            assertEquals(0L, saved.version)
            assertEquals(T1, saved.createdAt)
            assertEquals(ALICE, saved.createdBy)
            assertEquals(T1, saved.modifiedAt)
            val found = notes.findByIdOrNull(saved.id!!)
            assertNotNull(found)
            assertEquals(0L, found.version)
            assertEquals(T1, found.createdAt)
            assertEquals(ALICE, found.createdBy)
            assertEquals(T1, found.modifiedAt)
        }

        @Test
        fun testVersionedUpdateIncrementsVersionAndKeepsCreatedFields() {
            val inserted = notes.save(AuditedNote(text = "Hello"))
            clock.now = T2
            auditor.current = BOB

            val updated = notes.save(inserted.copy(text = "Hello again", createdAt = null, createdBy = null))

            assertEquals(1L, updated.version)
            assertEquals(T2, updated.modifiedAt)
            val found = notes.findByIdOrNull(inserted.id!!)
            assertNotNull(found)
            assertEquals("Hello again", found.text)
            assertEquals(1L, found.version)
            assertEquals(T1, found.createdAt)
            assertEquals(ALICE, found.createdBy)
            assertEquals(T2, found.modifiedAt)
        }

        // ---- date types ----

        @Test
        fun testLocalDateTimeAndLongDatesOnInsert() {
            val saved = events.save(AuditedEvent(name = "Launch"))

            assertEquals(localDateTime(T1), saved.createdAt)
            assertEquals(T1.toEpochMilli(), saved.modifiedAt)
            val found = events.findByIdOrNull(saved.id!!)
            assertEquals(localDateTime(T1), found?.createdAt)
            assertEquals(T1.toEpochMilli(), found?.modifiedAt)
        }

        @Test
        fun testLocalDateTimeAndLongDatesOnUpdate() {
            val inserted = events.save(AuditedEvent(name = "Launch"))
            clock.now = T2

            val updated = events.save(inserted.copy(name = "Relaunch"))

            assertEquals(localDateTime(T1), updated.createdAt)
            assertEquals(T2.toEpochMilli(), updated.modifiedAt)
            val found = events.findByIdOrNull(inserted.id!!)
            assertEquals(localDateTime(T1), found?.createdAt)
            assertEquals(T2.toEpochMilli(), found?.modifiedAt)
        }
    }

    @Nested
    @SpringJUnitConfig(NoModifyOnCreateAuditingConfiguration::class)
    @Transactional
    inner class NoModifyOnCreate : AuditingContextTest() {

        @Test
        fun testInsertSetsOnlyCreatedFields() {
            val saved = documents.save(AuditedDocument(title = "Draft"))

            assertAudited(saved, created = T1 to ALICE, modified = null to null)
            assertAudited(documents.findByIdOrNull(saved.id!!), created = T1 to ALICE, modified = null to null)
        }

        @Test
        fun testUpdateSetsModifiedFields() {
            val inserted = documents.save(AuditedDocument(title = "Draft"))
            clock.now = T2
            auditor.current = BOB

            val updated = documents.save(inserted.copy(title = "Published"))

            assertAudited(updated, created = T1 to ALICE, modified = T2 to BOB)
            assertAudited(documents.findByIdOrNull(inserted.id!!), created = T1 to ALICE, modified = T2 to BOB)
        }

        @Test
        fun testSaveAllInsertSetsOnlyCreatedFields() {
            val saved = documents.saveAll(listOf(AuditedDocument(title = "A"), AuditedDocument(title = "B")))

            saved.forEach { assertAudited(it, created = T1 to ALICE, modified = null to null) }
        }
    }

    @Nested
    @SpringJUnitConfig(NoDatesAuditingConfiguration::class)
    @Transactional
    inner class NoDates : AuditingContextTest() {

        @Test
        fun testInsertSetsOnlyAuditors() {
            val saved = documents.save(AuditedDocument(title = "Draft"))

            assertAudited(saved, created = null to ALICE, modified = null to ALICE)
            assertAudited(documents.findByIdOrNull(saved.id!!), created = null to ALICE, modified = null to ALICE)
        }

        @Test
        fun testUpdateSetsOnlyModifiedAuditor() {
            val inserted = documents.save(AuditedDocument(title = "Draft"))
            clock.now = T2
            auditor.current = BOB

            val updated = documents.save(inserted.copy(title = "Published"))

            assertAudited(updated, created = null to ALICE, modified = null to BOB)
            assertAudited(documents.findByIdOrNull(inserted.id!!), created = null to ALICE, modified = null to BOB)
        }

        @Test
        fun testDatesSetByTheCallerAreKept() {
            val saved = documents.save(AuditedDocument(title = "Draft", createdAt = EARLIER, modifiedAt = EARLIER))

            assertAudited(saved, created = EARLIER to ALICE, modified = EARLIER to ALICE)
        }
    }

    /** Documents the behaviour without [EnableExposedAuditing]: passes today and must keep passing. */
    @Nested
    @SpringJUnitConfig(NoAuditingConfiguration::class)
    @Transactional
    inner class NoAuditing : AuditingContextTest() {

        @Test
        fun testInsertSetsNothing() {
            val saved = documents.save(AuditedDocument(title = "Draft"))

            assertAudited(saved, created = null to null, modified = null to null)
            assertAudited(documents.findByIdOrNull(saved.id!!), created = null to null, modified = null to null)
        }

        @Test
        fun testUpdateSetsNothing() {
            val inserted = documents.save(AuditedDocument(title = "Draft"))
            clock.now = T2

            val updated = documents.save(inserted.copy(title = "Published"))

            assertAudited(updated, created = null to null, modified = null to null)
        }

        @Test
        fun testValuesSetByTheCallerAreStoredAsIs() {
            val saved = documents.save(
                AuditedDocument(title = "Draft", createdAt = EARLIER, createdBy = "mallory", modifiedAt = EARLIER, modifiedBy = "mallory")
            )

            assertAudited(
                documents.findByIdOrNull(saved.id!!),
                created = EARLIER to "mallory",
                modified = EARLIER to "mallory",
            )
        }

        @Test
        fun testOtherDateTypesAndVersionedEntitySetNothing() {
            val event = events.save(AuditedEvent(name = "Launch"))
            val note = notes.save(AuditedNote(text = "Hello"))

            assertNull(event.createdAt)
            assertNull(event.modifiedAt)
            assertEquals(0L, note.version)
            assertNull(note.createdAt)
            assertNull(note.createdBy)
            assertNull(note.modifiedAt)
        }
    }

    @ExposedRepository(table = AuditedDocuments::class)
    interface AuditedDocumentRepository : ExposedCrudRepository<AuditedDocument, Long>

    @ExposedRepository(table = AuditedEvents::class)
    interface AuditedEventRepository : ExposedCrudRepository<AuditedEvent, Long>

    @ExposedRepository(table = AuditedNotes::class)
    interface AuditedNoteRepository : ExposedCrudRepository<AuditedNote, Long>

    companion object {
        // millisecond precision, so the values survive the round trip through H2 and the conversion to Long
        val EARLIER: Instant = Instant.parse("2020-01-01T00:00:00Z")
        val T1: Instant = Instant.parse("2025-03-01T10:15:30.123Z")
        val T2: Instant = Instant.parse("2025-03-02T11:16:31.456Z")
        val T3: Instant = Instant.parse("2025-03-03T12:17:32.789Z")
        const val ALICE = "alice"
        const val BOB = "bob"
        const val CAROL = "carol"

        /** Spring Data converts the provided `Instant` to a `LocalDateTime` in the system time zone. */
        fun localDateTime(instant: Instant): LocalDateTime = LocalDateTime.ofInstant(instant, ZoneId.systemDefault())
    }
}

/** Shared setup of the auditing contexts: tables, auditor and clock are reset before every test. */
abstract class AuditingContextTest {

    @Autowired
    lateinit var tm: PlatformTransactionManager

    @Autowired
    lateinit var documents: AuditingRepositoryTest.AuditedDocumentRepository

    @Autowired
    lateinit var events: AuditingRepositoryTest.AuditedEventRepository

    @Autowired
    lateinit var notes: AuditingRepositoryTest.AuditedNoteRepository

    @Autowired
    @Qualifier("testAuditor")
    lateinit var auditor: TestAuditorAware

    @Autowired
    @Qualifier("fixedClock")
    lateinit var clock: FixedDateTimeProvider

    @BeforeEach
    fun createTables() {
        TransactionTemplate(tm).executeWithoutResult { SchemaUtils.create(AuditedDocuments, AuditedEvents, AuditedNotes) }
        auditor.current = AuditingRepositoryTest.ALICE
        clock.now = AuditingRepositoryTest.T1
    }

    @AfterEach
    fun dropTables() = TransactionTemplate(tm).executeWithoutResult { SchemaUtils.drop(AuditedDocuments, AuditedEvents, AuditedNotes) }

    /** Checks the audit fields of [document]; each pair is (date, auditor). */
    protected fun assertAudited(document: AuditedDocument?, created: Pair<Instant?, String?>, modified: Pair<Instant?, String?>) {
        assertNotNull(document)
        assertEquals(created.first, document.createdAt, "createdAt")
        assertEquals(created.second, document.createdBy, "createdBy")
        assertEquals(modified.first, document.modifiedAt, "modifiedAt")
        assertEquals(modified.second, document.modifiedBy, "modifiedBy")
    }
}

/** An [AuditorAware] whose [current] auditor the tests can change; `null` means there is no auditor. */
class TestAuditorAware : AuditorAware<String> {
    @Volatile
    var current: String? = AuditingRepositoryTest.ALICE

    override fun getCurrentAuditor(): Optional<String> = Optional.ofNullable(current)
}

/** A [DateTimeProvider] that returns the fixed instant [now], which the tests move forward. */
class FixedDateTimeProvider : DateTimeProvider {
    @Volatile
    var now: Instant = AuditingRepositoryTest.T1

    override fun getNow(): Optional<TemporalAccessor> = Optional.of(now)
}

/** Beans shared by all auditing contexts. The auditor and clock beans are also defined where auditing is disabled. */
abstract class AuditingTestBeans {
    @Bean
    open fun dataSource(): DataSource = EmbeddedDatabaseBuilder()
        .setType(EmbeddedDatabaseType.H2)
        .generateUniqueName(true)
        .build()

    @Bean
    open fun transactionManager(dataSource: DataSource): PlatformTransactionManager =
        SpringTransactionManager(dataSource)

    @Bean
    open fun testAuditor(): TestAuditorAware = TestAuditorAware()

    @Bean
    open fun otherAuditor(): TestAuditorAware = TestAuditorAware().apply { current = "eve" }

    @Bean
    open fun fixedClock(): FixedDateTimeProvider = FixedDateTimeProvider()

    @Bean
    open fun otherClock(): FixedDateTimeProvider = FixedDateTimeProvider().apply { now = Instant.parse("2026-01-01T00:00:00Z") }
}

@Configuration
@EnableTransactionManagement
@EnableExposedRepositories(
    considerNestedRepositories = true,
    includeFilters = [
        ComponentScan.Filter(
            type = FilterType.ASSIGNABLE_TYPE,
            classes = [
                AuditingRepositoryTest.AuditedDocumentRepository::class,
                AuditingRepositoryTest.AuditedEventRepository::class,
                AuditingRepositoryTest.AuditedNoteRepository::class,
            ],
        )
    ],
)
@EnableExposedAuditing(auditorAwareRef = "testAuditor", dateTimeProviderRef = "fixedClock")
open class DefaultAuditingConfiguration : AuditingTestBeans()

@Configuration
@EnableTransactionManagement
@EnableExposedRepositories(
    considerNestedRepositories = true,
    includeFilters = [
        ComponentScan.Filter(
            type = FilterType.ASSIGNABLE_TYPE,
            classes = [
                AuditingRepositoryTest.AuditedDocumentRepository::class,
                AuditingRepositoryTest.AuditedEventRepository::class,
                AuditingRepositoryTest.AuditedNoteRepository::class,
            ],
        )
    ],
)
@EnableExposedAuditing(auditorAwareRef = "testAuditor", dateTimeProviderRef = "fixedClock", modifyOnCreate = false)
open class NoModifyOnCreateAuditingConfiguration : AuditingTestBeans()

@Configuration
@EnableTransactionManagement
@EnableExposedRepositories(
    considerNestedRepositories = true,
    includeFilters = [
        ComponentScan.Filter(
            type = FilterType.ASSIGNABLE_TYPE,
            classes = [
                AuditingRepositoryTest.AuditedDocumentRepository::class,
                AuditingRepositoryTest.AuditedEventRepository::class,
                AuditingRepositoryTest.AuditedNoteRepository::class,
            ],
        )
    ],
)
@EnableExposedAuditing(auditorAwareRef = "testAuditor", dateTimeProviderRef = "fixedClock", setDates = false)
open class NoDatesAuditingConfiguration : AuditingTestBeans()

@Configuration
@EnableTransactionManagement
@EnableExposedRepositories(
    considerNestedRepositories = true,
    includeFilters = [
        ComponentScan.Filter(
            type = FilterType.ASSIGNABLE_TYPE,
            classes = [
                AuditingRepositoryTest.AuditedDocumentRepository::class,
                AuditingRepositoryTest.AuditedEventRepository::class,
                AuditingRepositoryTest.AuditedNoteRepository::class,
            ],
        )
    ],
)
open class NoAuditingConfiguration : AuditingTestBeans()

object AuditedDocuments : LongIdTable("audited_documents") {
    val title = varchar("title", 255)
    val createdAt = timestamp("created_at").nullable()
    val modifiedAt = timestamp("modified_at").nullable()
    val createdBy = varchar("created_by", 64).nullable()
    val modifiedBy = varchar("modified_by", 64).nullable()
}

/** Immutable, with `Instant` dates. The audit annotations land on the backing fields (their Java target). */
data class AuditedDocument(
    val id: Long? = null,
    val title: String,
    @CreatedDate val createdAt: Instant? = null,
    @LastModifiedDate val modifiedAt: Instant? = null,
    @CreatedBy val createdBy: String? = null,
    @LastModifiedBy val modifiedBy: String? = null,
)

object AuditedEvents : LongIdTable("audited_events") {
    val name = varchar("name", 255)
    val createdAt = datetime("created_at").nullable()
    val modifiedAt = long("modified_at").nullable()
}

/** `LocalDateTime` and `Long` (epoch millis) dates, no auditor properties. */
data class AuditedEvent(
    val id: Long? = null,
    val name: String,
    @CreatedDate val createdAt: LocalDateTime? = null,
    @LastModifiedDate val modifiedAt: Long? = null,
)

object AuditedNotes : LongIdTable("audited_notes") {
    val text = varchar("text", 255)
    val version = long("version")
    val createdAt = timestamp("created_at").nullable()
    val modifiedAt = timestamp("modified_at").nullable()
    val createdBy = varchar("created_by", 64).nullable()
}

/** Auditing together with optimistic locking: the version decides whether the entity is new. */
data class AuditedNote(
    val id: Long? = null,
    val text: String,
    @Version val version: Long? = null,
    @CreatedDate val createdAt: Instant? = null,
    @LastModifiedDate val modifiedAt: Instant? = null,
    @CreatedBy val createdBy: String? = null,
)
