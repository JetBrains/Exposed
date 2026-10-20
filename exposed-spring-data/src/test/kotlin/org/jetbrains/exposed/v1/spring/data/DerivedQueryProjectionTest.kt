package org.jetbrains.exposed.v1.spring.data

import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.spring.data.repository.ExposedRepositoryFactory
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.dao.IncorrectResultSizeDataAccessException
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.ScrollPosition
import org.springframework.data.domain.Slice
import org.springframework.data.domain.Sort
import org.springframework.data.domain.Window
import org.springframework.data.support.WindowIterator
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.util.Optional
import java.util.stream.Stream

/**
 * Derived queries that return a projection instead of the entity:
 * DTO (class) projections, closed interface projections, dynamic `Class<T>` projections and raw [ResultRow]s.
 *
 * Besides the mapped values, these tests check that only the columns the projection needs are selected,
 * and that invalid projections make the repository fail when it is created.
 */
open class DerivedQueryProjectionTest : ExposedSpringDataTestBase() {

    @Autowired
    override lateinit var repository: ProjectionCustomerRepository

    // ---- DTO projections: results ----

    @Test
    fun testDtoSingleResult() {
        seed()
        assertEquals(CustomerName("Carla"), repository.findByEmail("carla@example.com"))
    }

    @Test
    fun testDtoSingleResultMissingIsNull() {
        seed()
        assertNull(repository.findByEmail("nobody@x.io"))
    }

    @Test
    fun testDtoSingleResultMoreThanOneRowFails() {
        seed()
        assertThrows<IncorrectResultSizeDataAccessException> { repository.findNameByActive(true) }
    }

    @Test
    fun testDtoOptionalResult() {
        seed()
        assertEquals(Optional.of(CustomerName("Ann")), repository.findOptionalByEmail("ann@x.io"))
        assertEquals(Optional.empty<CustomerName>(), repository.findOptionalByEmail("nobody@x.io"))
    }

    @Test
    fun testDtoListWithSeveralColumns() {
        seed()
        assertEquals(
            listOf(CustomerSummary("Carla", "carla@example.com", 35), CustomerSummary("Dan", "dan@x.io", 40)),
            repository.findByAgeGreaterThanOrderByAgeAsc(30),
        )
    }

    @Test
    fun testDtoListEmpty() {
        seed()
        assertTrue(repository.findByAgeGreaterThanOrderByAgeAsc(100).isEmpty())
    }

    @Test
    fun testDtoMapsNullableColumn() {
        seed()
        assertEquals(
            listOf(
                CustomerNickname("Ann", "annie"),
                CustomerNickname("Bob", null),
                CustomerNickname("Carla", "car_la"),
                CustomerNickname("Dan", "50%off"),
            ),
            repository.findAllByOrderByAgeAsc(),
        )
    }

    @Test
    fun testDtoMapsIdColumnToRawValue() {
        val saved = seed()
        assertEquals(CustomerRef(saved.first { it.name == "Bob" }.id!!, "Bob"), repository.findRefByEmail("bob@x.io"))
    }

    @Test
    fun testDtoWithBooleanColumn() {
        seed()
        assertEquals(
            listOf(CustomerActivity(true), CustomerActivity(false), CustomerActivity(true), CustomerActivity(false)),
            repository.findActivityByAgeGreaterThanOrderByAgeAsc(0),
        )
    }

    @Test
    fun testDtoWithConstructorDefaultForUnmappedOptionalParameter() {
        seed()
        // `greeting` has a default value and no column, so the projection only needs `name`
        assertEquals(CustomerGreeting("Ann"), repository.findGreetingByEmail("ann@x.io"))
        assertEquals("hello", repository.findGreetingByEmail("ann@x.io")!!.greeting)
    }

    // ---- DTO projections: modifiers ----

    @Test
    fun testDtoFirst() {
        seed()
        assertEquals(listOf(CustomerName("Dan"), CustomerName("Carla")), repository.findFirst2ByOrderByAgeDesc())
    }

