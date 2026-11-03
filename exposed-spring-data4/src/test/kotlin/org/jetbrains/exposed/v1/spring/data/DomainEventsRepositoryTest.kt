package org.jetbrains.exposed.v1.spring.data

import org.jetbrains.exposed.v1.core.dao.id.LongIdTable
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.spring7.transaction.SpringTransactionManager
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.FilterType
import org.springframework.context.event.EventListener
import org.springframework.data.annotation.Transient
import org.springframework.data.domain.AfterDomainEventPublication
import org.springframework.data.domain.DomainEvents
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.annotation.EnableTransactionManagement
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.event.TransactionalEventListener
import org.springframework.transaction.support.TransactionTemplate
import java.util.concurrent.CopyOnWriteArrayList
import javax.sql.DataSource

/**
 * Domain events of aggregates with [DomainEvents] and [AfterDomainEventPublication], as published by Spring Data's
 * `EventPublishingRepositoryProxyPostProcessor`, which every Exposed repository gets through `RepositoryFactoryBeanSupport`:
 * - only `save*`, `delete`, `deleteAll(entities)` (and the `*InBatch` variants) with one argument publish events,
 * - the events come from the entities **passed in**, not from the returned ones, and are published after the call returned,
 * - the clean-up method is called on each passed entity after its events were published,
 * - `deleteById` gets only an id, so it can't publish anything,
 * - the repository's transaction advice wraps the publication, so a [TransactionalEventListener] sees the events once that
 *   transaction (or the surrounding one it joined) commits, and never on rollback.
 *
 * Runs in its own context with a nested repository, so the shared [SpringDataExposedTestConfiguration] doesn't pick it up.
 */
@SpringJUnitConfig(DomainEventsTestConfiguration::class)
@Transactional
open class DomainEventsRepositoryTest {

    @Autowired
    lateinit var tm: PlatformTransactionManager

    @Autowired
    lateinit var tickets: TicketRepository

    @Autowired
    lateinit var listener: RecordingTicketEventListener

    @Autowired
    lateinit var afterCommitListener: AfterCommitTicketEventListener

    @BeforeEach
    fun createTables() {
        TransactionTemplate(tm).executeWithoutResult { SchemaUtils.create(Tickets) }
        listener.clear()
        afterCommitListener.clear()
    }

    @AfterEach
    fun dropTables() = TransactionTemplate(tm).executeWithoutResult { SchemaUtils.drop(Tickets) }

    // ---- save ----

    @Test
    fun testSavePublishesEventsOfThePassedEntity() {
        val ticket = Ticket(title = "Login broken").registerEvent(TicketOpened("Login broken"))

        tickets.save(ticket)

        assertEquals(listOf(TicketOpened("Login broken")), listener.events)
    }

    @Test
    fun testSavePublishesEventsOfOneEntityInRegistrationOrder() {
        val ticket = Ticket(title = "Login broken")
            .registerEvent(TicketOpened("Login broken"))
            .registerEvent(TicketAssigned("Login broken", assignee = "ada"))

        tickets.save(ticket)

        assertEquals(listOf(TicketOpened("Login broken"), TicketAssigned("Login broken", assignee = "ada")), listener.events)
    }

    @Test
    fun testSaveCallsCleanUpMethodOnThePassedEntity() {
        val ticket = Ticket(title = "Login broken").registerEvent(TicketOpened("Login broken"))

        tickets.save(ticket)

        assertTrue(ticket.pendingEvents().isEmpty())
    }

    @Test
    fun testSavingTheSameEntityAgainDoesNotRepublishClearedEvents() {
        val ticket = Ticket(title = "Login broken").registerEvent(TicketOpened("Login broken"))

        tickets.save(ticket)
        tickets.save(ticket)

        assertEquals(listOf(TicketOpened("Login broken")), listener.events)
    }

    @Test
    fun testSaveReturnsEntityWithoutPendingEvents() {
        val ticket = Ticket(title = "Login broken").registerEvent(TicketOpened("Login broken"))

        val saved = tickets.save(ticket)

        // the returned entity is read back from the table, so it never carries the events of the passed one
        assertTrue(saved.pendingEvents().isEmpty())
    }

    @Test
    fun testSaveOfUpdatedEntityPublishesItsEvents() {
        val saved = tickets.save(Ticket(title = "Login broken"))
        val closed = saved.copy(status = "CLOSED").registerEvent(TicketClosed("Login broken"))

        tickets.save(closed)

        assertEquals(listOf(TicketClosed("Login broken")), listener.events)
        assertTrue(closed.pendingEvents().isEmpty())
    }

