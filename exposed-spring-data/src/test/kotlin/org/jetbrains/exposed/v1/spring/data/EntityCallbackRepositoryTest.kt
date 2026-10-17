package org.jetbrains.exposed.v1.spring.data

import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.dao.id.LongIdTable
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.insertAndGetId
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.spring.data.event.AfterConvertCallback
import org.jetbrains.exposed.v1.spring.data.event.AfterConvertEvent
import org.jetbrains.exposed.v1.spring.data.event.AfterDeleteCallback
import org.jetbrains.exposed.v1.spring.data.event.AfterDeleteEvent
import org.jetbrains.exposed.v1.spring.data.event.AfterSaveCallback
import org.jetbrains.exposed.v1.spring.data.event.AfterSaveEvent
import org.jetbrains.exposed.v1.spring.data.event.BeforeConvertCallback
import org.jetbrains.exposed.v1.spring.data.event.BeforeConvertEvent
import org.jetbrains.exposed.v1.spring.data.event.BeforeDeleteCallback
import org.jetbrains.exposed.v1.spring.data.event.BeforeDeleteEvent
import org.jetbrains.exposed.v1.spring.data.event.BeforeSaveCallback
import org.jetbrains.exposed.v1.spring.data.event.BeforeSaveEvent
import org.jetbrains.exposed.v1.spring.data.event.ExposedEntityEvent
import org.jetbrains.exposed.v1.spring.data.query.query
import org.jetbrains.exposed.v1.spring7.transaction.SpringTransactionManager
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.FilterType
import org.springframework.context.event.EventListener
import org.springframework.core.annotation.Order
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.annotation.EnableTransactionManagement
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate
import java.util.concurrent.CopyOnWriteArrayList
import javax.sql.DataSource

/**
 * Entity callbacks ([BeforeConvertCallback], [BeforeSaveCallback], [AfterSaveCallback], [AfterConvertCallback],
 * [BeforeDeleteCallback], [AfterDeleteCallback]) and the matching application events ([ExposedEntityEvent]):
 * - `save` / `saveAll` run, per entity, `BeforeConvert` → `BeforeSave` → insert or update → `AfterSave`,
 * - the entity returned by a callback is passed on: `BeforeConvert` / `BeforeSave` results are stored, the `AfterSave`
 *   result is what `save` returns, the `AfterConvert` result is what a read returns,
 * - `AfterConvert` runs for every row read as the entity type (CRUD reads, derived queries, `query { }`), but not for
 *   DTO, interface or `ResultRow` projections,
 * - `delete(entity)` / `deleteAll(entities)` run `BeforeDelete` → delete → `AfterDelete`, id-based and bulk deletes run nothing,
 * - callbacks are matched by entity type and ordered by [Order],
 * - each event is published right after its callbacks and carries the entity they returned and the table.
 *
 * Every callback records into [CallbackJournal], and so does the event listener, so a test can check the whole sequence.
 * The save and delete callbacks also record the status stored for the entity's code at call time, which shows whether
 * they ran before or after the SQL statement.
 *
 * Runs in its own context with a nested repository, so the shared [SpringDataExposedTestConfiguration] doesn't pick it up.
 */
@SpringJUnitConfig(EntityCallbackTestConfiguration::class)
@Transactional
open class EntityCallbackRepositoryTest {

    @Autowired
    lateinit var tm: PlatformTransactionManager

    @Autowired
    lateinit var shipments: ShipmentRepository

    @Autowired
    lateinit var journal: CallbackJournal

    @Autowired
    lateinit var firstBeforeConvert: FirstBeforeConvertCallback

    @Autowired
    lateinit var secondBeforeConvert: SecondBeforeConvertCallback

    @Autowired
    lateinit var beforeSave: RecordingBeforeSaveCallback

    @Autowired
    lateinit var afterSave: RecordingAfterSaveCallback

    @Autowired
    lateinit var afterConvert: RecordingAfterConvertCallback

    @Autowired
    lateinit var beforeDelete: RecordingBeforeDeleteCallback

    @Autowired
    lateinit var afterDelete: RecordingAfterDeleteCallback

    private val callbacks: List<RecordingShipmentCallback>
        get() = listOf(firstBeforeConvert, secondBeforeConvert, beforeSave, afterSave, afterConvert, beforeDelete, afterDelete)

