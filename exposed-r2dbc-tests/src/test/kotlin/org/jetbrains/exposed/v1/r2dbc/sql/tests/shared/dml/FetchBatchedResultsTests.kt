package org.jetbrains.exposed.v1.r2dbc.sql.tests.shared.dml

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flattenConcat
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.alias
import org.jetbrains.exposed.v1.core.dao.id.IntIdTable
import org.jetbrains.exposed.v1.r2dbc.batchInsert
import org.jetbrains.exposed.v1.r2dbc.insert
import org.jetbrains.exposed.v1.r2dbc.selectAll
import org.jetbrains.exposed.v1.r2dbc.tests.R2dbcDatabaseTestsBase
import org.jetbrains.exposed.v1.r2dbc.tests.TestDB
import org.jetbrains.exposed.v1.r2dbc.tests.shared.assertEqualLists
import org.junit.Test
import java.util.*

// Todo Confirm return type of fetchBatchedResults() & expected behavior on collection
// not possible to avoid emitting an empty flow, so tests needed to be altered to remove empty flow results
class FetchBatchedResultsTests : R2dbcDatabaseTestsBase() {
    @Test
    fun testFetchBatchedResultsWithWhereAndSetBatchSize() {
        val cities = DMLTestsData.Cities
        withTables(cities) { testDb ->
            val names = List(100) { UUID.randomUUID().toString() }
            // Oracle throws: Batch execution returning generated values is not supported
            cities.batchInsert(names, shouldReturnGeneratedValues = testDb != TestDB.ORACLE) { name -> this[cities.name] = name }

            val batches = cities.selectAll().where { cities.id less 51 }
                .fetchBatchedResults(batchSize = 25)
                .mapNotNull { it.toCityNameList().ifEmpty { null } }
                .toList()

            val expectedNames = names.take(50)
            assertEqualLists(
                listOf(
                    expectedNames.take(25),
                    expectedNames.takeLast(25)
                ),
                batches
            )
        }
    }

    @Test
    fun `when sortOrder is given, fetchBatchedResults should return batches in the given order`() {
        val cities = DMLTestsData.Cities
        withTables(cities) { testDb ->
            val names = List(100) { UUID.randomUUID().toString() }
            // Oracle throws: Batch execution returning generated values is not supported
            cities.batchInsert(names, shouldReturnGeneratedValues = testDb != TestDB.ORACLE) { name -> this[cities.name] = name }

            val batches = cities.selectAll().where { cities.id less 51 }
                .fetchBatchedResults(batchSize = 25, sortOrder = SortOrder.DESC)
                .mapNotNull { it.toCityNameList().ifEmpty { null } }
                .toList()

            val expectedNames = names.take(50).reversed()
            assertEqualLists(
                listOf(
                    expectedNames.take(25),
                    expectedNames.takeLast(25)
                ),
                batches
            )
        }
    }

    @Test
    fun `when batch size is greater than the amount of available items, fetchBatchedResults should return 1 batch`() {
        val cities = DMLTestsData.Cities
        withTables(cities) { testDb ->
            val names = List(25) { UUID.randomUUID().toString() }
            // Oracle throws: Batch execution returning generated values is not supported
            cities.batchInsert(names, shouldReturnGeneratedValues = testDb != TestDB.ORACLE) { name -> this[cities.name] = name }

            val batches = cities.selectAll()
                .fetchBatchedResults(batchSize = 100)
                .mapNotNull { it.toCityNameList().ifEmpty { null } }
                .toList()

            assertEqualLists(listOf(names), batches)
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `when there are no items, fetchBatchedResults should return an empty iterable`() {
        val cities = DMLTestsData.Cities
        withTables(cities) {
            val batches = cities.selectAll().fetchBatchedResults().flattenConcat().toList()

            assertEqualLists(batches, emptyList())
        }
    }

    @Test
    fun `when there are no items of the given condition, should return an empty iterable`() {
        val cities = DMLTestsData.Cities
        withTables(cities) { testDb ->
            val names = List(25) { UUID.randomUUID().toString() }
            // Oracle throws: Batch execution returning generated values is not supported
            cities.batchInsert(names, shouldReturnGeneratedValues = testDb != TestDB.ORACLE) { name -> this[cities.name] = name }

            val batches = cities.selectAll().where { cities.id greater 50 }
                .fetchBatchedResults(batchSize = 100)
                .mapNotNull { it.toCityNameList().ifEmpty { null } }
                .toList()

            assertEqualLists(emptyList(), batches)
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test(expected = java.lang.UnsupportedOperationException::class)
    fun `when the table doesn't have an autoinc column, fetchBatchedResults should throw an exception`() = runTest {
        DMLTestsData.UserData.selectAll().fetchBatchedResults().flattenConcat().toList()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test(expected = IllegalArgumentException::class)
    fun `when batch size is 0 or less, should throw an exception`() = runTest {
        DMLTestsData.Cities.selectAll().fetchBatchedResults(batchSize = -1).flattenConcat().toList()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun testFetchBatchedResultsWithAutoIncrementEntityId() {
        val tester1 = object : IntIdTable("table_1") {
            val data = varchar("data", 100)
        }

        val tester2 = object : IntIdTable("table_2") {
            val moreData = varchar("more_data", 100)
            val prevData = reference("prev_data", tester1, onUpdate = ReferenceOption.CASCADE)
        }

        withTables(tester1, tester2) {
            (tester2 innerJoin tester1).selectAll().fetchBatchedResults(10_000).flattenConcat().toList()
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun testFetchBatchedResultsWithAlias() {
        val tester = object : IntIdTable("tester") {
            val name = varchar("name", 1)
        }
        withTables(tester) {
            tester.insert { it[name] = "a" }
            tester.insert { it[name] = "b" }
            tester.alias("tester_alias").selectAll().fetchBatchedResults(1).flattenConcat().toList()
        }
    }
}
