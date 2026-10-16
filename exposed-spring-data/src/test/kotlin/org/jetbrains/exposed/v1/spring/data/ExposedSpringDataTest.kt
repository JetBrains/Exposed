package org.jetbrains.exposed.v1.spring.data

import org.jetbrains.exposed.v1.core.dao.id.LongIdTable
import org.jetbrains.exposed.v1.javatime.timestamp
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.spring.data.repository.ExposedRepositoryFactory
import org.jetbrains.exposed.v1.spring7.transaction.SpringTransactionManager
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertNotNull
import org.junit.jupiter.api.assertNull
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.FilterType
import org.springframework.dao.IncorrectResultSizeDataAccessException
import org.springframework.data.domain.KeysetScrollPosition
import org.springframework.data.domain.Limit
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.ScrollPosition
import org.springframework.data.domain.Slice
import org.springframework.data.domain.Sort
import org.springframework.data.domain.Window
import org.springframework.data.repository.ListPagingAndSortingRepository
import org.springframework.data.repository.findByIdOrNull
import org.springframework.data.support.WindowIterator
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.annotation.EnableTransactionManagement
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate
import java.time.Instant
import java.util.Optional
import java.util.stream.Stream
import javax.sql.DataSource

@SpringJUnitConfig(SpringDataExposedTestConfiguration::class)
@Transactional
open class ExposedSpringDataTest {

    @Autowired
    lateinit var customerRepository: CustomerRepository

    @Autowired
    lateinit var tm: PlatformTransactionManager

    @BeforeEach
    fun setUp() = TransactionTemplate(tm).executeWithoutResult { SchemaUtils.create(Customers) }

    @AfterEach
    fun tearDown() = TransactionTemplate(tm).executeWithoutResult { SchemaUtils.drop(Customers) }

    private fun seed(): List<Customer> = customerRepository.saveAll(
        listOf(
            Customer(name = "Ann", email = "ann@x.io", age = 25, active = true, nickname = "annie", createdAt = JAN),
            Customer(name = "Bob", email = "bob@x.io", age = 30, active = false, nickname = null, createdAt = FEB),
            Customer(name = "Carla", email = "carla@example.com", age = 35, active = true, nickname = "car_la", createdAt = MAR),
            Customer(name = "Dan", email = "dan@x.io", age = 40, active = false, nickname = "50%off", createdAt = APR),
        )
    )

    private fun List<Customer>.names() = map { it.name }.sorted()

    private fun List<Customer>.orderedNames() = map { it.name }

    // ---- CRUD ----

    @Test
    fun testSaveAssignsIdAndFindByIdReturnsIt() {
        val saved = customerRepository.save(Customer(name = "Ann", email = "ann@x.io"))
        assertNotNull(saved.id)
        assertEquals(saved, customerRepository.findByIdOrNull(saved.id))
    }

    @Test
    fun testFindByIdReturnsNullWhenMissing() {
        assertNull(customerRepository.findByIdOrNull(42L))
    }

    // ---- SIMPLE_PROPERTY ----

    @Test
    fun testFindByEmail() {
        customerRepository.save(Customer(name = "Ann", email = "ann@x.io"))
        assertEquals("Ann", customerRepository.findByEmail("ann@x.io")?.name)
        assertNull(customerRepository.findByEmail("nobody@x.io"))
    }

    @Test
    fun testSimplePropertyReturningList() {
        seed()
        assertEquals(listOf("Bob"), customerRepository.findByName("Bob").names())
        assertEquals(emptyList<String>(), customerRepository.findByName("Nobody").names())
    }

    @Test
    fun testSimplePropertyWithIsKeyword() {
        seed()
        assertEquals(listOf("Carla"), customerRepository.findByAgeIs(35).names())
    }

    @Test
    fun testSimplePropertyWithNullArgumentMeansIsNull() {
        seed()
        assertEquals(listOf("Bob"), customerRepository.findByNickname(null).names())
    }

    @Test
    fun testSimplePropertyOnIdColumn() {
        val ann = seed().first()
        assertEquals(listOf("Ann"), customerRepository.findAllByIdEquals(ann.id!!).names())
    }

    // ---- NEGATING_SIMPLE_PROPERTY ----

    @Test
    fun testNegatingSimpleProperty() {
        seed()
        assertEquals(listOf("Bob", "Carla", "Dan"), customerRepository.findByNameNot("Ann").names())
        assertEquals(listOf("Bob", "Carla", "Dan"), customerRepository.findByNameIsNot("Ann").names())
    }

    // ---- BETWEEN ----

    @Test
    fun testBetweenIsInclusive() {
        seed()
        assertEquals(listOf("Bob", "Carla"), customerRepository.findByAgeBetween(30, 35).names())
        assertEquals(listOf("Ann", "Bob"), customerRepository.findByCreatedAtBetween(JAN, FEB).names())
    }

    // ---- IS_NULL / IS_NOT_NULL ----

    @Test
    fun testIsNull() {
        seed()
        assertEquals(listOf("Bob"), customerRepository.findByNicknameIsNull().names())
        assertEquals(listOf("Bob"), customerRepository.findByNicknameNull().names())
    }

    @Test
    fun testIsNotNull() {
        seed()
        assertEquals(listOf("Ann", "Carla", "Dan"), customerRepository.findByNicknameIsNotNull().names())
        assertEquals(listOf("Ann", "Carla", "Dan"), customerRepository.findByNicknameNotNull().names())
    }

    // ---- LESS_THAN / LESS_THAN_EQUAL / GREATER_THAN / GREATER_THAN_EQUAL ----

    @Test
    fun testLessThan() {
        seed()
        assertEquals(listOf("Ann"), customerRepository.findByAgeLessThan(30).names())
        assertEquals(listOf("Ann"), customerRepository.findByAgeIsLessThan(30).names())
    }

    @Test
    fun testLessThanEqual() {
        seed()
        assertEquals(listOf("Ann", "Bob"), customerRepository.findByAgeLessThanEqual(30).names())
    }

    @Test
    fun testGreaterThan() {
        seed()
        assertEquals(listOf("Dan"), customerRepository.findByAgeGreaterThan(35).names())
        assertEquals(listOf("Dan"), customerRepository.findByAgeIsGreaterThan(35).names())
    }

    @Test
    fun testGreaterThanEqual() {
        seed()
        assertEquals(listOf("Carla", "Dan"), customerRepository.findByAgeGreaterThanEqual(35).names())
    }