    @BeforeEach
    fun createTables() {
        TransactionTemplate(tm).executeWithoutResult { SchemaUtils.create(Shipments) }
        callbacks.forEach { it.reset() }
        journal.clear()
    }

    @AfterEach
    fun dropTables() = TransactionTemplate(tm).executeWithoutResult { SchemaUtils.drop(Shipments) }

    // ---- save: insert ----

    @Test
    fun testSaveOfNewEntityRunsCallbacksAndEventsInOrder() {
        shipments.save(Shipment(code = "A", destination = "Berlin"))

        assertEquals(
            listOf(
                BEFORE_CONVERT_1, BEFORE_CONVERT_2, "BeforeConvertEvent",
                BEFORE_SAVE, "BeforeSaveEvent",
                AFTER_SAVE, "AfterSaveEvent",
            ),
            journal.stepsWithoutReads(),
        )
    }

    @Test
    fun testSaveOfNewEntityRunsBeforeSaveBeforeAndAfterSaveAfterTheInsert() {
        shipments.save(Shipment(code = "A", destination = "Berlin"))

        assertEquals(listOf<String?>(null), journal.storedStatusesAt(BEFORE_SAVE))
        assertEquals(listOf<String?>("NEW"), journal.storedStatusesAt(AFTER_SAVE))
    }

    @Test
    fun testAfterSaveOfNewEntityGetsTheSavedEntityWithId() {
        val saved = shipments.save(Shipment(code = "A", destination = "Berlin"))

        val afterSaveEntity = journal.shipmentsAt(AFTER_SAVE).single()
        assertEquals(saved, afterSaveEntity)
        assertEquals(storedIdOf("A"), afterSaveEntity.id)
    }

    // ---- save: update ----

    @Test
    fun testSaveOfExistingEntityRunsCallbacksAndEventsInOrder() {
        val existing = seed(code = "A", destination = "Berlin")

        shipments.save(existing.copy(status = "SHIPPED"))

        assertEquals(
            listOf(
                BEFORE_CONVERT_1, BEFORE_CONVERT_2, "BeforeConvertEvent",
                BEFORE_SAVE, "BeforeSaveEvent",
                AFTER_SAVE, "AfterSaveEvent",
            ),
            journal.stepsWithoutReads(),
        )
    }

    @Test
    fun testSaveOfExistingEntityRunsBeforeSaveBeforeAndAfterSaveAfterTheUpdate() {
        val existing = seed(code = "A", destination = "Berlin")

        shipments.save(existing.copy(status = "SHIPPED"))

        assertEquals(listOf<String?>("NEW"), journal.storedStatusesAt(BEFORE_SAVE))
        assertEquals(listOf<String?>("SHIPPED"), journal.storedStatusesAt(AFTER_SAVE))
    }

    // ---- save: values returned by callbacks ----

    @Test
    fun testValueChangedByBeforeConvertIsStoredOnInsert() {
        firstBeforeConvert.transform = { it.copy(destination = it.destination.uppercase()) }

        shipments.save(Shipment(code = "A", destination = "Berlin"))

        assertEquals("BERLIN", storedDestinationOf("A"))
    }

    @Test
    fun testValueChangedByBeforeConvertIsStoredOnUpdate() {
        val existing = seed(code = "A", destination = "Berlin")
        firstBeforeConvert.transform = { it.copy(destination = it.destination.uppercase()) }

        shipments.save(existing.copy(destination = "Hamburg"))

        assertEquals("HAMBURG", storedDestinationOf("A"))
    }

    @Test
    fun testValueChangedByBeforeSaveIsStored() {
        beforeSave.transform = { it.copy(status = "READY") }

        shipments.save(Shipment(code = "A", destination = "Berlin"))

        assertEquals("READY", storedStatusOf("A"))
    }

    @Test
    fun testBeforeSaveGetsTheEntityReturnedByBeforeConvert() {
        firstBeforeConvert.transform = { it.copy(destination = it.destination.uppercase()) }

        shipments.save(Shipment(code = "A", destination = "Berlin"))

        assertEquals(listOf("BERLIN"), journal.shipmentsAt(BEFORE_SAVE).map { it.destination })
    }

