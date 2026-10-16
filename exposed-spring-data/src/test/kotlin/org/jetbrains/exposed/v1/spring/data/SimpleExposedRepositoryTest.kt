package org.jetbrains.exposed.v1.spring.data

import org.jetbrains.exposed.v1.core.SqlLogger
import org.jetbrains.exposed.v1.core.Transaction
import org.jetbrains.exposed.v1.core.statements.Statement
import org.jetbrains.exposed.v1.core.statements.StatementContext
import org.jetbrains.exposed.v1.core.statements.StatementType
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager
import org.jetbrains.exposed.v1.spring.data.mapping.ExposedMappingContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertNotNull
import org.junit.jupiter.api.assertNull
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.dao.IncorrectUpdateSemanticsDataAccessException
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.data.repository.findByIdOrNull

/**
 * The base repository methods implemented by [org.jetbrains.exposed.v1.spring.data.repository.SimpleExposedRepository]:
 * CRUD, `findAll(Sort)` and `findAll(Pageable)`.
 */
open class SimpleExposedRepositoryTest : ExposedSpringDataTestBase() {

    @Autowired
    override lateinit var repository: CrudCustomerRepository

    // ---- CRUD ----

    @Test
    fun testSaveAssignsIdAndFindByIdReturnsIt() {
        val saved = repository.save(Customer(name = "Ann", email = "ann@x.io"))
        assertNotNull(saved.id)
        assertEquals(saved, repository.findByIdOrNull(saved.id))
    }

    @Test
    fun testFindByIdReturnsNullWhenMissing() {
        assertNull(repository.findByIdOrNull(42L))
    }

    @Autowired
    private lateinit var mappingContext: ExposedMappingContext

    @Test
    fun testRepositoriesShareTheMappingContextBean() {
        assertTrue(mappingContext.hasPersistentEntityFor(Customer::class.java))
    }

    @Test
    fun testExistsById() {
        val ann = seed().first()
        assertTrue(repository.existsById(ann.id!!))
        assertFalse(repository.existsById(-1L))
    }

    @Test
    fun testFindAllById() {
        val (ann, _, carla) = seed()
        assertEquals(listOf("Ann", "Carla"), repository.findAllById(listOf(ann.id!!, carla.id!!, -1L, ann.id)).names())
        assertTrue(repository.findAllById(emptyList()).isEmpty())
    }

    @Test
    fun testDeleteAllByIdAndEntities() {
        val (ann, bob, carla) = seed()
        repository.deleteAllById(listOf(ann.id!!, -1L))
        repository.deleteAll(listOf(bob))
        repository.deleteAllById(emptyList())
        repository.deleteAll(emptyList())
        assertEquals(listOf("Carla", "Dan"), repository.findAll().names())
        assertNull(repository.findByIdOrNull(ann.id))
        assertNotNull(repository.findByIdOrNull(carla.id!!))
    }

    // ---- saveAll ----

    @Test
    fun testSaveAllWithNoEntitiesRunsNoStatement() {
        val statements = captureStatements { assertTrue(repository.saveAll(emptyList()).isEmpty()) }
        assertTrue(statements.isEmpty()) { "Expected no statements, got $statements" }
    }

    @Test
    fun testSaveAllInsertsNewEntitiesInOneBatchAndReadsThemBackInOneQuery() {
        val statements = captureStatements { seed() }
        // the logger is called once per batched row, so statements are told apart by instance
        val inserts = statements.filter { it.type == StatementType.INSERT }
        assertEquals(4, inserts.size)
        assertEquals(1, inserts.distinct().size) { "Expected one batch insert, got $inserts" }
        assertEquals(1, statements.count { it.type == StatementType.SELECT }) { "Expected one select, got $statements" }
    }

    @Test
    fun testSaveAllReturnsEntitiesInInputOrderWithGeneratedIds() {
        val saved = seed()
        assertEquals(listOf("Ann", "Bob", "Carla", "Dan"), saved.orderedNames())
        assertEquals(4, saved.mapNotNull { it.id }.distinct().size)
        saved.forEach { assertEquals(it, repository.findByIdOrNull(it.id!!)) }
    }

