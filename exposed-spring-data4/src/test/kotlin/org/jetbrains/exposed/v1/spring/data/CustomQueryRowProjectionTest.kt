package org.jetbrains.exposed.v1.spring.data

import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greater
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.spring.data.query.query
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

/**
 * Custom queries whose `query { }` block returns [ResultRow]s, which are converted to the declared element type:
 * the entity, a DTO, a closed interface projection, or the raw [ResultRow].
 */
open class CustomQueryRowProjectionTest : ExposedSpringDataTestBase() {
    @Autowired
    override lateinit var repository: RowProjectionCustomQueryRepository

    @Autowired
    lateinit var crudRepository: CrudCustomerRepository

    @Test
    fun testRowsToDtoFromPartialSelect() {
        seed()
        assertEquals(
            listOf(CustomerName("Ann"), CustomerName("Bob"), CustomerName("Carla"), CustomerName("Dan")),
            repository.rowsToNames(),
        )
    }

    @Test
    fun testRowsToDtoFromSelectAll() {
        seed()
        assertEquals(
            listOf(CustomerSummary("Carla", "carla@example.com", 35), CustomerSummary("Dan", "dan@x.io", 40)),
            repository.rowsToSummaries(30),
        )
    }

    @Test
    fun testRowsToDtoEmpty() {
        seed()
        assertTrue(repository.rowsToSummaries(100).isEmpty())
    }