    // ---- BEFORE / AFTER ----

    @Test
    fun testBefore() {
        seed()
        assertEquals(listOf("Ann"), customerRepository.findByCreatedAtBefore(FEB).names())
        assertEquals(listOf("Ann"), customerRepository.findByCreatedAtIsBefore(FEB).names())
    }

    @Test
    fun testAfter() {
        seed()
        assertEquals(listOf("Dan"), customerRepository.findByCreatedAtAfter(MAR).names())
        assertEquals(listOf("Dan"), customerRepository.findByCreatedAtIsAfter(MAR).names())
    }

    @Test
    fun testCountByCreatedAtAfter() {
        seed()
        assertEquals(2L, customerRepository.countByCreatedAtAfter(FEB))
    }

    // ---- LIKE / NOT_LIKE (pattern is passed through as is) ----

    @Test
    fun testLikePassesPatternThrough() {
        seed()
        assertEquals(listOf("Carla", "Dan"), customerRepository.findByNameLike("%a%").names())
        assertEquals(listOf("Bob"), customerRepository.findByNameIsLike("B_b").names())
    }

    @Test
    fun testNotLike() {
        seed()
        assertEquals(listOf("Ann", "Bob"), customerRepository.findByNameNotLike("%a%").names())
        assertEquals(listOf("Ann", "Bob"), customerRepository.findByNameIsNotLike("%a%").names())
    }

    // ---- STARTING_WITH / ENDING_WITH / CONTAINING / NOT_CONTAINING (argument is escaped) ----

    @Test
    fun testStartingWith() {
        seed()
        assertEquals(listOf("Carla"), customerRepository.findByEmailStartingWith("ca").names())
        assertEquals(listOf("Carla"), customerRepository.findByEmailStartsWith("ca").names())
    }

    @Test
    fun testEndingWith() {
        seed()
        assertEquals(listOf("Ann", "Bob", "Dan"), customerRepository.findByEmailEndingWith("@x.io").names())
        assertEquals(listOf("Ann", "Bob", "Dan"), customerRepository.findByEmailEndsWith("@x.io").names())
    }

    @Test
    fun testContaining() {
        seed()
        assertEquals(listOf("Carla", "Dan"), customerRepository.findByNameContaining("a").names())
        assertEquals(listOf("Carla", "Dan"), customerRepository.findByNameContains("a").names())
    }

    @Test
    fun testNotContaining() {
        seed()
        assertEquals(listOf("Ann", "Bob"), customerRepository.findByNameNotContaining("a").names())
        assertEquals(listOf("Ann", "Bob"), customerRepository.findByNameNotContains("a").names())
    }

    @Test
    fun testWildcardsInArgumentAreEscaped() {
        seed()
        // unescaped, "_" would match any single character and "%" anything
        assertEquals(listOf("Carla"), customerRepository.findByNicknameContaining("_").names())
        assertEquals(listOf("Dan"), customerRepository.findByNicknameContaining("%").names())
        assertEquals(listOf("Dan"), customerRepository.findByNicknameStartingWith("50%").names())
        assertEquals(listOf("Carla"), customerRepository.findByNicknameEndingWith("_la").names())
    }

    // ---- IN / NOT_IN ----

    @Test
    fun testIn() {
        seed()
        assertEquals(listOf("Ann", "Dan"), customerRepository.findByAgeIn(listOf(25, 40)).names())
        assertEquals(listOf("Ann", "Dan"), customerRepository.findByAgeIsIn(listOf(25, 40)).names())
    }

    @Test
    fun testInWithEmptyCollectionMatchesNothing() {
        seed()
        assertEquals(emptyList<String>(), customerRepository.findByAgeIn(emptyList()).names())
    }

    @Test
    fun testInOnIdColumn() {
        val (ann, _, carla) = seed()
        assertEquals(listOf("Ann", "Carla"), customerRepository.findByIdIn(listOf(ann.id!!, carla.id!!)).names())
    }

    @Test
    fun testNotIn() {
        seed()
        assertEquals(listOf("Bob", "Carla"), customerRepository.findByAgeNotIn(listOf(25, 40)).names())
        assertEquals(listOf("Bob", "Carla"), customerRepository.findByAgeIsNotIn(listOf(25, 40)).names())
    }

    // ---- REGEX ----

    @Test
    fun testRegex() {
        seed()
        assertEquals(listOf("Ann", "Bob"), customerRepository.findByEmailMatches("^[a-b].*@x\\.io$").names())
        assertEquals(listOf("Ann", "Bob"), customerRepository.findByEmailRegex("^[a-b].*@x\\.io$").names())
        assertEquals(listOf("Ann", "Bob"), customerRepository.findByEmailMatchesRegex("^[a-b].*@x\\.io$").names())
    }

    // ---- TRUE / FALSE ----

    @Test
    fun testTrue() {
        seed()
        assertEquals(listOf("Ann", "Carla"), customerRepository.findByActiveTrue().names())
        assertEquals(listOf("Ann", "Carla"), customerRepository.findByActiveIsTrue().names())
    }

    @Test
    fun testFalse() {
        seed()
        assertEquals(listOf("Bob", "Dan"), customerRepository.findByActiveFalse().names())
        assertEquals(listOf("Bob", "Dan"), customerRepository.findByActiveIsFalse().names())
    }

    // ---- IgnoreCase ----

    @Test
    fun testIgnoreCaseOnSimpleProperty() {
        seed()
        assertEquals(listOf("Ann"), customerRepository.findByNameIgnoreCase("aNN").names())
        assertEquals(emptyList<String>(), customerRepository.findByName("aNN").names())
    }

    @Test
    fun testIgnoreCaseOnLikeVariants() {
        seed()
        assertEquals(listOf("Ann", "Carla", "Dan"), customerRepository.findByNameContainingIgnoreCase("A").names())
        assertEquals(listOf("Ann"), customerRepository.findByEmailStartingWithIgnoreCase("ANN").names())
        assertEquals(listOf("Carla"), customerRepository.findByEmailEndingWithIgnoreCase("EXAMPLE.COM").names())
    }

    @Test
    fun testAllIgnoreCase() {
        seed()
        assertEquals(listOf("Ann"), customerRepository.findByNameAndEmailAllIgnoreCase("ANN", "Ann@X.io").names())
    }

    // ---- And / Or ----

    @Test
    fun testAnd() {
        seed()
        assertEquals(listOf("Carla"), customerRepository.findByActiveTrueAndAgeGreaterThan(30).names())
    }

