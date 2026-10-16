package org.jetbrains.exposed.v1.spring.data

import org.jetbrains.exposed.v1.spring.data.repository.ExposedRepositoryFactory
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.domain.KeysetScrollPosition
import org.springframework.data.domain.Limit
import org.springframework.data.domain.ScrollPosition
import org.springframework.data.domain.Sort
import org.springframework.data.domain.Window
import org.springframework.data.support.WindowIterator

/** Derived queries with a [ScrollPosition] parameter returning a [Window], using offset and keyset scrolling. */
open class DerivedQueryScrollTest : ExposedSpringDataTestBase() {

    @Autowired
    override lateinit var repository: ScrollCustomerRepository

    // ---- Offset ----

    @Test
    fun testOffsetScrollInitialWindow() {
        seed()
        val window = repository.findFirst2ByAgeGreaterThanOrderByAgeAsc(0, ScrollPosition.offset())
        assertEquals(listOf("Ann", "Bob"), window.content.orderedNames())
        assertEquals(2, window.size())
        assertTrue(window.hasNext())
        assertFalse(window.isLast)
    }

    @Test
    fun testOffsetScrollContinuesAfterLastElement() {
        seed()
        val first = repository.findFirst2ByAgeGreaterThanOrderByAgeAsc(0, ScrollPosition.offset())
        val second = repository.findFirst2ByAgeGreaterThanOrderByAgeAsc(0, first.positionAt(first.size() - 1))
        assertEquals(listOf("Carla", "Dan"), second.content.orderedNames())
        assertFalse(second.hasNext())
        assertTrue(second.isLast)
    }

    @Test
    fun testOffsetScrollPositionMeansAfterThatIndex() {
        seed()
        // offset(1) is the position of the element at index 1 (Bob), so scrolling continues after it
        val window = repository.findFirst2ByAgeGreaterThanOrderByAgeAsc(0, ScrollPosition.offset(1))
        assertEquals(listOf("Carla", "Dan"), window.content.orderedNames())
    }

    @Test
    fun testOffsetScrollBeyondEndIsEmpty() {
        seed()
        val window = repository.findFirst2ByAgeGreaterThanOrderByAgeAsc(0, ScrollPosition.offset(10))
        assertTrue(window.isEmpty)
        assertFalse(window.hasNext())
    }

    @Test
    fun testOffsetScrollShiftsWhenRowsAreInsertedBeforePosition() {
        seed()
        val first = repository.findFirst2ByAgeGreaterThanOrderByAgeAsc(0, ScrollPosition.offset())
        repository.save(Customer(name = "Aaron", email = "aaron@x.io", age = 20))
        val second = repository.findFirst2ByAgeGreaterThanOrderByAgeAsc(0, first.positionAt(first.size() - 1))
        // offsets count rows, so the new row pushes Bob into the next window
        assertEquals(listOf("Bob", "Carla"), second.content.orderedNames())
    }

    @Test
    fun testWindowIteratorWithOffset() {
        seed()
        val names = WindowIterator.of<Customer> { repository.findFirst2ByAgeGreaterThanOrderByAgeAsc(0, it) }
            .startingAt(ScrollPosition.offset())
            .asSequence()
            .map { it.name }
            .toList()
        assertEquals(listOf("Ann", "Bob", "Carla", "Dan"), names)
    }

    // ---- Keyset ----

    @Test
    fun testKeysetScrollForward() {
        seed()
        val first = repository.findFirst2ByAgeGreaterThanOrderByAgeAsc(0, ScrollPosition.keyset())
        assertEquals(listOf("Ann", "Bob"), first.content.orderedNames())
        assertTrue(first.hasNext())

        val second = repository.findFirst2ByAgeGreaterThanOrderByAgeAsc(0, first.positionAt(first.size() - 1))
        assertEquals(listOf("Carla", "Dan"), second.content.orderedNames())
        assertFalse(second.hasNext())
    }

