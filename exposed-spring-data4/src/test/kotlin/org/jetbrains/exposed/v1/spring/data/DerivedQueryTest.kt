package org.jetbrains.exposed.v1.spring.data

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertNull
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.dao.IncorrectResultSizeDataAccessException
import org.springframework.data.domain.Limit
import org.springframework.data.domain.Sort
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.*
import java.util.stream.Stream

/**
 * Derived queries without paging or scrolling: every supported `Part.Type`, the subject prefixes
 * (`find`, `count`, `exists`, `delete`, ...), `First`/`Top`, `Distinct`, `OrderBy` and the `Sort` / `Limit` parameters.
 */
open class DerivedQueryTest : ExposedSpringDataTestBase() {

    @Autowired
    override lateinit var repository: QueryCustomerRepository

    // ---- SIMPLE_PROPERTY ----

    @Test
    fun testFindByEmail() {
        repository.save(Customer(name = "Ann", email = "ann@x.io"))
        assertEquals("Ann", repository.findByEmail("ann@x.io")?.name)
        assertNull(repository.findByEmail("nobody@x.io"))
    }

    @Test
    fun testSimplePropertyReturningList() {
        seed()
        assertEquals(listOf("Bob"), repository.findByName("Bob").names())
        assertEquals(emptyList<String>(), repository.findByName("Nobody").names())
    }

    @Test
    fun testSimplePropertyWithIsKeyword() {
        seed()
        assertEquals(listOf("Carla"), repository.findByAgeIs(35).names())
    }

    @Test
    fun testSimplePropertyWithNullArgumentMeansIsNull() {
        seed()
        assertEquals(listOf("Bob"), repository.findByNickname(null).names())
    }

    @Test
    fun testSimplePropertyOnIdColumn() {
        val ann = seed().first()
        assertEquals(listOf("Ann"), repository.findAllByIdEquals(ann.id!!).names())
    }

    // ---- NEGATING_SIMPLE_PROPERTY ----

    @Test
    fun testNegatingSimpleProperty() {
        seed()
        assertEquals(listOf("Bob", "Carla", "Dan"), repository.findByNameNot("Ann").names())
        assertEquals(listOf("Bob", "Carla", "Dan"), repository.findByNameIsNot("Ann").names())
    }

    // ---- BETWEEN ----

    @Test
    fun testBetweenIsInclusive() {
        seed()
        assertEquals(listOf("Bob", "Carla"), repository.findByAgeBetween(30, 35).names())
        assertEquals(listOf("Ann", "Bob"), repository.findByCreatedAtBetween(JAN, FEB).names())
    }

    // ---- IS_NULL / IS_NOT_NULL ----

    @Test
    fun testIsNull() {
        seed()
        assertEquals(listOf("Bob"), repository.findByNicknameIsNull().names())
        assertEquals(listOf("Bob"), repository.findByNicknameNull().names())
    }

    @Test
    fun testIsNotNull() {
        seed()
        assertEquals(listOf("Ann", "Carla", "Dan"), repository.findByNicknameIsNotNull().names())
        assertEquals(listOf("Ann", "Carla", "Dan"), repository.findByNicknameNotNull().names())
    }

    // ---- LESS_THAN / LESS_THAN_EQUAL / GREATER_THAN / GREATER_THAN_EQUAL ----

    @Test
    fun testLessThan() {
        seed()
        assertEquals(listOf("Ann"), repository.findByAgeLessThan(30).names())
        assertEquals(listOf("Ann"), repository.findByAgeIsLessThan(30).names())
    }

    @Test
    fun testLessThanEqual() {
        seed()
        assertEquals(listOf("Ann", "Bob"), repository.findByAgeLessThanEqual(30).names())
    }

    @Test
    fun testGreaterThan() {
        seed()
        assertEquals(listOf("Dan"), repository.findByAgeGreaterThan(35).names())
        assertEquals(listOf("Dan"), repository.findByAgeIsGreaterThan(35).names())
    }

    @Test
    fun testGreaterThanEqual() {
        seed()
        assertEquals(listOf("Carla", "Dan"), repository.findByAgeGreaterThanEqual(35).names())
    }

    // ---- BEFORE / AFTER ----

    @Test
    fun testBefore() {
        seed()
        assertEquals(listOf("Ann"), repository.findByCreatedAtBefore(FEB).names())
        assertEquals(listOf("Ann"), repository.findByCreatedAtIsBefore(FEB).names())
    }

