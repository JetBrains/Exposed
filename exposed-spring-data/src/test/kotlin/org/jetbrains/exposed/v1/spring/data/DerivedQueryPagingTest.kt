package org.jetbrains.exposed.v1.spring.data

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Slice
import org.springframework.data.domain.Sort

/** Derived queries with a [Pageable] parameter, returning [Page], [Slice] or [List]. */
open class DerivedQueryPagingTest : ExposedSpringDataTestBase() {

    @Autowired
    override lateinit var repository: PagingCustomerRepository

    // ---- Page ----

    @Test
    fun testPage() {
        seed()
        val first = repository.findPageByAgeGreaterThan(0, PageRequest.of(0, 3, Sort.by("age")))
        assertEquals(listOf("Ann", "Bob", "Carla"), first.content.orderedNames())
        assertEquals(4L, first.totalElements)
        assertEquals(2, first.totalPages)
        assertTrue(first.hasNext())

        val second = repository.findPageByAgeGreaterThan(0, PageRequest.of(1, 3, Sort.by("age")))
        assertEquals(listOf("Dan"), second.content.orderedNames())
        assertEquals(4L, second.totalElements)
        assertFalse(second.hasNext())
    }

    @Test
    fun testPageTotalCountsOnlyMatchingRows() {
        seed()
        val page = repository.findByNameContainingIgnoreCase("A", PageRequest.of(0, 2, Sort.by("name")))
        assertEquals(listOf("Ann", "Carla"), page.content.orderedNames())
        assertEquals(3L, page.totalElements)
        assertEquals(2, page.totalPages)
    }

    @Test
    fun testPageBeyondLastPageIsEmpty() {
        seed()
        val page = repository.findPageByAgeGreaterThan(0, PageRequest.of(5, 3))
        assertTrue(page.content.isEmpty())
        assertEquals(4L, page.totalElements)
    }

    @Test
    fun testUnpagedPageReturnsEverything() {
        seed()
        val page = repository.findPageByAgeGreaterThan(0, Pageable.unpaged())
        assertEquals(listOf("Ann", "Bob", "Carla", "Dan"), page.content.names())
        assertEquals(4L, page.totalElements)
    }

    @Test
    fun testPageableSortCombinedWithOrderBy() {
        seed()
        // static OrderBy first, then the Pageable's sort
        val page = repository.findByAgeGreaterThanOrderByActiveAsc(0, PageRequest.of(0, 4, Sort.by("age").descending()))
        assertEquals(listOf("Dan", "Bob", "Carla", "Ann"), page.content.orderedNames())
    }

    @Test
    fun testPageMetadata() {
        seed()
        val page = repository.findPageByAgeGreaterThan(0, PageRequest.of(0, 3, Sort.by("age")))
        assertEquals(0, page.number)
        assertEquals(3, page.size)
        assertEquals(3, page.numberOfElements)
        assertEquals(Sort.by("age"), page.sort)
        assertTrue(page.isFirst)
        assertFalse(page.isLast)
        assertFalse(page.hasPrevious())
    }

    @Test
    fun testLastFullPageStillCountsTotal() {
        seed()
        // the page is full, so the total can't be derived from the content and needs the count query
        val page = repository.findPageByAgeGreaterThan(0, PageRequest.of(1, 2, Sort.by("age")))
        assertEquals(listOf("Carla", "Dan"), page.content.orderedNames())
        assertEquals(4L, page.totalElements)
        assertEquals(2, page.totalPages)
        assertTrue(page.isLast)
        assertFalse(page.hasNext())
        assertTrue(page.hasPrevious())
    }

    @Test
    fun testIteratingAllPagesWithNextPageable() {
        seed()
        val names = mutableListOf<String>()
        var pages = 0
        var page = repository.findPageByAgeGreaterThan(0, PageRequest.of(0, 3, Sort.by("age")))
        while (true) {
            pages++
            names += page.content.orderedNames()
            if (!page.hasNext()) break
            page = repository.findPageByAgeGreaterThan(0, page.nextPageable())
        }
        assertEquals(2, pages)
        assertEquals(listOf("Ann", "Bob", "Carla", "Dan"), names)
    }