    @Test
    fun testOr() {
        seed()
        assertEquals(listOf("Ann", "Bob"), customerRepository.findByNameOrEmail("Ann", "bob@x.io").names())
    }

    @Test
    fun testAndBindsStrongerThanOr() {
        seed()
        // (active = true AND age > 30) OR name = 'Bob'
        assertEquals(listOf("Bob", "Carla"), customerRepository.findByActiveTrueAndAgeGreaterThanOrName(30, "Bob").names())
    }

    // ---- Subject prefixes: find / read / get / query / search / stream ----

    @Test
    fun testAllFindPrefixesBehaveTheSame() {
        seed()
        assertEquals(listOf("Bob"), customerRepository.readByName("Bob").names())
        assertEquals("Bob", customerRepository.getByName("Bob")?.name)
        assertEquals(listOf("Bob"), customerRepository.queryByName("Bob").names())
        assertEquals(listOf("Bob"), customerRepository.searchByName("Bob").names())
        assertEquals(listOf("Bob"), customerRepository.streamByName("Bob").use { it.toList() }.names())
    }

    @Test
    fun testTextBetweenPrefixAndByIsIgnored() {
        seed()
        assertEquals(listOf("Bob"), customerRepository.findAllByName("Bob").names())
        assertEquals(listOf("Bob"), customerRepository.findCustomersByName("Bob").names())
    }

    // ---- Single-result return types ----

    @Test
    fun testOptionalReturnType() {
        seed()
        assertEquals("Ann", customerRepository.findOneByEmail("ann@x.io").get().name)
        assertTrue(customerRepository.findOneByEmail("nobody@x.io").isEmpty)
    }

    @Test
    fun testSingleResultWithMoreThanOneRowThrows() {
        seed()
        assertThrows<IncorrectResultSizeDataAccessException> { customerRepository.findByActive(true) }
    }

    // ---- count ----

    @Test
    fun testCount() {
        seed()
        assertEquals(2L, customerRepository.countByActiveTrue())
        assertEquals(0L, customerRepository.countByName("Nobody"))
    }

    @Test
    fun testCountWithIntReturnType() {
        seed()
        assertEquals(2, customerRepository.countByAgeLessThan(35))
    }

    // ---- exists ----

    @Test
    fun testExists() {
        seed()
        assertTrue(customerRepository.existsByEmail("ann@x.io"))
        assertFalse(customerRepository.existsByEmail("nobody@x.io"))
    }

    @Test
    fun testExistsWithMultipleMatches() {
        seed()
        assertTrue(customerRepository.existsByAgeGreaterThan(20))
        assertFalse(customerRepository.existsByAgeGreaterThan(40))
    }

    // ---- delete / remove ----

    @Test
    fun testDeleteReturnsNumberOfDeletedRows() {
        seed()
        assertEquals(2L, customerRepository.deleteByActiveFalse())
        assertEquals(listOf("Ann", "Carla"), customerRepository.findAll().names())
    }

    @Test
    fun testRemovePrefix() {
        seed()
        assertEquals(1L, customerRepository.removeByName("Bob"))
        assertEquals(listOf("Ann", "Carla", "Dan"), customerRepository.findAll().names())
    }

    @Test
    fun testDeleteWithUnitReturnType() {
        seed()
        customerRepository.deleteByAge(25)
        assertEquals(listOf("Bob", "Carla", "Dan"), customerRepository.findAll().names())
    }

    @Test
    fun testDeleteReturningDeletedEntities() {
        seed()
        assertEquals(listOf("Carla", "Dan"), customerRepository.deleteByAgeGreaterThanEqual(35).names())
        assertEquals(listOf("Ann", "Bob"), customerRepository.findAll().names())
    }

    @Test
    fun testDeleteWithoutMatchDeletesNothing() {
        seed()
        assertEquals(0L, customerRepository.removeByName("Nobody"))
        assertEquals(4L, customerRepository.count())
    }

    // ---- First / Top ----

    @Test
    fun testFirstAndTopWithoutNumberReturnOneRow() {
        seed()
        assertEquals("Ann", customerRepository.findFirstByOrderByAgeAsc()?.name)
        assertEquals("Dan", customerRepository.findTopByOrderByAgeDesc()?.name)
    }

    @Test
    fun testTopWithNumber() {
        seed()
        assertEquals(listOf("Dan", "Carla"), customerRepository.findTop2ByOrderByAgeDesc().orderedNames())
        assertEquals(listOf("Ann", "Bob"), customerRepository.findFirst2ByOrderByAgeAsc().orderedNames())
    }

    @Test
    fun testTopWithMoreThanAvailableRows() {
        seed()
        assertEquals(listOf("Bob", "Dan"), customerRepository.findTop3ByActiveFalseOrderByAgeAsc().orderedNames())
    }

    @Test
    fun testFirstOnEmptyResult() {
        seed()
        assertNull(customerRepository.findFirstByNameOrderByAgeAsc("Nobody"))
    }

    // ---- Distinct ----

    @Test
    fun testDistinct() {
        seed()
        assertEquals(listOf("Ann", "Carla"), customerRepository.findDistinctByActiveTrue().names())
        assertEquals(listOf("Ann", "Carla"), customerRepository.findCustomersDistinctByActiveTrue().names())
    }

    // ---- OrderBy ----

    @Test
    fun testOrderBy() {
        seed()
        assertEquals(listOf("Carla", "Ann"), customerRepository.findByActiveTrueOrderByAgeDesc().orderedNames())
        assertEquals(listOf("Ann", "Carla"), customerRepository.findByActiveTrueOrderByAgeAsc().orderedNames())
    }

    @Test
    fun testOrderByWithoutDirectionIsAscending() {
        seed()
        assertEquals(listOf("Ann", "Bob", "Carla", "Dan"), customerRepository.findAllByOrderByAge().orderedNames())
    }

    @Test
    fun testOrderByMultipleProperties() {
        seed()
        assertEquals(listOf("Dan", "Bob", "Carla", "Ann"), customerRepository.findAllByOrderByActiveAscAgeDesc().orderedNames())
    }

    // ---- Sort parameter ----

    @Test
    fun testSortParameter() {
        seed()
        assertEquals(
            listOf("Dan", "Carla", "Bob"),
            customerRepository.findAllByAgeGreaterThan(25, Sort.by("age").descending()).orderedNames()
        )
        assertEquals(
            listOf("Bob", "Carla", "Dan"),
            customerRepository.findAllByAgeGreaterThan(25, Sort.by(Sort.Direction.ASC, "name")).orderedNames()
        )
    }

