package org.jetbrains.exposed.v1.spring.data

import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greater
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager
import org.jetbrains.exposed.v1.jdbc.update
import org.jetbrains.exposed.v1.spring.data.query.query
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

/**
 * Custom queries: Kotlin default methods on the repository interface that call `query { }` with an Exposed DSL block.
 */
open class CustomQueryTest : ExposedSpringDataTestBase() {
    @Autowired
    override lateinit var repository: CustomQueryRepository

    @Autowired
    lateinit var secondaryRepository: SecondaryCustomQueryRepository

    @Autowired
    lateinit var crudRepository: CrudCustomerRepository

    // ---- List results ----

    @Test
    fun testCustomQuery() {
        seed()
        val customers = repository.customQuery()
        assert(customers.size == 3)
        assert(customers.all { it.age >= 30 })
        val first = customers.first()
        assert(first.name == "Bob")
        val second = customers[1]
        assert(second.name == "Carla")
        val third = customers.last()
        assert(third.name == "Dan")
    }

    @Test
    fun testListResultMapsAllProperties() {
        val seeded = seed()
        assertEquals(seeded.filter { it.age >= 30 }.sortedBy { it.id }, repository.customQuery().sortedBy { it.id })
    }

    @Test
    fun testListResultMapsNullableColumn() {
        seed()
        val bob = repository.customQuery().single { it.name == "Bob" }
        assertEquals(null, bob.nickname)
    }

    @Test
    fun testListResultEmptyTable() {
        assertTrue(repository.customQuery().isEmpty())
    }

    @Test
    fun testListResultNoMatchingRows() {
        seed()
        assertTrue(repository.olderThan(100).isEmpty())
    }

    @Test
    fun testListResultWithParameter() {
        seed()
        assertEquals(listOf("Carla", "Dan"), repository.olderThan(30).names())
        assertEquals(listOf("Ann", "Bob", "Carla", "Dan"), repository.olderThan(0).names())
    }

    @Test
    fun testListResultWithMultipleParameters() {
        seed()
        assertEquals(listOf("Carla"), repository.activeAndOlderThan(active = true, age = 30).names())
        assertEquals(listOf("Bob", "Dan"), repository.activeAndOlderThan(active = false, age = 18).names())
    }

    @Test
    fun testListResultKeepsOrderOfQuery() {
        seed()
        assertEquals(listOf("Dan", "Carla", "Bob", "Ann"), repository.allByAgeDescending().orderedNames())
    }

    @Test
    fun testListResultWithLimitAndOffset() {
        seed()
        assertEquals(listOf("Bob", "Carla"), repository.pageByAge(limit = 2, offset = 1).orderedNames())
        assertTrue(repository.pageByAge(limit = 2, offset = 10).isEmpty())
    }

    // ---- Iterable results (a lazy Query is returned without toList()) ----

    @Test
    fun testIterableResultFromLazyQuery() {
        seed()
        assertEquals(listOf("Ann", "Bob"), repository.youngerThanLazy(35).names())
    }

    @Test
    fun testIterableResultIsMaterialisedInsideTheTransaction() {
        seed()
        val result = repository.youngerThanLazy(35)
        // the rows must already be read; iterating again must neither re-run the query nor fail
        assertEquals(result.toList(), result.toList())
        assertEquals(2, result.count())
    }

    @Test
    fun testIterableResultEmpty() {
        seed()
        assertTrue(repository.youngerThanLazy(0).none())
    }

    // ---- Single results ----

    @Test
    fun testSingleResult() {
        val seeded = seed()
        assertEquals(seeded.single { it.email == "carla@example.com" }, repository.byEmail("carla@example.com"))
    }

    @Test
    fun testSingleResultMissingRowFails() {
        seed()
        assertThrows<NoSuchElementException> { repository.byEmail("nobody@x.io") }
    }

    @Test
    fun testSingleResultMoreThanOneRowFails() {
        seed()
        assertThrows<IllegalArgumentException> { repository.singleOlderThan(18) }
    }

    @Test
    fun testSingleResultWithOrderAndLimit() {
        seed()
        assertEquals("Dan", repository.oldest().name)
    }