    @Test
    fun testSaveReturnsWhatAfterSaveReturns() {
        afterSave.transform = { it.copy(status = "RETURNED_BY_AFTER_SAVE") }

        val saved = shipments.save(Shipment(code = "A", destination = "Berlin"))

        assertEquals("RETURNED_BY_AFTER_SAVE", saved.status)
        // the returned value is not written back
        assertEquals("NEW", storedStatusOf("A"))
    }

    // ---- ordering and type matching ----

    @Test
    fun testCallbacksOfTheSameTypeRunInOrderOfTheirOrderAnnotation() {
        shipments.save(Shipment(code = "A", destination = "Berlin"))

        // the @Order(2) callback is declared first in the configuration
        assertEquals(listOf(BEFORE_CONVERT_1, BEFORE_CONVERT_2), journal.steps().filter { it.startsWith("BeforeConvert#") })
    }

    @Test
    fun testCallbacksOfTheSameTypeAreChained() {
        firstBeforeConvert.transform = { it.copy(code = it.code + "-1") }
        secondBeforeConvert.transform = { it.copy(code = it.code + "-2") }

        shipments.save(Shipment(code = "A", destination = "Berlin"))

        assertEquals(listOf("A-1"), journal.shipmentsAt(BEFORE_CONVERT_2).map { it.code })
        assertEquals(listOf("A-1-2"), Shipments.select(Shipments.code).map { it[Shipments.code] })
    }

    @Test
    fun testCallbacksForAnotherEntityTypeAreNotInvoked() {
        shipments.save(Shipment(code = "A", destination = "Berlin"))
        shipments.findAll()

        assertTrue(journal.steps().containsAll(listOf(BEFORE_CONVERT_1, BEFORE_SAVE, AFTER_SAVE, AFTER_CONVERT))) { journal.steps().toString() }
        assertTrue(journal.steps().none { it.startsWith(TICKET_PREFIX) }) { journal.steps().toString() }
    }

    // ---- saveAll ----

    @Test
    fun testSaveAllRunsCallbacksAndEventsInOrderForEachEntity() {
        val existing = seed(code = "B", destination = "Hamburg")

        shipments.saveAll(listOf(Shipment(code = "A", destination = "Berlin"), existing.copy(status = "SHIPPED")))

        val expected = listOf(
            BEFORE_CONVERT_1, BEFORE_CONVERT_2, "BeforeConvertEvent",
            BEFORE_SAVE, "BeforeSaveEvent",
            AFTER_SAVE, "AfterSaveEvent",
        )
        assertEquals(expected, journal.stepsWithoutReadsFor("A"))
        assertEquals(expected, journal.stepsWithoutReadsFor("B"))
    }

    @Test
    fun testSaveAllStoresValuesChangedByBeforeConvertForEachEntity() {
        val existing = seed(code = "B", destination = "Hamburg")
        firstBeforeConvert.transform = { it.copy(destination = it.destination.uppercase()) }

        shipments.saveAll(listOf(Shipment(code = "A", destination = "Berlin"), existing.copy(destination = "Munich")))

        assertEquals("BERLIN", storedDestinationOf("A"))
        assertEquals("MUNICH", storedDestinationOf("B"))
    }

    @Test
    fun testSaveAllReturnsWhatAfterSaveReturnsForEachEntity() {
        afterSave.transform = { it.copy(status = "RETURNED_BY_AFTER_SAVE") }

        val saved = shipments.saveAll(listOf(Shipment(code = "A", destination = "Berlin"), Shipment(code = "B", destination = "Hamburg")))

        assertEquals(listOf("A", "B"), saved.map { it.code })
        assertEquals(listOf("RETURNED_BY_AFTER_SAVE", "RETURNED_BY_AFTER_SAVE"), saved.map { it.status })
    }

    // ---- AfterConvert: entity reads ----

    @Test
    fun testFindByIdRunsAfterConvertAndReturnsItsResult() {
        val existing = seed(code = "A", destination = "Berlin")
        afterConvert.transform = { it.copy(status = "CONVERTED") }

        val found = shipments.findById(existing.id!!).get()

        assertEquals("CONVERTED", found.status)
        assertEquals(listOf(existing), journal.shipmentsAt(AFTER_CONVERT))
    }