    @Test
    fun testUnsortedSortParameter() {
        seed()
        assertEquals(listOf("Bob", "Carla", "Dan"), customerRepository.findAllByAgeGreaterThan(25, Sort.unsorted()).names())
    }

    @Test
    fun testSortParameterWithMultipleProperties() {
        seed()
        val sort = Sort.by(Sort.Order.asc("active"), Sort.Order.desc("age"))
        assertEquals(listOf("Dan", "Bob", "Carla", "Ann"), customerRepository.findAllByAgeGreaterThan(0, sort).orderedNames())
    }

    @Test
    fun testSortByUnknownPropertyFails() {
        seed()
        assertThrows<Exception> { customerRepository.findAllByAgeGreaterThan(0, Sort.by("doesNotExist")) }
    }

    // ---- Limit parameter ----

    @Test
    fun testLimitParameter() {
        seed()
        assertEquals(listOf("Ann", "Bob"), customerRepository.findLimitedByAgeGreaterThan(0, Limit.of(2), Sort.by("age")).orderedNames())
        assertEquals(4, customerRepository.findLimitedByAgeGreaterThan(0, Limit.unlimited(), Sort.by("age")).size)
    }

    // ---- Pageable: Page / Slice / List ----

    @Test
    fun testPage() {
        seed()
        val first = customerRepository.findPageByAgeGreaterThan(0, PageRequest.of(0, 3, Sort.by("age")))
        assertEquals(listOf("Ann", "Bob", "Carla"), first.content.orderedNames())
        assertEquals(4L, first.totalElements)
        assertEquals(2, first.totalPages)
        assertTrue(first.hasNext())

        val second = customerRepository.findPageByAgeGreaterThan(0, PageRequest.of(1, 3, Sort.by("age")))
        assertEquals(listOf("Dan"), second.content.orderedNames())
        assertEquals(4L, second.totalElements)
        assertFalse(second.hasNext())
    }

    @Test
    fun testPageTotalCountsOnlyMatchingRows() {
        seed()
        val page = customerRepository.findByNameContainingIgnoreCase("A", PageRequest.of(0, 2, Sort.by("name")))
        assertEquals(listOf("Ann", "Carla"), page.content.orderedNames())
        assertEquals(3L, page.totalElements)
        assertEquals(2, page.totalPages)
    }

    @Test
    fun testPageBeyondLastPageIsEmpty() {
        seed()
        val page = customerRepository.findPageByAgeGreaterThan(0, PageRequest.of(5, 3))
        assertTrue(page.content.isEmpty())
        assertEquals(4L, page.totalElements)
    }

    @Test
    fun testUnpagedPageReturnsEverything() {
        seed()
        val page = customerRepository.findPageByAgeGreaterThan(0, Pageable.unpaged())
        assertEquals(listOf("Ann", "Bob", "Carla", "Dan"), page.content.names())
        assertEquals(4L, page.totalElements)
    }

    @Test
    fun testSlice() {
        seed()
        val first = customerRepository.findSliceByAgeGreaterThan(0, PageRequest.of(0, 3, Sort.by("age")))
        assertEquals(listOf("Ann", "Bob", "Carla"), first.content.orderedNames())
        assertTrue(first.hasNext())

        val second = customerRepository.findSliceByAgeGreaterThan(0, PageRequest.of(1, 3, Sort.by("age")))
        assertEquals(listOf("Dan"), second.content.orderedNames())
        assertFalse(second.hasNext())
    }

    @Test
    fun testListWithPageable() {
        seed()
        assertEquals(
            listOf("Carla", "Dan"),
            customerRepository.findListByAgeGreaterThan(0, PageRequest.of(1, 2, Sort.by("age"))).orderedNames()
        )
    }

    @Test
    fun testPageableSortCombinedWithOrderBy() {
        seed()
        // static OrderBy first, then the Pageable's sort
        val page = customerRepository.findByAgeGreaterThanOrderByActiveAsc(0, PageRequest.of(0, 4, Sort.by("age").descending()))
        assertEquals(listOf("Dan", "Bob", "Carla", "Ann"), page.content.orderedNames())
    }

    // ---- More paging: metadata, boundaries, iteration ----

    @Test
    fun testPageMetadata() {
        seed()
        val page = customerRepository.findPageByAgeGreaterThan(0, PageRequest.of(0, 3, Sort.by("age")))
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
        val page = customerRepository.findPageByAgeGreaterThan(0, PageRequest.of(1, 2, Sort.by("age")))
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
        var page = customerRepository.findPageByAgeGreaterThan(0, PageRequest.of(0, 3, Sort.by("age")))
        while (true) {
            pages++
            names += page.content.orderedNames()
            if (!page.hasNext()) break
            page = customerRepository.findPageByAgeGreaterThan(0, page.nextPageable())
        }
        assertEquals(2, pages)
        assertEquals(listOf("Ann", "Bob", "Carla", "Dan"), names)
    }

    @Test
    fun testPageOnEmptyTable() {
        val page = customerRepository.findPageByAgeGreaterThan(0, PageRequest.of(0, 2))
        assertTrue(page.content.isEmpty())
        assertEquals(0L, page.totalElements)
        assertEquals(0, page.totalPages)
        assertFalse(page.hasNext())
    }

    @Test
    fun testPageWithNoMatches() {
        seed()
        val page = customerRepository.findPageByAgeGreaterThan(100, PageRequest.of(0, 2))
        assertTrue(page.content.isEmpty())
        assertEquals(0L, page.totalElements)
        assertEquals(0, page.totalPages)
    }

    @Test
    fun testPageableSortByUnknownPropertyFails() {
        seed()
        assertThrows<Exception> { customerRepository.findPageByAgeGreaterThan(0, PageRequest.of(0, 2, Sort.by("doesNotExist"))) }
    }

    @Test
    fun testSliceUnpaged() {
        seed()
        val slice = customerRepository.findSliceByAgeGreaterThan(0, Pageable.unpaged())
        assertEquals(listOf("Ann", "Bob", "Carla", "Dan"), slice.content.names())
        assertFalse(slice.hasNext())
    }