    // ---- Combining with other repository methods ----

    @Test
    fun testCustomQueryCallingCrudMethodFirst() {
        seed()
        // average age is 32.5 -> Carla (35) and Dan (40)
        assertEquals(listOf("Carla", "Dan"), repository.olderThanAverage().names())
    }

    @Test
    fun testCustomQueryCallingDerivedQueryFirst() {
        seed()
        assertEquals(listOf("Carla", "Dan"), repository.olderThanAgeOf("Bob").names())
    }

    @Test
    fun testCustomQueryCallingAnotherCustomQueryOnSameRepository() {
        seed()
        assertEquals(listOf("Ann", "Bob", "Carla", "Dan"), repository.youngAndOld().names())
    }

    @Test
    fun testCustomQueryCallingCustomQueryOfAnotherRepository() {
        seed()
        val (inner, outer) = repository.combinedWith(secondaryRepository)
        assertEquals(listOf("Ann"), inner.names())
        assertEquals(listOf("Bob", "Carla", "Dan"), outer.names())
    }

    @Test
    fun testCustomQueryWithoutQueryHelper() {
        seed()
        assertEquals(4L, repository.countWithoutHelper())
    }

    // ---- Misuse ----

    @Test
    fun testQueryOutsideRepositoryMethodFails() {
        seed()
        val error = assertThrows<IllegalStateException> {
            val result: List<Customer> = repository.query { Customers.selectAll().toList() }
            result
        }
        assertTrue(error.message!!.contains("default method"))
    }

    @Test
    fun testQueryOutsideRepositoryMethodFailsAfterCustomQuery() {
        seed()
        repository.customQuery()
        // the context of the previous call must not leak into code outside a repository method
        assertThrows<IllegalStateException> {
            val result: List<Customer> = repository.query { Customers.selectAll().toList() }
            result
        }
    }

    @Test
    fun testQueryOutsideRepositoryMethodFailsAfterFailedCustomQuery() {
        seed()
        assertThrows<IllegalStateException> { repository.failingQuery() }
        assertThrows<IllegalStateException> {
            val result: List<Customer> = repository.query { Customers.selectAll().toList() }
            result
        }
    }

    @Test
    fun testCustomQueryStillWorksAfterFailedCustomQuery() {
        seed()
        assertThrows<IllegalStateException> { repository.failingQuery() }
        assertEquals(listOf("Bob", "Carla", "Dan"), repository.customQuery().names())
    }

    @Test
    fun testQueryOnDifferentRepositoryInsideCustomQueryFails() {
        seed()
        val error = assertThrows<IllegalStateException> { repository.queryVia(crudRepository) }
        assertTrue(error.message!!.contains("different repository"))
    }

    @Test
    fun testPartialSelectFailsToMap() {
        seed()
        assertThrows<Exception> { repository.namesOnly() }
    }

    @Test
    fun testQueryInAnotherThreadIsIsolated() {
        seed()
        var failure: Throwable? = null
        val thread = Thread {
            failure = runCatching {
                val result: List<Customer> = repository.query { Customers.selectAll().toList() }
                result
            }.exceptionOrNull()
        }
        repository.customQuery()
        thread.start()
        thread.join()
        assertTrue(failure is IllegalStateException, "expected IllegalStateException, got $failure")
    }

    // ---- Transactions ----