    @Test
    fun testSaveWithoutEventsPublishesNothing() {
        tickets.save(Ticket(title = "Login broken"))

        assertTrue(listener.events.isEmpty())
    }

    @Test
    fun testEventsArePublishedAfterTheRowWasStored() {
        listener.onEvent = { listener.observedRowCounts += tickets.count() }

        tickets.save(Ticket(title = "Login broken").registerEvent(TicketOpened("Login broken")))

        assertEquals(listOf(1L), listener.observedRowCounts)
    }

    // ---- saveAll ----

    @Test
    fun testSaveAllPublishesEventsOfAllPassedEntities() {
        val a = Ticket(title = "A").registerEvent(TicketOpened("A"))
        val b = Ticket(title = "B").registerEvent(TicketOpened("B")).registerEvent(TicketAssigned("B", assignee = "ada"))

        tickets.saveAll(listOf(a, b))

        assertEquals(setOf(TicketOpened("A"), TicketOpened("B"), TicketAssigned("B", assignee = "ada")), listener.events.toSet())
        assertEquals(3, listener.events.size)
    }

    @Test
    fun testSaveAllCallsCleanUpMethodOnEachPassedEntity() {
        val a = Ticket(title = "A").registerEvent(TicketOpened("A"))
        val b = Ticket(title = "B").registerEvent(TicketOpened("B"))

        tickets.saveAll(listOf(a, b))

        assertTrue(a.pendingEvents().isEmpty())
        assertTrue(b.pendingEvents().isEmpty())
    }

    @Test
    fun testSaveAllWithNewAndExistingEntitiesPublishesEventsOfBoth() {
        val existing = tickets.save(Ticket(title = "Existing"))
        val updated = existing.copy(status = "CLOSED").registerEvent(TicketClosed("Existing"))
        val created = Ticket(title = "New").registerEvent(TicketOpened("New"))

        tickets.saveAll(listOf(updated, created))

        assertEquals(setOf(TicketClosed("Existing"), TicketOpened("New")), listener.events.toSet())
        assertEquals(2L, tickets.count())
    }

    // ---- delete ----

    @Test
    fun testDeletePublishesEventsOfThePassedEntity() {
        val saved = tickets.save(Ticket(title = "Login broken"))
        val toDelete = saved.registerEvent(TicketDeleted("Login broken"))

        tickets.delete(toDelete)

        assertEquals(listOf(TicketDeleted("Login broken")), listener.events)
        assertTrue(toDelete.pendingEvents().isEmpty())
        assertFalse(tickets.existsById(saved.id!!))
    }

    @Test
    fun testDeleteAllEntitiesPublishesEventsOfAllPassedEntities() {
        val a = tickets.save(Ticket(title = "A")).registerEvent(TicketDeleted("A"))
        val b = tickets.save(Ticket(title = "B")).registerEvent(TicketDeleted("B"))
        val c = tickets.save(Ticket(title = "C"))

        tickets.deleteAll(listOf(a, b))

        assertEquals(setOf(TicketDeleted("A"), TicketDeleted("B")), listener.events.toSet())
        assertEquals(2, listener.events.size)
        assertTrue(a.pendingEvents().isEmpty())
        assertTrue(b.pendingEvents().isEmpty())
        assertEquals(listOf(c.id), tickets.findAll().map { it.id })
    }

    @Test
    fun testDeleteByIdPublishesNothing() {
        val saved = tickets.save(Ticket(title = "Login broken")).registerEvent(TicketDeleted("Login broken"))

        tickets.deleteById(saved.id!!)

        // only the id reaches the repository: the entity's events are neither published nor cleared
        assertTrue(listener.events.isEmpty())
        assertEquals(listOf(TicketDeleted("Login broken")), saved.pendingEvents())
        assertFalse(tickets.existsById(saved.id!!))
    }

    @Test
    fun testDeleteAllByIdAndDeleteAllWithoutArgumentsPublishNothing() {
        val a = tickets.save(Ticket(title = "A"))
        tickets.save(Ticket(title = "B"))

        tickets.deleteAllById(listOf(a.id!!))
        tickets.deleteAll()

        assertTrue(listener.events.isEmpty())
        assertEquals(0L, tickets.count())
    }