    @Test
    fun testFindAllRunsAfterConvertForEachRow() {
        seed(code = "A", destination = "Berlin")
        seed(code = "B", destination = "Hamburg")
        afterConvert.transform = { it.copy(status = "CONVERTED") }

        val found = shipments.findAll()

        assertEquals(listOf("CONVERTED", "CONVERTED"), found.map { it.status })
        assertEquals(setOf("A", "B"), journal.shipmentsAt(AFTER_CONVERT).map { it.code }.toSet())
        assertEquals(2, journal.shipmentsAt(AFTER_CONVERT).size)
    }

    @Test
    fun testFindAllByIdRunsAfterConvertForEachRow() {
        val a = seed(code = "A", destination = "Berlin")
        seed(code = "B", destination = "Hamburg")
        val c = seed(code = "C", destination = "Munich")

        shipments.findAllById(listOf(a.id!!, c.id!!))

        assertEquals(setOf("A", "C"), journal.shipmentsAt(AFTER_CONVERT).map { it.code }.toSet())
        assertEquals(2, journal.shipmentsAt(AFTER_CONVERT).size)
    }

    @Test
    fun testDerivedQueryForSingleEntityRunsAfterConvert() {
        seed(code = "A", destination = "Berlin")
        afterConvert.transform = { it.copy(status = "CONVERTED") }

        val found = shipments.findByCode("A")

        assertEquals("CONVERTED", found?.status)
        assertEquals(listOf("A"), journal.shipmentsAt(AFTER_CONVERT).map { it.code })
    }

    @Test
    fun testDerivedQueryForEntityListRunsAfterConvertForEachRow() {
        seed(code = "A", destination = "Berlin")
        seed(code = "B", destination = "Hamburg", status = "SHIPPED")
        seed(code = "C", destination = "Munich")

        shipments.findByStatusOrderByCodeAsc("NEW")

        assertEquals(listOf("A", "C"), journal.shipmentsAt(AFTER_CONVERT).map { it.code })
    }

    @Test
    fun testCustomQueryForEntityListRunsAfterConvertForEachRow() {
        seed(code = "A", destination = "Berlin")
        seed(code = "B", destination = "Hamburg")
        afterConvert.transform = { it.copy(status = "CONVERTED") }

        val found = shipments.allOrderedByCode()

        assertEquals(listOf("CONVERTED", "CONVERTED"), found.map { it.status })
        assertEquals(listOf("A", "B"), journal.shipmentsAt(AFTER_CONVERT).map { it.code })
    }

    @Test
    fun testCustomQueryForSingleEntityRunsAfterConvert() {
        seed(code = "A", destination = "Berlin")
        afterConvert.transform = { it.copy(status = "CONVERTED") }

        val found = shipments.oneByCode("A")

        assertEquals("CONVERTED", found.status)
        assertEquals(listOf("A"), journal.shipmentsAt(AFTER_CONVERT).map { it.code })
    }

    @Test
    fun testAfterConvertEventCarriesTheEntityReturnedByAfterConvert() {
        seed(code = "A", destination = "Berlin")
        afterConvert.transform = { it.copy(status = "CONVERTED") }

        shipments.findByCode("A")

        assertEquals(listOf(AFTER_CONVERT, "AfterConvertEvent"), journal.steps())
        assertEquals(listOf("CONVERTED"), journal.shipmentsAt("AfterConvertEvent").map { it.status })
    }

    // ---- AfterConvert: projections ----

    @Test
    fun testDerivedDtoProjectionDoesNotRunAfterConvert() {
        seed(code = "A", destination = "Berlin")

        val found = shipments.findCodeByDestination("Berlin")

        assertEquals(ShipmentCode("A"), found)
        assertEquals(emptyList<String>(), journal.steps())
    }

    @Test
    fun testDerivedInterfaceProjectionDoesNotRunAfterConvert() {
        seed(code = "A", destination = "Berlin")

        val found = shipments.findDestinationByCode("A")

        assertEquals("Berlin", found?.destination)
        assertEquals(emptyList<String>(), journal.steps())
    }