    @Test
    fun testWriteInsideTransactionalCustomQuery() {
        seed()
        val deactivated = repository.deactivateOlderThan(30)
        assertEquals(listOf("Bob", "Carla", "Dan"), deactivated.names())
        assertTrue(deactivated.none { it.active })
        assertEquals(listOf("Bob", "Carla", "Dan"), crudRepository.findAll().filter { !it.active }.names())
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    fun testCustomQueryOpensItsOwnTransaction() {
        seed()
        assertEquals(listOf("Bob", "Carla", "Dan"), repository.customQuery().names())
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    fun testCustomQueryRunsReadOnlyByDefault() {
        assertTrue(repository.isReadOnlyTransaction())
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    fun testTransactionalCustomQueryIsNotReadOnly() {
        assertFalse(repository.isReadOnlyTransactionWhenTransactional())
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    fun testWriteInsideTransactionalCustomQueryIsCommitted() {
        seed()
        repository.deactivateOlderThan(30)
        assertEquals(listOf("Bob", "Carla", "Dan"), crudRepository.findAll().filter { !it.active }.names())
    }
}

@ExposedRepository(table = Customers::class)
interface CustomQueryRepository : ExposedCrudRepository<Customer, Long> {
    fun findByName(name: String): Customer?

    fun customQuery(): List<Customer> = query {
        Customers.selectAll()
            .where { Customers.age greaterEq 30 }
            .toList()
    }

    fun olderThan(age: Int): List<Customer> = query {
        Customers.selectAll().where { Customers.age greater age }.toList()
    }

    fun activeAndOlderThan(active: Boolean, age: Int): List<Customer> = query {
        Customers.selectAll().where { (Customers.active eq active) and (Customers.age greater age) }.toList()
    }

    fun allByAgeDescending(): List<Customer> = query {
        Customers.selectAll().orderBy(Customers.age, SortOrder.DESC).toList()
    }

    fun pageByAge(limit: Int, offset: Long): List<Customer> = query {
        Customers.selectAll().orderBy(Customers.age).limit(limit).offset(offset).toList()
    }

    fun youngerThanLazy(age: Int): Iterable<Customer> = query {
        Customers.selectAll().where { Customers.age less age }
    }

    fun byEmail(email: String): Customer = query {
        Customers.selectAll().where { Customers.email eq email }.single()
    }

    fun singleOlderThan(age: Int): Customer = query {
        Customers.selectAll().where { Customers.age greater age }.single()
    }

    fun oldest(): Customer = query {
        Customers.selectAll().orderBy(Customers.age, SortOrder.DESC).limit(1).single()
    }

    fun olderThanAverage(): List<Customer> {
        val average = findAll().map { it.age }.average()
        return query { Customers.selectAll().where { Customers.age greater average.toInt() }.toList() }
    }

    fun olderThanAgeOf(name: String): List<Customer> {
        val reference = checkNotNull(findByName(name))
        return olderThan(reference.age)
    }

    fun youngAndOld(): List<Customer> {
        val young: List<Customer> = query { Customers.selectAll().where { Customers.age less 30 }.toList() }
        val old = customQuery()
        val again: List<Customer> = query { Customers.selectAll().where { Customers.age less 30 }.toList() }
        check(young == again)
        return young + old
    }

    fun combinedWith(other: SecondaryCustomQueryRepository): Pair<List<Customer>, List<Customer>> {
        val inner = other.youngest()
        // the context of this repository must be restored after the nested call returns
        val outer: List<Customer> = query { Customers.selectAll().where { Customers.age greaterEq 30 }.toList() }
        return inner to outer
    }

    fun countWithoutHelper(): Long = Customers.selectAll().count()

    fun failingQuery(): List<Customer> = query {
        check(Customers.selectAll().empty()) { "boom" }
        Customers.selectAll().toList()
    }

    fun queryVia(other: CrudCustomerRepository): List<Customer> = other.query { Customers.selectAll().toList() }

    fun namesOnly(): List<Customer> = query { Customers.select(Customers.name).toList() }

    fun isReadOnlyTransaction(): Boolean = checkNotNull(TransactionManager.currentOrNull()).readOnly

    @Transactional
    fun isReadOnlyTransactionWhenTransactional(): Boolean = checkNotNull(TransactionManager.currentOrNull()).readOnly

    @Transactional
    fun deactivateOlderThan(age: Int): List<Customer> = query {
        Customers.update({ Customers.age greaterEq age }) { it[Customers.active] = false }
        Customers.selectAll().where { Customers.age greaterEq age }.toList()
    }
}

/** A second repository with a custom query, to test nested calls across repositories. */
@ExposedRepository(table = Customers::class)
interface SecondaryCustomQueryRepository : ExposedCrudRepository<Customer, Long> {
    fun youngest(): List<Customer> = query {
        Customers.selectAll().orderBy(Customers.age).limit(1).toList()
    }
}