    @Test
    fun testRowsToDtoFromLazyQuery() {
        seed()
        assertEquals(
            listOf(CustomerSummary("Ann", "ann@x.io", 25), CustomerSummary("Bob", "bob@x.io", 30)),
            repository.lazyRowsToSummaries(35).toList(),
        )
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    fun testRowsToDtoFromLazyQueryIsMaterialisedInsideTheTransaction() {
        seed()
        val result = repository.lazyRowsToSummaries(35)
        assertEquals(listOf("Ann", "Bob"), result.map { it.name })
        assertEquals(result.toList(), result.toList())
    }

    @Test
    fun testRowsToDtoMapsNullableColumn() {
        seed()
        assertEquals(
            listOf(
                CustomerNickname("Ann", "annie"),
                CustomerNickname("Bob", null),
                CustomerNickname("Carla", "car_la"),
                CustomerNickname("Dan", "50%off"),
            ),
            repository.rowsToNicknames(),
        )
    }

    @Test
    fun testRowsToDtoMapsIdToRawValue() {
        val saved = seed()
        assertEquals(saved.map { CustomerRef(it.id!!, it.name) }, repository.rowsToRefs())
    }

    @Test
    fun testRowsToDtoWithConstructorDefaultForUnmappedOptionalParameter() {
        seed()
        val greetings = repository.rowsToGreetings()
        assertEquals(listOf("Ann", "Bob", "Carla", "Dan"), greetings.map { it.name })
        assertTrue(greetings.all { it.greeting == "hello" })
    }

    @Test
    fun testRowToDtoSingleResult() {
        seed()
        assertEquals(CustomerSummary("Carla", "carla@example.com", 35), repository.rowToSummary("carla@example.com"))
    }

    @Test
    fun testRowToDtoSingleResultMissingRowFails() {
        seed()
        assertThrows<NoSuchElementException> { repository.rowToSummary("nobody@x.io") }
    }

    @Test
    fun testRowToDtoNullableSingleResult() {
        seed()
        assertEquals(CustomerName("Ann"), repository.rowToNameOrNull("ann@x.io"))
        assertNull(repository.rowToNameOrNull("nobody@x.io"))
    }

    @Test
    fun testRowsToEntityNullableSingleResult() {
        val saved = seed()
        assertEquals(saved.single { it.name == "Ann" }, repository.rowToCustomerOrNull("ann@x.io"))
        assertNull(repository.rowToCustomerOrNull("nobody@x.io"))
    }

    @Test
    fun testRowsToInterfaceProjection() {
        seed()
        val views = repository.rowsToSummaryViews()
        assertEquals(listOf("Ann", "Bob", "Carla", "Dan"), views.map { it.name })
        assertEquals(listOf("ann@x.io", "bob@x.io", "carla@example.com", "dan@x.io"), views.map { it.email })
        assertEquals(listOf(25, 30, 35, 40), views.map { it.age })
    }

    @Test
    fun testRowsToInterfaceProjectionFromLazyQuery() {
        seed()
        assertEquals(listOf("annie", null, "car_la", "50%off"), repository.lazyRowsToNicknameViews().map { it.nickname })
    }

    @Test
    fun testRowToInterfaceProjectionSingleResult() {
        seed()
        assertEquals("Dan", repository.rowToNameView("dan@x.io").name)
    }

    @Test
    fun testRowsToInterfaceProjectionMapsIdToRawValue() {
        val saved = seed()
        assertEquals(saved.map { it.id }, repository.rowsToIdViews().map { it.id })
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    fun testRowsToInterfaceProjectionIsUsableAfterTheTransaction() {
        seed()
        assertEquals(listOf("Ann", "Bob", "Carla", "Dan"), repository.rowsToSummaryViews().map { it.name })
    }

    @Test
    fun testRowsReturnedUnmapped() {
        seed()
        assertEquals(listOf("Ann", "Bob", "Carla", "Dan"), repository.rawRows().map { it[Customers.name] })
    }

    @Test
    fun testRowsReturnedUnmappedFromLazyQuery() {
        seed()
        assertEquals(listOf(25, 30, 35, 40), repository.lazyRawRows().map { it[Customers.age] })
    }

    @Test
    fun testRowReturnedUnmapped() {
        seed()
        assertEquals("car_la", repository.rawRow("carla@example.com")[Customers.nickname])
    }

    @Test
    fun testRowsToDifferentProjectionsInOneMethod() {
        seed()
        val (names, views) = repository.namesAndViews()
        assertEquals(listOf(CustomerName("Ann"), CustomerName("Bob")), names)
        assertEquals(listOf("carla@example.com", "dan@x.io"), views.map { it.email })
    }

    @Test
    fun testRowsWithExplicitTypeArgument() {
        seed()
        assertEquals(listOf("Ann", "Bob", "Carla", "Dan"), repository.namesWithExplicitTypeArgument())
    }

    // ---- Rows converted to projections: selected columns ----

    @Test
    fun testLazySelectAllIsNarrowedToDtoColumns() {
        seed()
        val statements = captureSelects { repository.lazyRowsToSummaries(35) }
        assertEquals(setOf("name", "email", "age"), selectedColumns(statements.single()))
    }

    @Test
    fun testLazySelectAllIsNarrowedToInterfaceColumns() {
        seed()
        val statements = captureSelects { repository.lazyRowsToNicknameViews() }
        assertEquals(setOf("nickname"), selectedColumns(statements.single()))
    }

    @Test
    fun testLazySelectAllIsNotNarrowedForEntities() {
        seed()
        val statements = captureSelects { repository.youngerThanLazy(35) }
        assertEquals(Customers.columns.map { it.name.lowercase() }.toSet(), selectedColumns(statements.single()))
    }

    @Test
    fun testLazySelectAllIsNotNarrowedForRawRows() {
        seed()
        val statements = captureSelects { repository.lazyRawRows() }
        assertEquals(Customers.columns.map { it.name.lowercase() }.toSet(), selectedColumns(statements.single()))
    }

    @Test
    fun testExplicitSelectIsNotNarrowed() {
        seed()
        // the user chose the columns, so they are kept even though the projection needs fewer
        val statements = captureSelects { repository.lazyExplicitSelectToNames() }
        assertEquals(setOf("name", "email"), selectedColumns(statements.single()))
    }

    @Test
    fun testDistinctSelectAllIsNotNarrowed() {
        seed()
        // narrowing would change what DISTINCT compares, so the user's select list is kept
        assertEquals(4, repository.lazyDistinctRowsToActivities().count())
    }

    // ---- Rows converted to projections: invalid projections ----

    @Test
    fun testRowsToDtoWithColumnMissingFromSelectFails() {
        seed()
        val error = assertThrows<IllegalStateException> { repository.partialRowsToSummaries() }
        assertTrue(error.message!!.contains("email")) { error.message }
    }

    @Test
    fun testLazyRowsToDtoWithColumnMissingFromSelectFails() {
        seed()
        val error = assertThrows<IllegalStateException> { repository.lazyPartialRowsToSummaries() }
        assertTrue(error.message!!.contains("email")) { error.message }
    }

    @Test
    fun testRowsToDtoWithPropertyWithoutColumnFails() {
        seed()
        val error = assertThrows<Exception> { repository.rowsToUnknownProperty() }
        assertTrue(error.stackTraceToString().contains("unknown")) { error.stackTraceToString() }
    }

    @Test
    fun testRowsToNestedDtoFails() {
        seed()
        assertThrows<Exception> { repository.rowsToNestedDto() }
    }

    @Test
    fun testRowsToOpenInterfaceProjectionFails() {
        seed()
        assertThrows<Exception> { repository.rowsToOpenView() }
    }

    @Test
    fun testRowsToScalarTypeFails() {
        seed()
        val error = assertThrows<Exception> { repository.rowsToStrings() }
        assertTrue(error.stackTraceToString().contains("String")) { error.stackTraceToString() }
    }

    @Test
    fun testRowsToProjectionOutsideRepositoryMethodFails() {
        seed()
        val error = assertThrows<IllegalStateException> {
            val result: List<CustomerName> = repository.query { Customers.select(Customers.name).toList() }
            result
        }
        assertTrue(error.message!!.contains("default method"))
    }

    @Test
    fun testRowsToProjectionOnDifferentRepositoryFails() {
        seed()
        val error = assertThrows<IllegalStateException> { repository.rowsToNamesVia(crudRepository) }
        assertTrue(error.message!!.contains("different repository"))
    }

    @Test
    fun testFailedProjectionDoesNotBreakLaterCalls() {
        seed()
        assertThrows<IllegalStateException> { repository.partialRowsToSummaries() }
        assertEquals(listOf(CustomerName("Ann"), CustomerName("Bob"), CustomerName("Carla"), CustomerName("Dan")), repository.rowsToNames())
    }
}

@ExposedRepository(table = Customers::class)
interface RowProjectionCustomQueryRepository : ExposedCrudRepository<Customer, Long> {
    fun youngerThanLazy(age: Int): Iterable<Customer> = query {
        Customers.selectAll().where { Customers.age less age }
    }

    fun rowsToNames(): List<CustomerName> = query {
        Customers.select(Customers.name).orderBy(Customers.age).toList()
    }

    fun rowsToSummaries(minAge: Int): List<CustomerSummary> = query {
        Customers.selectAll().where { Customers.age greater minAge }.orderBy(Customers.age).toList()
    }

    fun lazyRowsToSummaries(maxAge: Int): Iterable<CustomerSummary> = query {
        Customers.selectAll().where { Customers.age less maxAge }.orderBy(Customers.age)
    }

    fun rowsToNicknames(): List<CustomerNickname> = query {
        Customers.select(Customers.name, Customers.nickname).orderBy(Customers.age).toList()
    }

    fun rowsToRefs(): List<CustomerRef> = query {
        Customers.select(Customers.id, Customers.name).orderBy(Customers.age).toList()
    }

    fun rowsToGreetings(): List<CustomerGreeting> = query {
        Customers.select(Customers.name).orderBy(Customers.age).toList()
    }

    fun rowToSummary(email: String): CustomerSummary = query {
        Customers.selectAll().where { Customers.email eq email }.single()
    }

    fun rowToNameOrNull(email: String): CustomerName? = query {
        Customers.select(Customers.name).where { Customers.email eq email }.singleOrNull()
    }

    fun rowToCustomerOrNull(email: String): Customer? = query {
        Customers.selectAll().where { Customers.email eq email }.singleOrNull()
    }

    fun rowsToSummaryViews(): List<SummaryView> = query {
        Customers.selectAll().orderBy(Customers.age).toList()
    }

    fun lazyRowsToNicknameViews(): Iterable<NicknameView> = query {
        Customers.selectAll().orderBy(Customers.age)
    }

    fun rowToNameView(email: String): NameView = query {
        Customers.selectAll().where { Customers.email eq email }.single()
    }

    fun rowsToIdViews(): List<IdView> = query {
        Customers.select(Customers.id).orderBy(Customers.age).toList()
    }

    fun rawRows(): List<ResultRow> = query {
        Customers.selectAll().orderBy(Customers.age).toList()
    }

    fun lazyRawRows(): Iterable<ResultRow> = query {
        Customers.selectAll().orderBy(Customers.age)
    }

    fun rawRow(email: String): ResultRow = query {
        Customers.selectAll().where { Customers.email eq email }.single()
    }

    fun namesAndViews(): Pair<List<CustomerName>, List<SummaryView>> {
        val names: List<CustomerName> = query {
            Customers.select(Customers.name).where { Customers.age less 35 }.orderBy(Customers.age).toList()
        }
        val views: List<SummaryView> = query {
            Customers.selectAll().where { Customers.age greaterEq 35 }.orderBy(Customers.age).toList()
        }
        return names to views
    }

    fun namesWithExplicitTypeArgument(): List<String> =
        query<CustomerName> { Customers.select(Customers.name).orderBy(Customers.age).toList() }.map { it.name }

    fun lazyExplicitSelectToNames(): Iterable<CustomerName> = query {
        Customers.select(Customers.name, Customers.email).orderBy(Customers.age)
    }

    fun lazyDistinctRowsToActivities(): Iterable<CustomerActivity> = query {
        Customers.selectAll().withDistinct()
    }

    fun partialRowsToSummaries(): List<CustomerSummary> = query {
        Customers.select(Customers.name, Customers.age).toList()
    }

    fun lazyPartialRowsToSummaries(): Iterable<CustomerSummary> = query {
        Customers.select(Customers.name, Customers.age)
    }

    fun rowsToUnknownProperty(): List<CustomerWithUnknownProperty> = query {
        Customers.selectAll().toList()
    }

    fun rowsToNestedDto(): List<DerivedQueryProjectionTest.CustomerWithNestedName> = query {
        Customers.selectAll().toList()
    }

    fun rowsToOpenView(): List<DerivedQueryProjectionTest.OpenView> = query {
        Customers.selectAll().toList()
    }

    fun rowsToStrings(): List<String> = query<String> {
        Customers.select(Customers.name).toList()
    }

    fun rowsToNamesVia(other: CrudCustomerRepository): List<CustomerName> = other.query {
        Customers.select(Customers.name).toList()
    }
}