    @Test
    fun testCustomQueryDtoProjectionDoesNotRunAfterConvert() {
        seed(code = "A", destination = "Berlin")
        seed(code = "B", destination = "Hamburg")

        val found = shipments.codesOrderedByCode()

        assertEquals(listOf(ShipmentCode("A"), ShipmentCode("B")), found)
        assertEquals(emptyList<String>(), journal.steps())
    }

    @Test
    fun testCustomQueryResultRowsDoNotRunAfterConvert() {
        seed(code = "A", destination = "Berlin")

        val rows = shipments.rowsOrderedByCode()

        assertEquals(listOf("A"), rows.map { it[Shipments.code] })
        assertEquals(emptyList<String>(), journal.steps())
    }

    @Test
    fun testOnlyTheEntityReadRunsAfterConvertNextToProjectionReads() {
        seed(code = "A", destination = "Berlin")

        shipments.findByCode("A")
        shipments.findCodeByDestination("Berlin")
        shipments.findDestinationByCode("A")
        shipments.codesOrderedByCode()
        shipments.rowsOrderedByCode()

        assertEquals(listOf(AFTER_CONVERT, "AfterConvertEvent"), journal.steps())
    }

    // ---- delete ----

    @Test
    fun testDeleteRunsCallbacksAndEventsInOrder() {
        val existing = seed(code = "A", destination = "Berlin")

        shipments.delete(existing)

        assertEquals(listOf(BEFORE_DELETE, "BeforeDeleteEvent", AFTER_DELETE, "AfterDeleteEvent"), journal.steps())
    }

    @Test
    fun testDeleteRunsBeforeDeleteBeforeAndAfterDeleteAfterTheDelete() {
        val existing = seed(code = "A", destination = "Berlin")

        shipments.delete(existing)

        assertEquals(listOf<String?>("NEW"), journal.storedStatusesAt(BEFORE_DELETE))
        assertEquals(listOf<String?>(null), journal.storedStatusesAt(AFTER_DELETE))
    }

    @Test
    fun testDeleteCallbacksGetThePassedEntity() {
        val existing = seed(code = "A", destination = "Berlin")

        shipments.delete(existing)

        assertEquals(listOf(existing), journal.shipmentsAt(BEFORE_DELETE))
        assertEquals(listOf(existing), journal.shipmentsAt(AFTER_DELETE))
    }

    @Test
    fun testDeleteAllEntitiesRunsCallbacksAndEventsInOrderForEachEntity() {
        val a = seed(code = "A", destination = "Berlin")
        val b = seed(code = "B", destination = "Hamburg")
        seed(code = "C", destination = "Munich")

        shipments.deleteAll(listOf(a, b))

        val expected = listOf(BEFORE_DELETE, "BeforeDeleteEvent", AFTER_DELETE, "AfterDeleteEvent")
        assertEquals(expected, journal.stepsFor("A"))
        assertEquals(expected, journal.stepsFor("B"))
        assertEquals(emptyList<String>(), journal.stepsFor("C"))
    }

    @Test
    fun testDeleteByIdRunsNoCallbacks() {
        val existing = seed(code = "A", destination = "Berlin")

        shipments.deleteById(existing.id!!)

        assertEquals(emptyList<String>(), journal.steps())
        assertEquals(null, storedStatusOf("A"))
    }

    @Test
    fun testDeleteAllByIdAndDeleteAllWithoutArgumentsRunNoCallbacks() {
        val a = seed(code = "A", destination = "Berlin")
        seed(code = "B", destination = "Hamburg")

        shipments.deleteAllById(listOf(a.id!!))
        shipments.deleteAll()

        assertEquals(emptyList<String>(), journal.steps())
        assertEquals(0L, Shipments.selectAll().count())
    }

    // ---- application events ----

    @Test
    fun testSaveEventsCarryTheEntitiesReturnedByTheCallbacks() {
        firstBeforeConvert.transform = { it.copy(destination = it.destination.uppercase()) }
        beforeSave.transform = { it.copy(status = "READY") }
        afterSave.transform = { it.copy(status = "RETURNED_BY_AFTER_SAVE") }

        shipments.save(Shipment(code = "A", destination = "Berlin"))

        assertEquals(listOf(Shipment(code = "A", destination = "BERLIN")), journal.shipmentsAt("BeforeConvertEvent"))
        assertEquals(listOf(Shipment(code = "A", destination = "BERLIN", status = "READY")), journal.shipmentsAt("BeforeSaveEvent"))
        assertEquals(listOf("RETURNED_BY_AFTER_SAVE"), journal.shipmentsAt("AfterSaveEvent").map { it.status })
    }