    @Test
    fun testSliceOnExactBoundaryHasNoNext() {
        seed()
        val last = customerRepository.findSliceByAgeGreaterThan(0, PageRequest.of(1, 2, Sort.by("age")))
        assertEquals(listOf("Carla", "Dan"), last.content.orderedNames())
        assertFalse(last.hasNext())
        assertTrue(last.hasPrevious())

        val all = customerRepository.findSliceByAgeGreaterThan(0, PageRequest.of(0, 4, Sort.by("age")))
        assertEquals(4, all.numberOfElements)
        assertFalse(all.hasNext())
    }

    @Test
    fun testIteratingAllSlicesWithNextPageable() {
        seed()
        val names = mutableListOf<String>()
        var slice = customerRepository.findSliceByAgeGreaterThan(0, PageRequest.of(0, 3, Sort.by("age")))
        names += slice.content.orderedNames()
        while (slice.hasNext()) {
            slice = customerRepository.findSliceByAgeGreaterThan(0, slice.nextPageable())
            names += slice.content.orderedNames()
        }
        assertEquals(listOf("Ann", "Bob", "Carla", "Dan"), names)
    }

    // ---- Scrolling: offset ----

    @Test
    fun testOffsetScrollInitialWindow() {
        seed()
        val window = customerRepository.findFirst2ByAgeGreaterThanOrderByAgeAsc(0, ScrollPosition.offset())
        assertEquals(listOf("Ann", "Bob"), window.content.orderedNames())
        assertEquals(2, window.size())
        assertTrue(window.hasNext())
        assertFalse(window.isLast)
    }

    @Test
    fun testOffsetScrollContinuesAfterLastElement() {
        seed()
        val first = customerRepository.findFirst2ByAgeGreaterThanOrderByAgeAsc(0, ScrollPosition.offset())
        val second = customerRepository.findFirst2ByAgeGreaterThanOrderByAgeAsc(0, first.positionAt(first.size() - 1))
        assertEquals(listOf("Carla", "Dan"), second.content.orderedNames())
        assertFalse(second.hasNext())
        assertTrue(second.isLast)
    }

    @Test
    fun testOffsetScrollPositionMeansAfterThatIndex() {
        seed()
        // offset(1) is the position of the element at index 1 (Bob), so scrolling continues after it
        val window = customerRepository.findFirst2ByAgeGreaterThanOrderByAgeAsc(0, ScrollPosition.offset(1))
        assertEquals(listOf("Carla", "Dan"), window.content.orderedNames())
    }

    @Test
    fun testOffsetScrollBeyondEndIsEmpty() {
        seed()
        val window = customerRepository.findFirst2ByAgeGreaterThanOrderByAgeAsc(0, ScrollPosition.offset(10))
        assertTrue(window.isEmpty)
        assertFalse(window.hasNext())
    }

    @Test
    fun testOffsetScrollShiftsWhenRowsAreInsertedBeforePosition() {
        seed()
        val first = customerRepository.findFirst2ByAgeGreaterThanOrderByAgeAsc(0, ScrollPosition.offset())
        customerRepository.save(Customer(name = "Aaron", email = "aaron@x.io", age = 20))
        val second = customerRepository.findFirst2ByAgeGreaterThanOrderByAgeAsc(0, first.positionAt(first.size() - 1))
        // offsets count rows, so the new row pushes Bob into the next window
        assertEquals(listOf("Bob", "Carla"), second.content.orderedNames())
    }

    @Test
    fun testWindowIteratorWithOffset() {
        seed()
        val names = WindowIterator.of<Customer> { customerRepository.findFirst2ByAgeGreaterThanOrderByAgeAsc(0, it) }
            .startingAt(ScrollPosition.offset())
            .asSequence()
            .map { it.name }
            .toList()
        assertEquals(listOf("Ann", "Bob", "Carla", "Dan"), names)
    }

    // ---- Scrolling: keyset ----

    @Test
    fun testKeysetScrollForward() {
        seed()
        val first = customerRepository.findFirst2ByAgeGreaterThanOrderByAgeAsc(0, ScrollPosition.keyset())
        assertEquals(listOf("Ann", "Bob"), first.content.orderedNames())
        assertTrue(first.hasNext())

        val second = customerRepository.findFirst2ByAgeGreaterThanOrderByAgeAsc(0, first.positionAt(first.size() - 1))
        assertEquals(listOf("Carla", "Dan"), second.content.orderedNames())
        assertFalse(second.hasNext())
    }

    @Test
    fun testKeysetPositionContainsSortKeys() {
        seed()
        val window = customerRepository.findFirst2ByAgeGreaterThanOrderByAgeAsc(0, ScrollPosition.keyset())
        val position = window.positionAt(1)
        assertTrue(position is KeysetScrollPosition) { "Expected a keyset position, got $position" }
        assertEquals(30, (position as KeysetScrollPosition).keys["age"])
        assertTrue(position.scrollsForward())
    }

    @Test
    fun testKeysetScrollDescending() {
        seed()
        val first = customerRepository.findFirst2ByAgeGreaterThanOrderByAgeDesc(0, ScrollPosition.keyset())
        assertEquals(listOf("Dan", "Carla"), first.content.orderedNames())

        val second = customerRepository.findFirst2ByAgeGreaterThanOrderByAgeDesc(0, first.positionAt(first.size() - 1))
        assertEquals(listOf("Bob", "Ann"), second.content.orderedNames())
        assertFalse(second.hasNext())
    }

    @Test
    fun testKeysetScrollBackward() {
        seed()
        val first = customerRepository.findFirst2ByAgeGreaterThanOrderByAgeAsc(0, ScrollPosition.keyset())
        val second = customerRepository.findFirst2ByAgeGreaterThanOrderByAgeAsc(0, first.positionAt(first.size() - 1))

        val backFromCarla = (second.positionAt(0) as KeysetScrollPosition).backward()
        val previous = customerRepository.findFirst2ByAgeGreaterThanOrderByAgeAsc(0, backFromCarla)
        // content keeps the declared sort order, even when scrolling backward
        assertEquals(listOf("Ann", "Bob"), previous.content.orderedNames())
        assertFalse(previous.hasNext())
    }

    @Test
    fun testKeysetScrollIsStableWhenRowsAreInsertedBeforePosition() {
        seed()
        val first = customerRepository.findFirst2ByAgeGreaterThanOrderByAgeAsc(0, ScrollPosition.keyset())
        customerRepository.save(Customer(name = "Aaron", email = "aaron@x.io", age = 20))
        val second = customerRepository.findFirst2ByAgeGreaterThanOrderByAgeAsc(0, first.positionAt(first.size() - 1))
        // keyset continues after Bob's age, so the new row doesn't shift the next window
        assertEquals(listOf("Carla", "Dan"), second.content.orderedNames())
    }