    @Test
    fun testAfter() {
        seed()
        assertEquals(listOf("Dan"), repository.findByCreatedAtAfter(MAR).names())
        assertEquals(listOf("Dan"), repository.findByCreatedAtIsAfter(MAR).names())
    }

    @Test
    fun testCountByCreatedAtAfter() {
        seed()
        assertEquals(2L, repository.countByCreatedAtAfter(FEB))
    }

    // ---- LIKE / NOT_LIKE (pattern is passed through as is) ----

    @Test
    fun testLikePassesPatternThrough() {
        seed()
        assertEquals(listOf("Carla", "Dan"), repository.findByNameLike("%a%").names())
        assertEquals(listOf("Bob"), repository.findByNameIsLike("B_b").names())
    }

    @Test
    fun testNotLike() {
        seed()
        assertEquals(listOf("Ann", "Bob"), repository.findByNameNotLike("%a%").names())
        assertEquals(listOf("Ann", "Bob"), repository.findByNameIsNotLike("%a%").names())
    }

    // ---- STARTING_WITH / ENDING_WITH / CONTAINING / NOT_CONTAINING (argument is escaped) ----

    @Test
    fun testStartingWith() {
        seed()
        assertEquals(listOf("Carla"), repository.findByEmailStartingWith("ca").names())
        assertEquals(listOf("Carla"), repository.findByEmailStartsWith("ca").names())
    }

    @Test
    fun testEndingWith() {
        seed()
        assertEquals(listOf("Ann", "Bob", "Dan"), repository.findByEmailEndingWith("@x.io").names())
        assertEquals(listOf("Ann", "Bob", "Dan"), repository.findByEmailEndsWith("@x.io").names())
    }

    @Test
    fun testContaining() {
        seed()
        assertEquals(listOf("Carla", "Dan"), repository.findByNameContaining("a").names())
        assertEquals(listOf("Carla", "Dan"), repository.findByNameContains("a").names())
    }

    @Test
    fun testNotContaining() {
        seed()
        assertEquals(listOf("Ann", "Bob"), repository.findByNameNotContaining("a").names())
        assertEquals(listOf("Ann", "Bob"), repository.findByNameNotContains("a").names())
    }

    @Test
    fun testWildcardsInArgumentAreEscaped() {
        seed()
        // unescaped, "_" would match any single character and "%" anything
        assertEquals(listOf("Carla"), repository.findByNicknameContaining("_").names())
        assertEquals(listOf("Dan"), repository.findByNicknameContaining("%").names())
        assertEquals(listOf("Dan"), repository.findByNicknameStartingWith("50%").names())
        assertEquals(listOf("Carla"), repository.findByNicknameEndingWith("_la").names())
    }

    // ---- IN / NOT_IN ----

    @Test
    fun testIn() {
        seed()
        assertEquals(listOf("Ann", "Dan"), repository.findByAgeIn(listOf(25, 40)).names())
        assertEquals(listOf("Ann", "Dan"), repository.findByAgeIsIn(listOf(25, 40)).names())
    }

    @Test
    fun testInWithEmptyCollectionMatchesNothing() {
        seed()
        assertEquals(emptyList<String>(), repository.findByAgeIn(emptyList()).names())
    }

    @Test
    fun testInOnIdColumn() {
        val (ann, _, carla) = seed()
        assertEquals(listOf("Ann", "Carla"), repository.findByIdIn(listOf(ann.id!!, carla.id!!)).names())
    }

    @Test
    fun testNotIn() {
        seed()
        assertEquals(listOf("Bob", "Carla"), repository.findByAgeNotIn(listOf(25, 40)).names())
        assertEquals(listOf("Bob", "Carla"), repository.findByAgeIsNotIn(listOf(25, 40)).names())
    }

    // ---- REGEX ----

    @Test
    fun testRegex() {
        seed()
        assertEquals(listOf("Ann", "Bob"), repository.findByEmailMatches("^[a-b].*@x\\.io$").names())
        assertEquals(listOf("Ann", "Bob"), repository.findByEmailRegex("^[a-b].*@x\\.io$").names())
        assertEquals(listOf("Ann", "Bob"), repository.findByEmailMatchesRegex("^[a-b].*@x\\.io$").names())
    }

    // ---- TRUE / FALSE ----

    @Test
    fun testTrue() {
        seed()
        assertEquals(listOf("Ann", "Carla"), repository.findByActiveTrue().names())
        assertEquals(listOf("Ann", "Carla"), repository.findByActiveIsTrue().names())
    }