    @Test
    fun testKeysetPositionContainsSortKeys() {
        seed()
        val window = repository.findFirst2ByAgeGreaterThanOrderByAgeAsc(0, ScrollPosition.keyset())
        val position = window.positionAt(1)
        assertTrue(position is KeysetScrollPosition) { "Expected a keyset position, got $position" }
        assertEquals(30, (position as KeysetScrollPosition).keys["age"])
        assertTrue(position.scrollsForward())
    }

    @Test
    fun testKeysetScrollDescending() {
        seed()
        val first = repository.findFirst2ByAgeGreaterThanOrderByAgeDesc(0, ScrollPosition.keyset())
        assertEquals(listOf("Dan", "Carla"), first.content.orderedNames())

        val second = repository.findFirst2ByAgeGreaterThanOrderByAgeDesc(0, first.positionAt(first.size() - 1))
        assertEquals(listOf("Bob", "Ann"), second.content.orderedNames())
        assertFalse(second.hasNext())
    }

    @Test
    fun testKeysetScrollBackward() {
        seed()
        val first = repository.findFirst2ByAgeGreaterThanOrderByAgeAsc(0, ScrollPosition.keyset())
        val second = repository.findFirst2ByAgeGreaterThanOrderByAgeAsc(0, first.positionAt(first.size() - 1))

        val backFromCarla = (second.positionAt(0) as KeysetScrollPosition).backward()
        val previous = repository.findFirst2ByAgeGreaterThanOrderByAgeAsc(0, backFromCarla)
        // content keeps the declared sort order, even when scrolling backward
        assertEquals(listOf("Ann", "Bob"), previous.content.orderedNames())
        assertFalse(previous.hasNext())
    }

    @Test
    fun testKeysetScrollIsStableWhenRowsAreInsertedBeforePosition() {
        seed()
        val first = repository.findFirst2ByAgeGreaterThanOrderByAgeAsc(0, ScrollPosition.keyset())
        repository.save(Customer(name = "Aaron", email = "aaron@x.io", age = 20))
        val second = repository.findFirst2ByAgeGreaterThanOrderByAgeAsc(0, first.positionAt(first.size() - 1))
        // keyset continues after Bob's age, so the new row doesn't shift the next window
        assertEquals(listOf("Carla", "Dan"), second.content.orderedNames())
    }

    @Test
    fun testKeysetScrollWithNonUniqueSortVisitsEveryRowOnce() {
        seed()
        // `active` has duplicates, so the keyset needs a unique tie-breaker (the id) to not skip or repeat rows
        val names = WindowIterator.of<Customer> { repository.findFirstByAgeGreaterThanOrderByActiveAsc(0, it) }
            .startingAt(ScrollPosition.keyset())
            .asSequence()
            .map { it.name }
            .toList()
        assertEquals(4, names.size)
        assertEquals(listOf("Ann", "Bob", "Carla", "Dan"), names.sorted())
        assertEquals(setOf("Bob", "Dan"), names.take(2).toSet())
    }

    @Test
    fun testKeysetScrollWithCriteria() {
        seed()
        val first = repository.findFirstByActiveTrueOrderByAgeAsc(ScrollPosition.keyset())
        assertEquals(listOf("Ann"), first.content.orderedNames())
        assertTrue(first.hasNext())

        val second = repository.findFirstByActiveTrueOrderByAgeAsc(first.positionAt(0))
        assertEquals(listOf("Carla"), second.content.orderedNames())
        assertFalse(second.hasNext())
    }

    @Test
    fun testWindowIteratorWithKeyset() {
        seed()
        val names = WindowIterator.of<Customer> { repository.findFirst2ByAgeGreaterThanOrderByAgeAsc(0, it) }
            .startingAt(ScrollPosition.keyset())
            .asSequence()
            .map { it.name }
            .toList()
        assertEquals(listOf("Ann", "Bob", "Carla", "Dan"), names)
    }

    // ---- Limit / Sort parameters and no limit ----