    @Test
    fun testDeleteEventsCarryTheEntitiesReturnedByTheCallbacks() {
        val existing = seed(code = "A", destination = "Berlin")
        beforeDelete.transform = { it.copy(status = "DELETING") }
        afterDelete.transform = { it.copy(status = "DELETED") }

        shipments.delete(existing)

        assertEquals(listOf(existing.copy(status = "DELETING")), journal.shipmentsAt("BeforeDeleteEvent"))
        assertEquals(listOf(existing.copy(status = "DELETED")), journal.shipmentsAt("AfterDeleteEvent"))
    }

    @Test
    fun testEventsCarryTheTableOfTheRepository() {
        val existing = seed(code = "A", destination = "Berlin")

        shipments.save(existing.copy(status = "SHIPPED"))
        shipments.findById(existing.id!!)
        shipments.delete(existing.copy(status = "SHIPPED"))

        val events = journal.entries.filter { it.step.endsWith("Event") }
        assertEquals(
            setOf("BeforeConvertEvent", "BeforeSaveEvent", "AfterSaveEvent", "AfterConvertEvent", "BeforeDeleteEvent", "AfterDeleteEvent"),
            events.map { it.step }.toSet(),
        )
        events.forEach { assertSame(Shipments, it.table, it.step) }
    }

    @Test
    fun testCallbacksGetTheTableOfTheRepository() {
        shipments.save(Shipment(code = "A", destination = "Berlin"))

        val callbackEntries = journal.entries.filterNot { it.step.endsWith("Event") }
        assertTrue(callbackEntries.isNotEmpty())
        callbackEntries.forEach { assertSame(Shipments, it.table, it.step) }
    }

    /** Inserts a row with the DSL, so no callback runs. */
    private fun seed(code: String, destination: String, status: String = "NEW"): Shipment {
        val id = Shipments.insertAndGetId {
            it[Shipments.code] = code
            it[Shipments.destination] = destination
            it[Shipments.status] = status
        }.value
        return Shipment(id = id, code = code, destination = destination, status = status)
    }

    private fun storedIdOf(code: String): Long? =
        Shipments.select(Shipments.id).where { Shipments.code eq code }.singleOrNull()?.get(Shipments.id)?.value

    private fun storedStatusOf(code: String): String? =
        Shipments.select(Shipments.status).where { Shipments.code eq code }.singleOrNull()?.get(Shipments.status)

    private fun storedDestinationOf(code: String): String? =
        Shipments.select(Shipments.destination).where { Shipments.code eq code }.singleOrNull()?.get(Shipments.destination)

    @ExposedRepository(table = Shipments::class)
    interface ShipmentRepository : ExposedCrudRepository<Shipment, Long> {
        fun findByCode(code: String): Shipment?

        fun findByStatusOrderByCodeAsc(status: String): List<Shipment>

        fun findCodeByDestination(destination: String): ShipmentCode?

        fun findDestinationByCode(code: String): ShipmentDestinationView?

        fun allOrderedByCode(): List<Shipment> = query {
            Shipments.selectAll().orderBy(Shipments.code).toList()
        }

        fun oneByCode(code: String): Shipment = query {
            Shipments.selectAll().where { Shipments.code eq code }.single()
        }

        fun codesOrderedByCode(): List<ShipmentCode> = query {
            Shipments.selectAll().orderBy(Shipments.code).toList()
        }

        fun rowsOrderedByCode(): List<ResultRow> = query {
            Shipments.selectAll().orderBy(Shipments.code).toList()
        }
    }
}

private const val BEFORE_CONVERT_1 = "BeforeConvert#1"
private const val BEFORE_CONVERT_2 = "BeforeConvert#2"
private const val BEFORE_SAVE = "BeforeSave"
private const val AFTER_SAVE = "AfterSave"
private const val AFTER_CONVERT = "AfterConvert"
private const val BEFORE_DELETE = "BeforeDelete"
private const val AFTER_DELETE = "AfterDelete"
private const val TICKET_PREFIX = "Ticket:"