    @Test
    fun testKeysetScrollWithNonUniqueSortVisitsEveryRowOnce() {
        seed()
        // `active` has duplicates, so the keyset needs a unique tie-breaker (the id) to not skip or repeat rows
        val names = WindowIterator.of<Customer> { customerRepository.findFirstByAgeGreaterThanOrderByActiveAsc(0, it) }
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
        val first = customerRepository.findFirstByActiveTrueOrderByAgeAsc(ScrollPosition.keyset())
        assertEquals(listOf("Ann"), first.content.orderedNames())
        assertTrue(first.hasNext())

        val second = customerRepository.findFirstByActiveTrueOrderByAgeAsc(first.positionAt(0))
        assertEquals(listOf("Carla"), second.content.orderedNames())
        assertFalse(second.hasNext())
    }

    @Test
    fun testWindowIteratorWithKeyset() {
        seed()
        val names = WindowIterator.of<Customer> { customerRepository.findFirst2ByAgeGreaterThanOrderByAgeAsc(0, it) }
            .startingAt(ScrollPosition.keyset())
            .asSequence()
            .map { it.name }
            .toList()
        assertEquals(listOf("Ann", "Bob", "Carla", "Dan"), names)
    }

    // ---- Scrolling: Limit / Sort parameters and no limit ----

    @Test
    fun testScrollWithLimitAndSortParameters() {
        seed()
        val first = customerRepository.findWindowByAgeGreaterThan(0, ScrollPosition.keyset(), Limit.of(3), Sort.by("age").descending())
        assertEquals(listOf("Dan", "Carla", "Bob"), first.content.orderedNames())
        assertTrue(first.hasNext())

        val second = customerRepository.findWindowByAgeGreaterThan(0, first.positionAt(first.size() - 1), Limit.of(3), Sort.by("age").descending())
        assertEquals(listOf("Ann"), second.content.orderedNames())
        assertFalse(second.hasNext())
    }

    @Test
    fun testScrollWithOffsetAndLimitParameter() {
        seed()
        val window = customerRepository.findWindowByAgeGreaterThan(0, ScrollPosition.offset(0), Limit.of(2), Sort.by("age"))
        assertEquals(listOf("Bob", "Carla"), window.content.orderedNames())
        assertTrue(window.hasNext())
    }

    @Test
    fun testScrollWithoutLimitReturnsEverything() {
        seed()
        val offset = customerRepository.findByAgeGreaterThanOrderByAgeAsc(0, ScrollPosition.offset())
        assertEquals(listOf("Ann", "Bob", "Carla", "Dan"), offset.content.orderedNames())
        assertFalse(offset.hasNext())

        val keyset = customerRepository.findByAgeGreaterThanOrderByAgeAsc(0, ScrollPosition.keyset())
        assertEquals(listOf("Ann", "Bob", "Carla", "Dan"), keyset.content.orderedNames())
        assertFalse(keyset.hasNext())
    }

    @Test
    fun testScrollOnEmptyResult() {
        seed()
        val window = customerRepository.findFirst2ByAgeGreaterThanOrderByAgeAsc(100, ScrollPosition.keyset())
        assertTrue(window.isEmpty)
        assertFalse(window.hasNext())
    }

    companion object {
        val JAN: Instant = Instant.parse("2024-01-01T00:00:00Z")
        val FEB: Instant = Instant.parse("2024-02-01T00:00:00Z")
        val MAR: Instant = Instant.parse("2024-03-01T00:00:00Z")
        val APR: Instant = Instant.parse("2024-04-01T00:00:00Z")
    }
}

/**
 * `findAll(Sort)` and `findAll(Pageable)` from [ListPagingAndSortingRepository].
 *
 * Uses its own context with a nested repository, so a missing base-class implementation only fails these tests.
 */
@SpringJUnitConfig(PagingAndSortingTestConfiguration::class)
@Transactional
open class ExposedSpringDataPagingAndSortingRepositoryTest {

    @Autowired
    lateinit var repository: PagingCustomerRepository

    @Autowired
    lateinit var tm: PlatformTransactionManager

    @BeforeEach
    fun setUp() = TransactionTemplate(tm).executeWithoutResult { SchemaUtils.create(Customers) }

    @AfterEach
    fun tearDown() = TransactionTemplate(tm).executeWithoutResult { SchemaUtils.drop(Customers) }

    private fun seed() {
        repository.saveAll(
            listOf(
                Customer(name = "Ann", email = "ann@x.io", age = 25, active = true),
                Customer(name = "Bob", email = "bob@x.io", age = 30, active = false),
                Customer(name = "Carla", email = "carla@example.com", age = 35, active = true),
                Customer(name = "Dan", email = "dan@x.io", age = 40, active = false),
            )
        )
    }

    private fun Iterable<Customer>.orderedNames() = map { it.name }

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

    @ExposedRepository(table = Customers::class)
    interface PagingCustomerRepository :
        ExposedCrudRepository<Customer, Long>,
        ListPagingAndSortingRepository<Customer, Long>
}

@Configuration
@EnableExposedRepositories(
    basePackageClasses = [ExposedSpringDataPagingAndSortingRepositoryTest::class],
    considerNestedRepositories = true,
    includeFilters = [
        ComponentScan.Filter(
            type = FilterType.ASSIGNABLE_TYPE,
            classes = [ExposedSpringDataPagingAndSortingRepositoryTest.PagingCustomerRepository::class],
        ),
    ],
)
@EnableTransactionManagement
open class PagingAndSortingTestConfiguration {
    @Bean
    open fun dataSource(): DataSource = EmbeddedDatabaseBuilder()
        .setType(EmbeddedDatabaseType.H2)
        .generateUniqueName(true)
        .build()

    @Bean
    open fun transactionManager(dataSource: DataSource): PlatformTransactionManager =
        SpringTransactionManager(dataSource)
}

/**
 * Part types that have no meaning for a flat entity mapped to a single table (collections, geo, document stores).
 * Repositories using them must fail when the repository is created, not on the first call.
 *
 * The repositories are nested interfaces, so the package scan of [SpringDataExposedTestConfiguration] doesn't pick them up.
 */
class ExposedSpringDataUnsupportedPartTypesTest {