    @Test
    fun testFalse() {
        seed()
        assertEquals(listOf("Bob", "Dan"), repository.findByActiveFalse().names())
        assertEquals(listOf("Bob", "Dan"), repository.findByActiveIsFalse().names())
    }

    // ---- IgnoreCase ----

    @Test
    fun testIgnoreCaseOnSimpleProperty() {
        seed()
        assertEquals(listOf("Ann"), repository.findByNameIgnoreCase("aNN").names())
        assertEquals(emptyList<String>(), repository.findByName("aNN").names())
    }

    @Test
    fun testIgnoreCaseOnLikeVariants() {
        seed()
        assertEquals(listOf("Ann", "Carla", "Dan"), repository.findByNameContainingIgnoreCase("A").names())
        assertEquals(listOf("Ann"), repository.findByEmailStartingWithIgnoreCase("ANN").names())
        assertEquals(listOf("Carla"), repository.findByEmailEndingWithIgnoreCase("EXAMPLE.COM").names())
    }

    @Test
    fun testAllIgnoreCase() {
        seed()
        assertEquals(listOf("Ann"), repository.findByNameAndEmailAllIgnoreCase("ANN", "Ann@X.io").names())
    }

    // ---- And / Or ----

    @Test
    fun testAnd() {
        seed()
        assertEquals(listOf("Carla"), repository.findByActiveTrueAndAgeGreaterThan(30).names())
    }

    @Test
    fun testOr() {
        seed()
        assertEquals(listOf("Ann", "Bob"), repository.findByNameOrEmail("Ann", "bob@x.io").names())
    }

    @Test
    fun testAndBindsStrongerThanOr() {
        seed()
        // (active = true AND age > 30) OR name = 'Bob'
        assertEquals(listOf("Bob", "Carla"), repository.findByActiveTrueAndAgeGreaterThanOrName(30, "Bob").names())
    }

    // ---- Subject prefixes: find / read / get / query / search / stream ----

    @Test
    fun testAllFindPrefixesBehaveTheSame() {
        seed()
        assertEquals(listOf("Bob"), repository.readByName("Bob").names())
        assertEquals("Bob", repository.getByName("Bob")?.name)
        assertEquals(listOf("Bob"), repository.queryByName("Bob").names())
        assertEquals(listOf("Bob"), repository.searchByName("Bob").names())
        assertEquals(listOf("Bob"), repository.streamByName("Bob").use { it.toList() }.names())
    }

    @Test
    fun testTextBetweenPrefixAndByIsIgnored() {
        seed()
        assertEquals(listOf("Bob"), repository.findAllByName("Bob").names())
        assertEquals(listOf("Bob"), repository.findCustomersByName("Bob").names())
    }

    // ---- Single-result return types ----

    @Test
    fun testOptionalReturnType() {
        seed()
        assertEquals("Ann", repository.findOneByEmail("ann@x.io").get().name)
        assertTrue(repository.findOneByEmail("nobody@x.io").isEmpty)
    }

    @Test
    fun testSingleResultWithMoreThanOneRowThrows() {
        seed()
        assertThrows<IncorrectResultSizeDataAccessException> { repository.findByActive(true) }
    }

    // ---- count ----

    @Test
    fun testCount() {
        seed()
        assertEquals(2L, repository.countByActiveTrue())
        assertEquals(0L, repository.countByName("Nobody"))
    }

    @Test
    fun testCountWithIntReturnType() {
        seed()
        assertEquals(2, repository.countByAgeLessThan(35))
    }

    // ---- exists ----

    @Test
    fun testExists() {
        seed()
        assertTrue(repository.existsByEmail("ann@x.io"))
        assertFalse(repository.existsByEmail("nobody@x.io"))
    }

    @Test
    fun testExistsWithMultipleMatches() {
        seed()
        assertTrue(repository.existsByAgeGreaterThan(20))
        assertFalse(repository.existsByAgeGreaterThan(40))
    }

    // ---- delete / remove ----

    @Test
    fun testDeleteReturnsNumberOfDeletedRows() {
        seed()
        assertEquals(2L, repository.deleteByActiveFalse())
        assertEquals(listOf("Ann", "Carla"), repository.findAll().names())
    }

    @Test
    fun testRemovePrefix() {
        seed()
        assertEquals(1L, repository.removeByName("Bob"))
        assertEquals(listOf("Ann", "Carla", "Dan"), repository.findAll().names())
    }

    @Test
    fun testDeleteWithUnitReturnType() {
        seed()
        repository.deleteByAge(25)
        assertEquals(listOf("Bob", "Carla", "Dan"), repository.findAll().names())
    }