    @Test
    fun testDtoDistinctRemovesDuplicates() {
        seed()
        // unlike full entities, a projection without the id can contain duplicates that DISTINCT removes
        assertEquals(
            setOf(CustomerActivity(true), CustomerActivity(false)),
            repository.findDistinctByAgeGreaterThan(0).toSet(),
        )
        assertEquals(2, repository.findDistinctByAgeGreaterThan(0).size)
    }

    @Test
    fun testDtoSortParameterOnPropertyThatIsNotProjected() {
        seed()
        assertEquals(
            listOf(CustomerName("Dan"), CustomerName("Bob")),
            repository.findByActive(false, Sort.by(Sort.Direction.DESC, "age")),
        )
    }

    @Test
    fun testDtoStream() {
        seed()
        repository.streamByActiveOrderByAgeAsc(true).use { stream ->
            assertEquals(listOf(CustomerName("Ann"), CustomerName("Carla")), stream.toList())
        }
    }

    // ---- DTO projections: paging and scrolling ----

    @Test
    fun testDtoPage() {
        seed()
        val page = repository.findPageByAgeGreaterThanOrderByAgeAsc(0, PageRequest.of(0, 3))
        assertEquals(listOf(CustomerName("Ann"), CustomerName("Bob"), CustomerName("Carla")), page.content)
        assertEquals(4, page.totalElements)
        assertEquals(2, page.totalPages)
        assertTrue(page.hasNext())
    }

    @Test
    fun testDtoPageTotalCountsOnlyMatchingRows() {
        seed()
        val page = repository.findPageByAgeGreaterThanOrderByAgeAsc(28, PageRequest.of(1, 2))
        assertEquals(listOf(CustomerName("Dan")), page.content)
        assertEquals(3, page.totalElements)
        assertFalse(page.hasNext())
    }

    @Test
    fun testDtoSlice() {
        seed()
        val slice = repository.findSliceByAgeGreaterThanOrderByAgeAsc(0, PageRequest.of(0, 2))
        assertEquals(listOf(CustomerName("Ann"), CustomerName("Bob")), slice.content)
        assertTrue(slice.hasNext())
    }

    @Test
    fun testDtoKeysetScrollWithSortKeysThatAreNotProjected() {
        seed()
        val first = repository.findFirst2ByAgeGreaterThanOrderByAgeAsc(0, ScrollPosition.keyset())
        assertEquals(listOf(CustomerName("Ann"), CustomerName("Bob")), first.content)

        val second = repository.findFirst2ByAgeGreaterThanOrderByAgeAsc(0, first.positionAt(first.size() - 1))
        assertEquals(listOf(CustomerName("Carla"), CustomerName("Dan")), second.content)
        assertFalse(second.hasNext())
    }

    @Test
    fun testDtoWindowIteratorWithKeyset() {
        seed()
        val names = WindowIterator.of<CustomerName> { repository.findFirst2ByAgeGreaterThanOrderByAgeAsc(0, it) }
            .startingAt(ScrollPosition.keyset())
            .asSequence()
            .map { it.name }
            .toList()
        assertEquals(listOf("Ann", "Bob", "Carla", "Dan"), names)
    }

    @Test
    fun testDtoOffsetScroll() {
        seed()
        val window = repository.findFirst2ByAgeGreaterThanOrderByAgeAsc(0, ScrollPosition.offset(1))
        assertEquals(listOf(CustomerName("Carla"), CustomerName("Dan")), window.content)
    }

    // ---- Interface projections ----

    @Test
    fun testInterfaceSingleResult() {
        seed()
        val view = repository.findViewByEmail("dan@x.io")
        assertEquals("Dan", view?.name)
    }

    @Test
    fun testInterfaceSingleResultMissingIsNull() {
        seed()
        assertNull(repository.findViewByEmail("nobody@x.io"))
    }