@Configuration
@EnableTransactionManagement
@EnableExposedRepositories(
    considerNestedRepositories = true,
    includeFilters = [
        ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = [EntityCallbackRepositoryTest.ShipmentRepository::class])
    ],
)
open class EntityCallbackTestConfiguration {
    @Bean
    open fun dataSource(): DataSource = EmbeddedDatabaseBuilder()
        .setType(EmbeddedDatabaseType.H2)
        .generateUniqueName(true)
        .build()

    @Bean
    open fun transactionManager(dataSource: DataSource): PlatformTransactionManager =
        SpringTransactionManager(dataSource)

    @Bean
    open fun callbackJournal(): CallbackJournal = CallbackJournal()

    // declared before the @Order(1) callback, so the order can't come from the declaration order
    @Bean
    open fun secondBeforeConvertCallback(journal: CallbackJournal): SecondBeforeConvertCallback = SecondBeforeConvertCallback(journal)

    @Bean
    open fun firstBeforeConvertCallback(journal: CallbackJournal): FirstBeforeConvertCallback = FirstBeforeConvertCallback(journal)

    @Bean
    open fun recordingBeforeSaveCallback(journal: CallbackJournal): RecordingBeforeSaveCallback = RecordingBeforeSaveCallback(journal)

    @Bean
    open fun recordingAfterSaveCallback(journal: CallbackJournal): RecordingAfterSaveCallback = RecordingAfterSaveCallback(journal)

    @Bean
    open fun recordingAfterConvertCallback(journal: CallbackJournal): RecordingAfterConvertCallback = RecordingAfterConvertCallback(journal)

    @Bean
    open fun recordingBeforeDeleteCallback(journal: CallbackJournal): RecordingBeforeDeleteCallback = RecordingBeforeDeleteCallback(journal)

    @Bean
    open fun recordingAfterDeleteCallback(journal: CallbackJournal): RecordingAfterDeleteCallback = RecordingAfterDeleteCallback(journal)

    @Bean
    open fun ticketBeforeConvertCallback(journal: CallbackJournal): TicketBeforeConvertCallback = TicketBeforeConvertCallback(journal)

    @Bean
    open fun ticketAfterConvertCallback(journal: CallbackJournal): TicketAfterConvertCallback = TicketAfterConvertCallback(journal)

    @Bean
    open fun recordingEntityEventListener(journal: CallbackJournal): RecordingEntityEventListener = RecordingEntityEventListener(journal)
}

/** Records callback invocations and published events in the order they happen. */
open class CallbackJournal {
    data class Entry(val step: String, val entity: Any, val table: Table, val storedStatus: String?)

    private val recorded = CopyOnWriteArrayList<Entry>()
    val entries: List<Entry> get() = recorded.toList()

    fun record(step: String, entity: Any, table: Table, storedStatus: String? = null) {
        recorded += Entry(step, entity, table, storedStatus)
    }

    fun steps(): List<String> = entries.map { it.step }

    /** Steps without `AfterConvert`, whether `save` reads the saved row back as entity is not part of these tests. */
    fun stepsWithoutReads(): List<String> = steps().filterNot { it.startsWith(AFTER_CONVERT) }

    fun stepsFor(code: String): List<String> = entries.filter { (it.entity as? Shipment)?.code == code }.map { it.step }

    fun stepsWithoutReadsFor(code: String): List<String> = stepsFor(code).filterNot { it.startsWith(AFTER_CONVERT) }

    fun shipmentsAt(step: String): List<Shipment> = entries.filter { it.step == step }.map { it.entity as Shipment }

    fun storedStatusesAt(step: String): List<String?> = entries.filter { it.step == step }.map { it.storedStatus }

    fun clear() = recorded.clear()
}

/**
 * Records the entity it gets and returns the result of [transform]. With [observeStoredRow], it also records the status
 * currently stored for the entity's code, or `null` if there is no such row.
 */
abstract class RecordingShipmentCallback(
    private val journal: CallbackJournal,
    private val step: String,
    private val observeStoredRow: Boolean = false,
) {
    var transform: (Shipment) -> Shipment = { it }

    protected fun record(entity: Shipment, table: Table): Shipment {
        val storedStatus = if (observeStoredRow) {
            Shipments.select(Shipments.status).where { Shipments.code eq entity.code }.singleOrNull()?.get(Shipments.status)
        } else {
            null
        }
        journal.record(step, entity, table, storedStatus)
        return transform(entity)
    }

    fun reset() {
        transform = { it }
    }
}