    @Test
    fun testPageOnEmptyTable() {
        val page = repository.findPageByAgeGreaterThan(0, PageRequest.of(0, 2))
        assertTrue(page.content.isEmpty())
        assertEquals(0L, page.totalElements)
        assertEquals(0, page.totalPages)
        assertFalse(page.hasNext())
    }

    @Test
    fun testPageWithNoMatches() {
        seed()
        val page = repository.findPageByAgeGreaterThan(100, PageRequest.of(0, 2))
        assertTrue(page.content.isEmpty())
        assertEquals(0L, page.totalElements)
        assertEquals(0, page.totalPages)
    }

    @Test
    fun testPageableSortByUnknownPropertyFails() {
        seed()
        assertThrows<Exception> { repository.findPageByAgeGreaterThan(0, PageRequest.of(0, 2, Sort.by("doesNotExist"))) }
    }

    // ---- Slice ----

    @Test
    fun testSlice() {
        seed()
        val first = repository.findSliceByAgeGreaterThan(0, PageRequest.of(0, 3, Sort.by("age")))
        assertEquals(listOf("Ann", "Bob", "Carla"), first.content.orderedNames())
        assertTrue(first.hasNext())

        val second = repository.findSliceByAgeGreaterThan(0, PageRequest.of(1, 3, Sort.by("age")))
        assertEquals(listOf("Dan"), second.content.orderedNames())
        assertFalse(second.hasNext())
    }

    @Test
    fun testSliceUnpaged() {
        seed()
        val slice = repository.findSliceByAgeGreaterThan(0, Pageable.unpaged())
        assertEquals(listOf("Ann", "Bob", "Carla", "Dan"), slice.content.names())
        assertFalse(slice.hasNext())
    }

    @Test
    fun testSliceOnExactBoundaryHasNoNext() {
        seed()
        val last = repository.findSliceByAgeGreaterThan(0, PageRequest.of(1, 2, Sort.by("age")))
        assertEquals(listOf("Carla", "Dan"), last.content.orderedNames())
        assertFalse(last.hasNext())
        assertTrue(last.hasPrevious())

        val all = repository.findSliceByAgeGreaterThan(0, PageRequest.of(0, 4, Sort.by("age")))
        assertEquals(4, all.numberOfElements)
        assertFalse(all.hasNext())
    }

    @Test
    fun testIteratingAllSlicesWithNextPageable() {
        seed()
        val names = mutableListOf<String>()
        var slice = repository.findSliceByAgeGreaterThan(0, PageRequest.of(0, 3, Sort.by("age")))
        names += slice.content.orderedNames()
        while (slice.hasNext()) {
            slice = repository.findSliceByAgeGreaterThan(0, slice.nextPageable())
            names += slice.content.orderedNames()
        }
        assertEquals(listOf("Ann", "Bob", "Carla", "Dan"), names)
    }

    // ---- List ----

    @Test
    fun testListWithPageable() {
        seed()
        assertEquals(
            listOf("Carla", "Dan"),
            repository.findListByAgeGreaterThan(0, PageRequest.of(1, 2, Sort.by("age"))).orderedNames()
        )
    }
}

@ExposedRepository(table = Customers::class)
interface PagingCustomerRepository : ExposedCrudRepository<Customer, Long> {
    fun findPageByAgeGreaterThan(age: Int, pageable: Pageable): Page<Customer>
    fun findByNameContainingIgnoreCase(name: String, pageable: Pageable): Page<Customer>
    fun findSliceByAgeGreaterThan(age: Int, pageable: Pageable): Slice<Customer>
    fun findListByAgeGreaterThan(age: Int, pageable: Pageable): List<Customer>
    fun findByAgeGreaterThanOrderByActiveAsc(age: Int, pageable: Pageable): Page<Customer>
}
