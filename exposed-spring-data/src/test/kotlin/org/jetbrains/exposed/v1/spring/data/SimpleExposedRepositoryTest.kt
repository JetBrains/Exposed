package org.jetbrains.exposed.v1.spring.data

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertNotNull
import org.junit.jupiter.api.assertNull
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
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
}

/** Only the inherited base methods, no derived queries. */
@ExposedRepository(table = Customers::class)
interface CrudCustomerRepository : ExposedCrudRepository<Customer, Long>