    @Test
    fun testSaveAllWithNewAndExistingEntitiesKeepsInputOrder() {
        val (ann, bob) = seed()
        val saved = repository.saveAll(
            listOf(
                Customer(name = "Eve", email = "eve@x.io"),
                bob.copy(age = 31),
                Customer(name = "Finn", email = "finn@x.io"),
                ann.copy(nickname = "ann"),
            )
        )
        assertEquals(listOf("Eve", "Bob", "Finn", "Ann"), saved.orderedNames())
        assertEquals(bob.id, saved[1].id)
        assertEquals(31, saved[1].age)
        assertEquals("ann", saved[3].nickname)
        assertEquals(6L, repository.count())
    }

    @Test
    fun testSaveAllWithSameExistingEntityTwiceKeepsLastState() {
        val ann = seed().first()
        val saved = repository.saveAll(listOf(ann.copy(age = 1), ann.copy(age = 2)))
        assertEquals(listOf(2, 2), saved.map { it.age })
        assertEquals(2, repository.findByIdOrNull(ann.id!!)?.age)
    }

    @Test
    fun testSaveAllWithMissingExistingEntityFails() {
        assertThrows<IncorrectUpdateSemanticsDataAccessException> {
            repository.saveAll(listOf(Customer(id = -1L, name = "Ghost", email = "ghost@x.io")))
        }
    }

    // ---- findAll(Pageable) ----

    @Test
    fun testFindAllWithPageable() {
        seed()
        val page = repository.findAll(PageRequest.of(0, 3, Sort.by("age")))
        assertEquals(listOf("Ann", "Bob", "Carla"), page.content.orderedNames())
        assertEquals(4L, page.totalElements)
        assertEquals(2, page.totalPages)
        assertEquals(Sort.by("age"), page.sort)
        assertTrue(page.hasNext())
    }

    @Test
    fun testFindAllWithPageableOnLastFullPage() {
        seed()
        val page = repository.findAll(PageRequest.of(1, 2, Sort.by("age")))
        assertEquals(listOf("Carla", "Dan"), page.content.orderedNames())
        assertEquals(4L, page.totalElements)
        assertTrue(page.isLast)
    }

    @Test
    fun testFindAllWithPageableDescending() {
        seed()
        assertEquals(listOf("Bob", "Ann"), repository.findAll(PageRequest.of(1, 2, Sort.by("age").descending())).content.orderedNames())
    }

    @Test
    fun testFindAllWithPageableOnEmptyTable() {
        val page = repository.findAll(PageRequest.of(0, 2))
        assertTrue(page.content.isEmpty())
        assertEquals(0L, page.totalElements)
        assertEquals(0, page.totalPages)
    }

    @Test
    fun testFindAllUnpaged() {
        seed()
        val page = repository.findAll(Pageable.unpaged())
        assertEquals(4, page.numberOfElements)
        assertEquals(4L, page.totalElements)
        assertEquals(1, page.totalPages)
    }

    // ---- findAll(Sort) ----

    @Test
    fun testFindAllWithSort() {
        seed()
        assertEquals(listOf("Dan", "Carla", "Bob", "Ann"), repository.findAll(Sort.by("age").descending()).orderedNames())
        assertEquals(listOf("Bob", "Dan", "Ann", "Carla"), repository.findAll(Sort.by("active", "age")).orderedNames())
    }

    @Test
    fun testFindAllWithUnknownSortPropertyFails() {
        seed()
        assertThrows<Exception> { repository.findAll(Sort.by("doesNotExist")) }
        assertThrows<Exception> { repository.findAll(PageRequest.of(0, 2, Sort.by("doesNotExist"))) }
    }

    /**
     * Runs [block] and returns the statement of every execution in the current (test) transaction.
     * A batch statement appears once per batched row, as the same instance.
     */
    private fun captureStatements(block: () -> Unit): List<Statement<*>> {
        val statements = mutableListOf<Statement<*>>()
        TransactionManager.current().addLogger(
            object : SqlLogger {
                override fun log(context: StatementContext, transaction: Transaction) {
                    statements += context.statement
                }
            }
        )
        block()
        return statements
    }
}

/** Only the inherited base methods, no derived queries. */
@ExposedRepository(table = Customers::class)
interface CrudCustomerRepository : ExposedCrudRepository<Customer, Long>