@Order(1)
class FirstBeforeConvertCallback(journal: CallbackJournal) :
    RecordingShipmentCallback(journal, BEFORE_CONVERT_1), BeforeConvertCallback<Shipment> {
    override fun onBeforeConvert(entity: Shipment, table: Table): Shipment = record(entity, table)
}

@Order(2)
class SecondBeforeConvertCallback(journal: CallbackJournal) :
    RecordingShipmentCallback(journal, BEFORE_CONVERT_2), BeforeConvertCallback<Shipment> {
    override fun onBeforeConvert(entity: Shipment, table: Table): Shipment = record(entity, table)
}

class RecordingBeforeSaveCallback(journal: CallbackJournal) :
    RecordingShipmentCallback(journal, BEFORE_SAVE, observeStoredRow = true), BeforeSaveCallback<Shipment> {
    override fun onBeforeSave(entity: Shipment, table: Table): Shipment = record(entity, table)
}

class RecordingAfterSaveCallback(journal: CallbackJournal) :
    RecordingShipmentCallback(journal, AFTER_SAVE, observeStoredRow = true), AfterSaveCallback<Shipment> {
    override fun onAfterSave(entity: Shipment, table: Table): Shipment = record(entity, table)
}

class RecordingAfterConvertCallback(journal: CallbackJournal) :
    RecordingShipmentCallback(journal, AFTER_CONVERT), AfterConvertCallback<Shipment> {
    override fun onAfterConvert(entity: Shipment, table: Table): Shipment = record(entity, table)
}

class RecordingBeforeDeleteCallback(journal: CallbackJournal) :
    RecordingShipmentCallback(journal, BEFORE_DELETE, observeStoredRow = true), BeforeDeleteCallback<Shipment> {
    override fun onBeforeDelete(entity: Shipment, table: Table): Shipment = record(entity, table)
}

class RecordingAfterDeleteCallback(journal: CallbackJournal) :
    RecordingShipmentCallback(journal, AFTER_DELETE, observeStoredRow = true), AfterDeleteCallback<Shipment> {
    override fun onAfterDelete(entity: Shipment, table: Table): Shipment = record(entity, table)
}

/** Callbacks for another entity type, which must never be invoked for [Shipment]s. */
class TicketBeforeConvertCallback(private val journal: CallbackJournal) : BeforeConvertCallback<Ticket> {
    override fun onBeforeConvert(entity: Ticket, table: Table): Ticket = entity.also { journal.record("${TICKET_PREFIX}BeforeConvert", it, table) }
}

class TicketAfterConvertCallback(private val journal: CallbackJournal) : AfterConvertCallback<Ticket> {
    override fun onAfterConvert(entity: Ticket, table: Table): Ticket = entity.also { journal.record("${TICKET_PREFIX}AfterConvert", it, table) }
}

/** Records every [ExposedEntityEvent] under its class name, e.g. `BeforeSaveEvent`. */
open class RecordingEntityEventListener(private val journal: CallbackJournal) {
    @EventListener
    open fun on(event: ExposedEntityEvent<*>) {
        val step = when (event) {
            is BeforeConvertEvent<*> -> "BeforeConvertEvent"
            is BeforeSaveEvent<*> -> "BeforeSaveEvent"
            is AfterSaveEvent<*> -> "AfterSaveEvent"
            is AfterConvertEvent<*> -> "AfterConvertEvent"
            is BeforeDeleteEvent<*> -> "BeforeDeleteEvent"
            is AfterDeleteEvent<*> -> "AfterDeleteEvent"
            else -> error("Unexpected event $event")
        }
        journal.record(step, event.entity, event.table)
    }
}

object Shipments : LongIdTable("shipments") {
    val code = varchar("code", 64)
    val destination = varchar("destination", 255)
    val status = varchar("status", 32)
}

data class Shipment(
    val id: Long? = null,
    val code: String,
    val destination: String,
    val status: String = "NEW",
)

data class ShipmentCode(val code: String)

interface ShipmentDestinationView {
    val destination: String
}