    // ---- transactional event listener ----

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    fun testTransactionalListenerSeesEventOnlyAfterCommit() {
        val seenBeforeCommit = TransactionTemplate(tm).execute {
            tickets.save(Ticket(title = "Login broken").registerEvent(TicketOpened("Login broken")))
            afterCommitListener.events
        }

        assertEquals(emptyList<TicketEvent>(), seenBeforeCommit)
        assertEquals(listOf(TicketOpened("Login broken")), afterCommitListener.events)
        assertEquals(listOf(TicketOpened("Login broken")), listener.events)
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    fun testTransactionalListenerSeesNothingOnRollback() {
        TransactionTemplate(tm).executeWithoutResult { status ->
            tickets.save(Ticket(title = "Login broken").registerEvent(TicketOpened("Login broken")))
            status.setRollbackOnly()
        }

        assertTrue(afterCommitListener.events.isEmpty())
        // the plain listener got the event synchronously, before the rollback
        assertEquals(listOf(TicketOpened("Login broken")), listener.events)
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    fun testTransactionalListenerSeesEventAfterRepositoryTransactionCommitted() {
        // no surrounding transaction: the repository's own transaction wraps the publication
        tickets.save(Ticket(title = "Login broken").registerEvent(TicketOpened("Login broken")))

        assertEquals(listOf(TicketOpened("Login broken")), afterCommitListener.events)
    }

    @Test
    fun testTransactionalListenerSeesNothingInsideTheTestTransaction() {
        tickets.save(Ticket(title = "Login broken").registerEvent(TicketOpened("Login broken")))

        // the test transaction is rolled back after the test, so the event is never delivered
        assertTrue(afterCommitListener.events.isEmpty())
        assertEquals(listOf(TicketOpened("Login broken")), listener.events)
    }

    @ExposedRepository(table = Tickets::class)
    interface TicketRepository : ExposedCrudRepository<Ticket, Long>
}

@Configuration
@EnableTransactionManagement
@EnableExposedRepositories(
    considerNestedRepositories = true,
    includeFilters = [
        ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = [DomainEventsRepositoryTest.TicketRepository::class])
    ],
)
open class DomainEventsTestConfiguration {
    @Bean
    open fun dataSource(): DataSource = EmbeddedDatabaseBuilder()
        .setType(EmbeddedDatabaseType.H2)
        .generateUniqueName(true)
        .build()

    @Bean
    open fun transactionManager(dataSource: DataSource): PlatformTransactionManager =
        SpringTransactionManager(dataSource)

    @Bean
    open fun recordingTicketEventListener(): RecordingTicketEventListener = RecordingTicketEventListener()

    @Bean
    open fun afterCommitTicketEventListener(): AfterCommitTicketEventListener = AfterCommitTicketEventListener()
}

/** Records every [TicketEvent] synchronously, when it is published. [onEvent] runs after recording, e.g. to look at the table. */
open class RecordingTicketEventListener {
    private val recorded = CopyOnWriteArrayList<TicketEvent>()
    val events: List<TicketEvent> get() = recorded.toList()
    val observedRowCounts: MutableList<Long> = CopyOnWriteArrayList()
    var onEvent: () -> Unit = {}

    @EventListener
    open fun on(event: TicketEvent) {
        recorded += event
        onEvent()
    }

    fun clear() {
        recorded.clear()
        observedRowCounts.clear()
        onEvent = {}
    }
}

/** Records every [TicketEvent] after the transaction it was published in committed (the default phase). */
open class AfterCommitTicketEventListener {
    private val recorded = CopyOnWriteArrayList<TicketEvent>()
    val events: List<TicketEvent> get() = recorded.toList()

    @TransactionalEventListener
    open fun on(event: TicketEvent) {
        recorded += event
    }

    fun clear() = recorded.clear()
}

object Tickets : LongIdTable("tickets") {
    val title = varchar("title", 255)
    val status = varchar("status", 32)
}

sealed interface TicketEvent

data class TicketOpened(val title: String) : TicketEvent

data class TicketAssigned(val title: String, val assignee: String) : TicketEvent

data class TicketClosed(val title: String) : TicketEvent

data class TicketDeleted(val title: String) : TicketEvent

/**
 * Immutable aggregate root whose events live in a [Transient] list outside the constructor: it has no column, isn't part of
 * `equals`, and a `copy` (like every instance read from the table) starts without events.
 */
data class Ticket(
    val id: Long? = null,
    val title: String,
    val status: String = "OPEN",
) {
    @Transient
    private val domainEvents: MutableList<TicketEvent> = mutableListOf()

    fun registerEvent(event: TicketEvent): Ticket = apply { domainEvents += event }

    fun pendingEvents(): List<TicketEvent> = domainEvents.toList()

    @DomainEvents
    fun domainEvents(): List<TicketEvent> = domainEvents.toList()

    @AfterDomainEventPublication
    fun clearDomainEvents() = domainEvents.clear()
}