    @Test
    fun testScrollWithLimitAndSortParameters() {
        seed()
        val first = repository.findWindowByAgeGreaterThan(0, ScrollPosition.keyset(), Limit.of(3), Sort.by("age").descending())
        assertEquals(listOf("Dan", "Carla", "Bob"), first.content.orderedNames())
        assertTrue(first.hasNext())

        val second = repository.findWindowByAgeGreaterThan(0, first.positionAt(first.size() - 1), Limit.of(3), Sort.by("age").descending())
        assertEquals(listOf("Ann"), second.content.orderedNames())
        assertFalse(second.hasNext())
    }

    @Test
    fun testScrollWithOffsetAndLimitParameter() {
        seed()
        val window = repository.findWindowByAgeGreaterThan(0, ScrollPosition.offset(0), Limit.of(2), Sort.by("age"))
        assertEquals(listOf("Bob", "Carla"), window.content.orderedNames())
        assertTrue(window.hasNext())
    }

    @Test
    fun testScrollWithoutLimitReturnsEverything() {
        seed()
        val offset = repository.findByAgeGreaterThanOrderByAgeAsc(0, ScrollPosition.offset())
        assertEquals(listOf("Ann", "Bob", "Carla", "Dan"), offset.content.orderedNames())
        assertFalse(offset.hasNext())

        val keyset = repository.findByAgeGreaterThanOrderByAgeAsc(0, ScrollPosition.keyset())
        assertEquals(listOf("Ann", "Bob", "Carla", "Dan"), keyset.content.orderedNames())
        assertFalse(keyset.hasNext())
    }

    @Test
    fun testScrollOnEmptyResult() {
        seed()
        val window = repository.findFirst2ByAgeGreaterThanOrderByAgeAsc(100, ScrollPosition.keyset())
        assertTrue(window.isEmpty)
        assertFalse(window.hasNext())
    }

    // ---- Invalid signatures (rejected when the repository is created) ----

    @Test
    fun testScrollPositionWithoutWindowReturnTypeFailsAtCreation() {
        assertThrows<Exception> { ExposedRepositoryFactory().getRepository(ScrollWithoutWindowRepository::class.java) }
    }

    @Test
    fun testWindowWithoutScrollPositionFailsAtCreation() {
        assertThrows<Exception> { ExposedRepositoryFactory().getRepository(WindowWithoutScrollPositionRepository::class.java) }
    }

    /** Nested, so the package scan of [SpringDataExposedTestConfiguration] doesn't pick it up. */
    @ExposedRepository(table = Customers::class)
    interface ScrollWithoutWindowRepository : ExposedCrudRepository<Customer, Long> {
        fun findFirst2ByAgeGreaterThanOrderByAgeAsc(age: Int, position: ScrollPosition): List<Customer>
    }

    /** Nested, so the package scan of [SpringDataExposedTestConfiguration] doesn't pick it up. */
    @ExposedRepository(table = Customers::class)
    interface WindowWithoutScrollPositionRepository : ExposedCrudRepository<Customer, Long> {
        fun findFirst2ByAgeGreaterThanOrderByAgeAsc(age: Int): Window<Customer>
    }
}

@ExposedRepository(table = Customers::class)
interface ScrollCustomerRepository : ExposedCrudRepository<Customer, Long> {
    fun findFirst2ByAgeGreaterThanOrderByAgeAsc(age: Int, position: ScrollPosition): Window<Customer>
    fun findFirst2ByAgeGreaterThanOrderByAgeDesc(age: Int, position: ScrollPosition): Window<Customer>
    fun findFirstByAgeGreaterThanOrderByActiveAsc(age: Int, position: ScrollPosition): Window<Customer>
    fun findFirstByActiveTrueOrderByAgeAsc(position: ScrollPosition): Window<Customer>
    fun findWindowByAgeGreaterThan(age: Int, position: ScrollPosition, limit: Limit, sort: Sort): Window<Customer>
    fun findByAgeGreaterThanOrderByAgeAsc(age: Int, position: ScrollPosition): Window<Customer>
}