    @ParameterizedTest
    @ValueSource(
        classes = [
            IsEmptyRepository::class,
            IsNotEmptyRepository::class,
            NearRepository::class,
            WithinRepository::class,
            ExistsRepository::class,
        ]
    )
    fun testUnsupportedPartTypeFailsAtCreation(repositoryInterface: Class<*>) {
        val expectedType = repositoryInterface.getAnnotation(ExpectedPartType::class.java).value
        val exception = assertThrows<Exception> { ExposedRepositoryFactory().getRepository(repositoryInterface) }
        val messages = generateSequence<Throwable>(exception) { it.cause }.mapNotNull { it.message }.toList()
        assertTrue(messages.any { expectedType in it }) { "Expected a message mentioning $expectedType, got $messages" }
    }

    @Test
    fun testUnknownPropertyFailsAtCreation() {
        assertThrows<Exception> { ExposedRepositoryFactory().getRepository(UnknownPropertyRepository::class.java) }
    }

    @Test
    fun testNestedPropertyPathFailsAtCreation() {
        assertThrows<Exception> { ExposedRepositoryFactory().getRepository(NestedPathRepository::class.java) }
    }

    @Test
    fun testScrollPositionWithoutWindowReturnTypeFailsAtCreation() {
        assertThrows<Exception> { ExposedRepositoryFactory().getRepository(ScrollWithoutWindowRepository::class.java) }
    }

    @Test
    fun testWindowWithoutScrollPositionFailsAtCreation() {
        assertThrows<Exception> { ExposedRepositoryFactory().getRepository(WindowWithoutScrollPositionRepository::class.java) }
    }

    @Retention(AnnotationRetention.RUNTIME)
    annotation class ExpectedPartType(val value: String)

    @ExpectedPartType("IS_EMPTY")
    @ExposedRepository(table = Customers::class)
    interface IsEmptyRepository : ExposedCrudRepository<Customer, Long> {
        fun findByNicknameIsEmpty(): List<Customer>
    }

    @ExpectedPartType("IS_NOT_EMPTY")
    @ExposedRepository(table = Customers::class)
    interface IsNotEmptyRepository : ExposedCrudRepository<Customer, Long> {
        fun findByNicknameIsNotEmpty(): List<Customer>
    }

    @ExpectedPartType("NEAR")
    @ExposedRepository(table = Customers::class)
    interface NearRepository : ExposedCrudRepository<Customer, Long> {
        fun findByAgeNear(age: Int): List<Customer>
    }

    @ExpectedPartType("WITHIN")
    @ExposedRepository(table = Customers::class)
    interface WithinRepository : ExposedCrudRepository<Customer, Long> {
        fun findByAgeWithin(age: Int): List<Customer>
    }

    @ExpectedPartType("EXISTS")
    @ExposedRepository(table = Customers::class)
    interface ExistsRepository : ExposedCrudRepository<Customer, Long> {
        fun findByNicknameExists(): List<Customer>
    }

    @ExposedRepository(table = Customers::class)
    interface UnknownPropertyRepository : ExposedCrudRepository<Customer, Long> {
        fun findByEmial(email: String): Customer?
    }

    @ExposedRepository(table = Customers::class)
    interface NestedPathRepository : ExposedCrudRepository<Customer, Long> {
        fun findByEmailBytes(bytes: ByteArray): List<Customer>
    }

    @ExposedRepository(table = Customers::class)
    interface ScrollWithoutWindowRepository : ExposedCrudRepository<Customer, Long> {
        fun findFirst2ByAgeGreaterThanOrderByAgeAsc(age: Int, position: ScrollPosition): List<Customer>
    }

    @ExposedRepository(table = Customers::class)
    interface WindowWithoutScrollPositionRepository : ExposedCrudRepository<Customer, Long> {
        fun findFirst2ByAgeGreaterThanOrderByAgeAsc(age: Int): Window<Customer>
    }
}

@Configuration
@EnableExposedRepositories
@EnableTransactionManagement
open class SpringDataExposedTestConfiguration {
    @Bean
    open fun dataSource(): DataSource = EmbeddedDatabaseBuilder()
        .setType(EmbeddedDatabaseType.H2)
        .generateUniqueName(true)
        .build()

    @Bean
    open fun transactionManager(dataSource: DataSource): PlatformTransactionManager =
        SpringTransactionManager(dataSource)
}

object Customers : LongIdTable("customers") {
    val name = varchar("name", 255)
    val email = varchar("email", 255).uniqueIndex()
    val age = integer("age")
    val active = bool("active")
    val nickname = varchar("nickname", 255).nullable()
    val createdAt = timestamp("created_at")
}

data class Customer(
    val id: Long? = null,
    val name: String,
    val email: String,
    val age: Int = 0,
    val active: Boolean = true,
    val nickname: String? = null,
    val createdAt: Instant = Instant.parse("2024-01-01T00:00:00Z"),
)

@Suppress("TooManyFunctions")
@ExposedRepository(table = Customers::class)
interface CustomerRepository : ExposedCrudRepository<Customer, Long> {
    // SIMPLE_PROPERTY
    fun findByEmail(email: String): Customer?
    fun findByName(name: String): List<Customer>
    fun findByAgeIs(age: Int): List<Customer>
    fun findByNickname(nickname: String?): List<Customer>
    fun findAllByIdEquals(id: Long): List<Customer>

    // NEGATING_SIMPLE_PROPERTY
    fun findByNameNot(name: String): List<Customer>
    fun findByNameIsNot(name: String): List<Customer>

    // BETWEEN
    fun findByAgeBetween(from: Int, to: Int): List<Customer>
    fun findByCreatedAtBetween(from: Instant, to: Instant): List<Customer>

    // IS_NULL / IS_NOT_NULL
    fun findByNicknameIsNull(): List<Customer>
    fun findByNicknameNull(): List<Customer>
    fun findByNicknameIsNotNull(): List<Customer>
    fun findByNicknameNotNull(): List<Customer>

    // LESS_THAN / LESS_THAN_EQUAL / GREATER_THAN / GREATER_THAN_EQUAL
    fun findByAgeLessThan(age: Int): List<Customer>
    fun findByAgeIsLessThan(age: Int): List<Customer>
    fun findByAgeLessThanEqual(age: Int): List<Customer>
    fun findByAgeGreaterThan(age: Int): List<Customer>
    fun findByAgeIsGreaterThan(age: Int): List<Customer>
    fun findByAgeGreaterThanEqual(age: Int): List<Customer>