    @Test
    fun testInterfaceListWithSeveralProperties() {
        seed()
        val views = repository.findByActiveOrderByAgeAsc(true)
        assertEquals(listOf("Ann", "Carla"), views.map { it.name })
        assertEquals(listOf("ann@x.io", "carla@example.com"), views.map { it.email })
        assertEquals(listOf(25, 35), views.map { it.age })
    }

    @Test
    fun testInterfaceMapsNullableProperty() {
        seed()
        assertEquals(listOf("annie", null, "car_la", "50%off"), repository.findNicknameViewsByAgeGreaterThanOrderByAgeAsc(0).map { it.nickname })
    }

    @Test
    fun testInterfaceMapsIdToRawValue() {
        val saved = seed()
        assertEquals(saved.first { it.name == "Carla" }.id, repository.findIdViewByEmail("carla@example.com")?.id)
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    fun testInterfaceProjectionIsUsableAfterTheTransaction() {
        seed()
        val views = repository.findByActiveOrderByAgeAsc(false)
        // the values are copied out of the row, not read lazily
        assertEquals(listOf("Bob", "Dan"), views.map { it.name })
    }

    @Test
    fun testInterfacePage() {
        seed()
        val page = repository.findViewPageByAgeGreaterThanOrderByAgeAsc(0, PageRequest.of(1, 3))
        assertEquals(listOf("Dan"), page.content.map { it.name })
        assertEquals(4, page.totalElements)
    }

    // ---- Dynamic projections ----

    @Test
    fun testDynamicProjectionToDto() {
        seed()
        assertEquals(CustomerName("Bob"), repository.findByEmail("bob@x.io", CustomerName::class.java))
    }

    @Test
    fun testDynamicProjectionToInterface() {
        seed()
        assertEquals("Bob", repository.findByEmail("bob@x.io", NameView::class.java)?.name)
    }

    @Test
    fun testDynamicProjectionToEntity() {
        seed()
        val customer = repository.findByEmail("bob@x.io", Customer::class.java)
        assertEquals("Bob", customer?.name)
        assertEquals(30, customer?.age)
    }

    @Test
    fun testDynamicProjectionMissingIsNull() {
        seed()
        assertNull(repository.findByEmail("nobody@x.io", CustomerName::class.java))
    }

    @Test
    fun testDynamicProjectionList() {
        seed()
        assertEquals(
            listOf(CustomerSummary("Ann", "ann@x.io", 25), CustomerSummary("Bob", "bob@x.io", 30)),
            repository.findByAgeLessThanOrderByAgeAsc(35, CustomerSummary::class.java),
        )
    }

    @Test
    fun testDynamicProjectionWithUnknownPropertyFailsOnCall() {
        seed()
        // dynamic projections can't be checked at startup, so the error comes at call time
        assertThrows<Exception> { repository.findByEmail("bob@x.io", CustomerWithUnknownProperty::class.java) }
    }

    // ---- Raw ResultRow ----

    @Test
    fun testResultRowIsReturnedUnmapped() {
        seed()
        val row = repository.findRowByEmail("ann@x.io")
        assertEquals("Ann", row?.get(Customers.name))
        assertEquals(25, row?.get(Customers.age))
    }

    @Test
    fun testResultRowList() {
        seed()
        assertEquals(listOf("Carla", "Dan"), repository.findRowsByAgeGreaterThanOrderByAgeAsc(30).map { it[Customers.name] })
    }

    // ---- Entity results are unaffected ----

    @Test
    fun testEntityReturnTypeStillMapsFullEntity() {
        seed()
        val customer = repository.findCustomerByEmail("carla@example.com")
        assertEquals("car_la", customer?.nickname)
        assertEquals(MAR, customer?.createdAt)
    }

    // ---- Selected columns ----

    @Test
    fun testDtoSelectsOnlyProjectedColumns() {
        seed()
        val statements = captureSelects { repository.findByEmail("ann@x.io") }
        assertEquals(setOf("name"), selectedColumns(statements.single()))
    }

    @Test
    fun testDtoWithSeveralColumnsSelectsExactlyThose() {
        seed()
        val statements = captureSelects { repository.findByAgeGreaterThanOrderByAgeAsc(30) }
        assertEquals(setOf("name", "email", "age"), selectedColumns(statements.single()))
    }

    @Test
    fun testInterfaceSelectsOnlyProjectedColumns() {
        seed()
        val statements = captureSelects { repository.findNicknameViewsByAgeGreaterThanOrderByAgeAsc(0) }
        assertEquals(setOf("nickname"), selectedColumns(statements.single()))
    }

    @Test
    fun testDynamicProjectionSelectsOnlyProjectedColumns() {
        seed()
        val statements = captureSelects { repository.findByEmail("ann@x.io", CustomerName::class.java) }
        assertEquals(setOf("name"), selectedColumns(statements.single()))
    }

    @Test
    fun testKeysetScrollSelectsSortKeysInAddition() {
        seed()
        val statements = captureSelects { repository.findFirst2ByAgeGreaterThanOrderByAgeAsc(0, ScrollPosition.keyset()) }
        assertEquals(setOf("name", "age", "id"), selectedColumns(statements.single()))
    }

    @Test
    fun testEntitySelectsAllColumns() {
        seed()
        val statements = captureSelects { repository.findCustomerByEmail("ann@x.io") }
        assertEquals(Customers.columns.map { it.name.lowercase() }.toSet(), selectedColumns(statements.single()))
    }

    // ---- Transactions ----

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    fun testProjectionOpensItsOwnTransaction() {
        seed()
        assertEquals(CustomerName("Ann"), repository.findByEmail("ann@x.io"))
        assertEquals("Ann", repository.findViewByEmail("ann@x.io")?.name)
    }

    // ---- Invalid projections fail when the repository is created ----

    @Test
    fun testDtoWithPropertyWithoutColumnFailsAtCreation() {
        val error = assertThrows<Exception> { ExposedRepositoryFactory().getRepository(UnknownDtoPropertyRepository::class.java) }
        assertTrue(error.stackTraceToString().contains("unknown")) { error.stackTraceToString() }
    }

    @Test
    fun testInterfaceWithPropertyWithoutColumnFailsAtCreation() {
        val error = assertThrows<Exception> { ExposedRepositoryFactory().getRepository(UnknownInterfacePropertyRepository::class.java) }
        assertTrue(error.stackTraceToString().contains("unknown")) { error.stackTraceToString() }
    }

    @Test
    fun testOpenInterfaceProjectionFailsAtCreation() {
        assertThrows<Exception> { ExposedRepositoryFactory().getRepository(OpenProjectionRepository::class.java) }
    }

    @Test
    fun testNestedInterfaceProjectionFailsAtCreation() {
        assertThrows<Exception> { ExposedRepositoryFactory().getRepository(NestedProjectionRepository::class.java) }
    }

    @Test
    fun testNestedDtoProjectionFailsAtCreation() {
        assertThrows<Exception> { ExposedRepositoryFactory().getRepository(NestedDtoProjectionRepository::class.java) }
    }

    /** Nested, so the package scan of [SpringDataExposedTestConfiguration] doesn't pick it up. */
    @ExposedRepository(table = Customers::class)
    interface UnknownDtoPropertyRepository : ExposedCrudRepository<Customer, Long> {
        fun findByEmail(email: String): CustomerWithUnknownProperty?
    }

    /** Nested, so the package scan of [SpringDataExposedTestConfiguration] doesn't pick it up. */
    @ExposedRepository(table = Customers::class)
    interface UnknownInterfacePropertyRepository : ExposedCrudRepository<Customer, Long> {
        fun findByEmail(email: String): UnknownView?
    }

    /** Nested, so the package scan of [SpringDataExposedTestConfiguration] doesn't pick it up. */
    @ExposedRepository(table = Customers::class)
    interface OpenProjectionRepository : ExposedCrudRepository<Customer, Long> {
        fun findByEmail(email: String): OpenView?
    }

    /** Nested, so the package scan of [SpringDataExposedTestConfiguration] doesn't pick it up. */
    @ExposedRepository(table = Customers::class)
    interface NestedProjectionRepository : ExposedCrudRepository<Customer, Long> {
        fun findByEmail(email: String): NestedView?
    }

    /** Nested, so the package scan of [SpringDataExposedTestConfiguration] doesn't pick it up. */
    @ExposedRepository(table = Customers::class)
    interface NestedDtoProjectionRepository : ExposedCrudRepository<Customer, Long> {
        fun findByEmail(email: String): CustomerWithNestedName?
    }

    interface UnknownView {
        val name: String
        val unknown: String
    }

    interface OpenView {
        @get:Value("#{target.name + ' <' + target.email + '>'}")
        val display: String
    }

    interface NestedView {
        val name: String
        val nested: NameView
    }

    data class CustomerWithNestedName(val name: CustomerName)
}

/** Projections only used by the derived query projection tests. */
data class CustomerRef(val id: Long, val name: String)

data class CustomerActivity(val active: Boolean)

data class CustomerGreeting(val name: String, val greeting: String = "hello")

data class CustomerWithUnknownProperty(val name: String, val unknown: String)

interface NameView {
    val name: String
}

interface SummaryView {
    val name: String
    val email: String
    val age: Int
}

interface NicknameView {
    val nickname: String?
}

interface IdView {
    val id: Long
}

@ExposedRepository(table = Customers::class)
interface ProjectionCustomerRepository : ExposedCrudRepository<Customer, Long> {
    // DTO
    fun findByEmail(email: String): CustomerName?
    fun findNameByActive(active: Boolean): CustomerName?
    fun findOptionalByEmail(email: String): Optional<CustomerName>
    fun findByAgeGreaterThanOrderByAgeAsc(age: Int): List<CustomerSummary>
    fun findAllByOrderByAgeAsc(): List<CustomerNickname>
    fun findRefByEmail(email: String): CustomerRef?
    fun findActivityByAgeGreaterThanOrderByAgeAsc(age: Int): List<CustomerActivity>
    fun findGreetingByEmail(email: String): CustomerGreeting?
    fun findFirst2ByOrderByAgeDesc(): List<CustomerName>
    fun findDistinctByAgeGreaterThan(age: Int): List<CustomerActivity>
    fun findByActive(active: Boolean, sort: Sort): List<CustomerName>
    fun streamByActiveOrderByAgeAsc(active: Boolean): Stream<CustomerName>
    fun findPageByAgeGreaterThanOrderByAgeAsc(age: Int, pageable: Pageable): Page<CustomerName>
    fun findSliceByAgeGreaterThanOrderByAgeAsc(age: Int, pageable: Pageable): Slice<CustomerName>
    fun findFirst2ByAgeGreaterThanOrderByAgeAsc(age: Int, position: ScrollPosition): Window<CustomerName>

    // Interface
    fun findViewByEmail(email: String): NameView?
    fun findByActiveOrderByAgeAsc(active: Boolean): List<SummaryView>
    fun findNicknameViewsByAgeGreaterThanOrderByAgeAsc(age: Int): List<NicknameView>
    fun findIdViewByEmail(email: String): IdView?
    fun findViewPageByAgeGreaterThanOrderByAgeAsc(age: Int, pageable: Pageable): Page<NameView>

    // Dynamic
    fun <P : Any> findByEmail(email: String, type: Class<P>): P?
    fun <P : Any> findByAgeLessThanOrderByAgeAsc(age: Int, type: Class<P>): List<P>

    // Raw rows
    fun findRowByEmail(email: String): ResultRow?
    fun findRowsByAgeGreaterThanOrderByAgeAsc(age: Int): List<ResultRow>

    // Entity, for comparison
    fun findCustomerByEmail(email: String): Customer?
}