    @Test
    fun testDeleteReturningDeletedEntities() {
        seed()
        assertEquals(listOf("Carla", "Dan"), repository.deleteByAgeGreaterThanEqual(35).names())
        assertEquals(listOf("Ann", "Bob"), repository.findAll().names())
    }

    @Test
    fun testDeleteWithoutMatchDeletesNothing() {
        seed()
        assertEquals(0L, repository.removeByName("Nobody"))
        assertEquals(4L, repository.count())
    }

    // ---- First / Top ----

    @Test
    fun testFirstAndTopWithoutNumberReturnOneRow() {
        seed()
        assertEquals("Ann", repository.findFirstByOrderByAgeAsc()?.name)
        assertEquals("Dan", repository.findTopByOrderByAgeDesc()?.name)
    }

    @Test
    fun testTopWithNumber() {
        seed()
        assertEquals(listOf("Dan", "Carla"), repository.findTop2ByOrderByAgeDesc().orderedNames())
        assertEquals(listOf("Ann", "Bob"), repository.findFirst2ByOrderByAgeAsc().orderedNames())
    }

    @Test
    fun testTopWithMoreThanAvailableRows() {
        seed()
        assertEquals(listOf("Bob", "Dan"), repository.findTop3ByActiveFalseOrderByAgeAsc().orderedNames())
    }

    @Test
    fun testFirstOnEmptyResult() {
        seed()
        assertNull(repository.findFirstByNameOrderByAgeAsc("Nobody"))
    }

    // ---- Distinct ----

    @Test
    fun testDistinct() {
        seed()
        assertEquals(listOf("Ann", "Carla"), repository.findDistinctByActiveTrue().names())
        assertEquals(listOf("Ann", "Carla"), repository.findCustomersDistinctByActiveTrue().names())
    }

    // ---- OrderBy ----

    @Test
    fun testOrderBy() {
        seed()
        assertEquals(listOf("Carla", "Ann"), repository.findByActiveTrueOrderByAgeDesc().orderedNames())
        assertEquals(listOf("Ann", "Carla"), repository.findByActiveTrueOrderByAgeAsc().orderedNames())
    }

    @Test
    fun testOrderByWithoutDirectionIsAscending() {
        seed()
        assertEquals(listOf("Ann", "Bob", "Carla", "Dan"), repository.findAllByOrderByAge().orderedNames())
    }

    @Test
    fun testOrderByMultipleProperties() {
        seed()
        assertEquals(listOf("Dan", "Bob", "Carla", "Ann"), repository.findAllByOrderByActiveAscAgeDesc().orderedNames())
    }

    // ---- Sort parameter ----

    @Test
    fun testSortParameter() {
        seed()
        assertEquals(
            listOf("Dan", "Carla", "Bob"),
            repository.findAllByAgeGreaterThan(25, Sort.by("age").descending()).orderedNames()
        )
        assertEquals(
            listOf("Bob", "Carla", "Dan"),
            repository.findAllByAgeGreaterThan(25, Sort.by(Sort.Direction.ASC, "name")).orderedNames()
        )
    }

    @Test
    fun testUnsortedSortParameter() {
        seed()
        assertEquals(listOf("Bob", "Carla", "Dan"), repository.findAllByAgeGreaterThan(25, Sort.unsorted()).names())
    }

    @Test
    fun testSortParameterWithMultipleProperties() {
        seed()
        val sort = Sort.by(Sort.Order.asc("active"), Sort.Order.desc("age"))
        assertEquals(listOf("Dan", "Bob", "Carla", "Ann"), repository.findAllByAgeGreaterThan(0, sort).orderedNames())
    }

    @Test
    fun testSortByUnknownPropertyFails() {
        seed()
        assertThrows<Exception> { repository.findAllByAgeGreaterThan(0, Sort.by("doesNotExist")) }
    }

    // ---- Limit parameter ----

    @Test
    fun testLimitParameter() {
        seed()
        assertEquals(listOf("Ann", "Bob"), repository.findLimitedByAgeGreaterThan(0, Limit.of(2), Sort.by("age")).orderedNames())
        assertEquals(4, repository.findLimitedByAgeGreaterThan(0, Limit.unlimited(), Sort.by("age")).size)
    }
}

@Suppress("TooManyFunctions")
@ExposedRepository(table = Customers::class)
interface QueryCustomerRepository : ExposedCrudRepository<Customer, Long> {
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
}