    // BEFORE / AFTER
    fun findByCreatedAtBefore(t: Instant): List<Customer>
    fun findByCreatedAtIsBefore(t: Instant): List<Customer>
    fun findByCreatedAtAfter(t: Instant): List<Customer>
    fun findByCreatedAtIsAfter(t: Instant): List<Customer>
    fun countByCreatedAtAfter(t: Instant): Long

    // LIKE / NOT_LIKE
    fun findByNameLike(pattern: String): List<Customer>
    fun findByNameIsLike(pattern: String): List<Customer>
    fun findByNameNotLike(pattern: String): List<Customer>
    fun findByNameIsNotLike(pattern: String): List<Customer>

    // STARTING_WITH / ENDING_WITH / CONTAINING / NOT_CONTAINING
    fun findByEmailStartingWith(prefix: String): List<Customer>
    fun findByEmailStartsWith(prefix: String): List<Customer>
    fun findByEmailEndingWith(suffix: String): List<Customer>
    fun findByEmailEndsWith(suffix: String): List<Customer>
    fun findByNameContaining(part: String): List<Customer>
    fun findByNameContains(part: String): List<Customer>
    fun findByNameNotContaining(part: String): List<Customer>
    fun findByNameNotContains(part: String): List<Customer>
    fun findByNicknameContaining(part: String): List<Customer>
    fun findByNicknameStartingWith(prefix: String): List<Customer>
    fun findByNicknameEndingWith(suffix: String): List<Customer>

    // IN / NOT_IN
    fun findByAgeIn(ages: Collection<Int>): List<Customer>
    fun findByAgeIsIn(ages: Collection<Int>): List<Customer>
    fun findByAgeNotIn(ages: Collection<Int>): List<Customer>
    fun findByAgeIsNotIn(ages: Collection<Int>): List<Customer>
    fun findByIdIn(ids: Collection<Long>): List<Customer>

    // REGEX
    fun findByEmailMatches(regex: String): List<Customer>
    fun findByEmailRegex(regex: String): List<Customer>
    fun findByEmailMatchesRegex(regex: String): List<Customer>

    // TRUE / FALSE
    fun findByActiveTrue(): List<Customer>
    fun findByActiveIsTrue(): List<Customer>
    fun findByActiveFalse(): List<Customer>
    fun findByActiveIsFalse(): List<Customer>

    // IgnoreCase
    fun findByNameIgnoreCase(name: String): List<Customer>
    fun findByNameContainingIgnoreCase(name: String): List<Customer>
    fun findByEmailStartingWithIgnoreCase(prefix: String): List<Customer>
    fun findByEmailEndingWithIgnoreCase(suffix: String): List<Customer>
    fun findByNameAndEmailAllIgnoreCase(name: String, email: String): List<Customer>

    // And / Or
    fun findByActiveTrueAndAgeGreaterThan(age: Int): List<Customer>
    fun findByNameOrEmail(name: String, email: String): List<Customer>
    fun findByActiveTrueAndAgeGreaterThanOrName(age: Int, name: String): List<Customer>

    // Subject prefixes
    fun readByName(name: String): List<Customer>
    fun getByName(name: String): Customer?
    fun queryByName(name: String): List<Customer>
    fun searchByName(name: String): List<Customer>
    fun streamByName(name: String): Stream<Customer>
    fun findAllByName(name: String): List<Customer>
    fun findCustomersByName(name: String): List<Customer>

    // Single-result return types
    fun findOneByEmail(email: String): Optional<Customer>
    fun findByActive(active: Boolean): Customer?

    // count
    fun countByActiveTrue(): Long
    fun countByName(name: String): Long
    fun countByAgeLessThan(age: Int): Int

    // exists
    fun existsByEmail(email: String): Boolean
    fun existsByAgeGreaterThan(age: Int): Boolean

    // delete / remove
    @Transactional
    fun deleteByActiveFalse(): Long

    @Transactional
    fun removeByName(name: String): Long

    @Transactional
    fun deleteByAge(age: Int)

    @Transactional
    fun deleteByAgeGreaterThanEqual(age: Int): List<Customer>

    // First / Top
    fun findFirstByOrderByAgeAsc(): Customer?
    fun findTopByOrderByAgeDesc(): Customer?
    fun findTop2ByOrderByAgeDesc(): List<Customer>
    fun findFirst2ByOrderByAgeAsc(): List<Customer>
    fun findTop3ByActiveFalseOrderByAgeAsc(): List<Customer>
    fun findFirstByNameOrderByAgeAsc(name: String): Customer?

    // Distinct
    fun findDistinctByActiveTrue(): List<Customer>
    fun findCustomersDistinctByActiveTrue(): List<Customer>

    // OrderBy
    fun findByActiveTrueOrderByAgeDesc(): List<Customer>
    fun findByActiveTrueOrderByAgeAsc(): List<Customer>
    fun findAllByOrderByAge(): List<Customer>
    fun findAllByOrderByActiveAscAgeDesc(): List<Customer>

    // Sort / Limit parameters
    fun findAllByAgeGreaterThan(age: Int, sort: Sort): List<Customer>
    fun findLimitedByAgeGreaterThan(age: Int, limit: Limit, sort: Sort): List<Customer>

    // Pageable
    fun findPageByAgeGreaterThan(age: Int, pageable: Pageable): Page<Customer>
    fun findByNameContainingIgnoreCase(name: String, pageable: Pageable): Page<Customer>
    fun findSliceByAgeGreaterThan(age: Int, pageable: Pageable): Slice<Customer>
    fun findListByAgeGreaterThan(age: Int, pageable: Pageable): List<Customer>
    fun findByAgeGreaterThanOrderByActiveAsc(age: Int, pageable: Pageable): Page<Customer>

    // Scrolling
    fun findFirst2ByAgeGreaterThanOrderByAgeAsc(age: Int, position: ScrollPosition): Window<Customer>
    fun findFirst2ByAgeGreaterThanOrderByAgeDesc(age: Int, position: ScrollPosition): Window<Customer>
    fun findFirstByAgeGreaterThanOrderByActiveAsc(age: Int, position: ScrollPosition): Window<Customer>
    fun findFirstByActiveTrueOrderByAgeAsc(position: ScrollPosition): Window<Customer>
    fun findWindowByAgeGreaterThan(age: Int, position: ScrollPosition, limit: Limit, sort: Sort): Window<Customer>
    fun findByAgeGreaterThanOrderByAgeAsc(age: Int, position: ScrollPosition): Window<Customer>
}
