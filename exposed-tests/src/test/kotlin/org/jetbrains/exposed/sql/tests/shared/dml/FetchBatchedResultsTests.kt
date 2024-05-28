package org.jetbrains.exposed.sql.tests.shared.dml

import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.tests.DatabaseTestsBase
import org.jetbrains.exposed.sql.tests.shared.assertEqualLists
import org.junit.Test
import java.util.*

class FetchBatchedResultsTests : DatabaseTestsBase() {
    @Test
    fun testFetchBatchedResultsWithWhereAndSetBatchSize() {
        val cities = DMLTestsData.Cities
        withTables(cities) {
            val names = List(100) { UUID.randomUUID().toString() }
            cities.batchInsert(names) { name -> this[cities.name] = name }

            val batches = cities.selectAll().where { cities.id less 51 }
                .fetchBatchedResults(batchSize = 25)
                .toList().map { it.toCityNameList() }

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
    fun `when batch size is greater than the amount of available items, fetchBatchedResults should return 1 batch`() {
        val cities = DMLTestsData.Cities
        withTables(cities) {
            val names = List(25) { UUID.randomUUID().toString() }
            cities.batchInsert(names) { name -> this[cities.name] = name }

            val batches = cities.selectAll()
                .fetchBatchedResults(batchSize = 100)
                .toList().map { it.toCityNameList() }

            assertEqualLists(listOf(names), batches)
        }
    }

    @Test
    fun `when there are no items, fetchBatchedResults should return an empty iterable`() {
        val cities = DMLTestsData.Cities
        withTables(cities) {
            val batches = cities.selectAll().fetchBatchedResults().toList()

            assertEqualLists(batches, emptyList())
        }
    }

    @Test
    fun `when there are no items of the given condition, should return an empty iterable`() {
        val cities = DMLTestsData.Cities
        withTables(cities) {
            val names = List(25) { UUID.randomUUID().toString() }
            cities.batchInsert(names) { name -> this[cities.name] = name }

            val batches = cities.selectAll().where { cities.id greater 50 }
                .fetchBatchedResults(batchSize = 100)
                .toList().map { it.toCityNameList() }

            assertEqualLists(emptyList(), batches)
        }
    }

    @Test(expected = java.lang.UnsupportedOperationException::class)
    fun `when the table doesn't have an autoinc column, fetchBatchedResults should throw an exception`() {
        DMLTestsData.UserData.selectAll().fetchBatchedResults()
    }

    @Test(expected = IllegalArgumentException::class)
    fun `when batch size is 0 or less, should throw an exception`() {
        DMLTestsData.Cities.selectAll().fetchBatchedResults(batchSize = -1)
    }
}
