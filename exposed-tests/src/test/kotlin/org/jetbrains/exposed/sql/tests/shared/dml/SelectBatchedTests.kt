package org.jetbrains.exposed.sql.tests.shared.dml

import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.tests.DatabaseTestsBase
import org.jetbrains.exposed.sql.tests.shared.assertEqualLists
import org.jetbrains.exposed.sql.tests.shared.assertTrue
import org.junit.Test
import java.util.*
import kotlin.text.Typography.less

class SelectBatchedTests : DatabaseTestsBase() {
    private val scopedCities = object : Table(Cities.tableName) {
        val id: Column<Int> = integer("cityId").autoIncrement()
        val name: Column<String> = varchar("name", 50)
        override val primaryKey = PrimaryKey(id)
        override val defaultScope = { Op.build { id less 51} }
    }

    @Test
    fun `selectBatched should respect 'where' expression and the provided batch size`() {
        withTables(Cities, scopedCities) {
            val names = List(100) { UUID.randomUUID().toString() }
            Cities.batchInsert(names) { name -> this[Cities.name] = name }

            Cities.selectBatched(batchSize = 25) { Cities.id less 51 }
                .toList().map { it.toCityNameList() }
                .let { batches ->
                    val expectedNames = names.take(50)
                    assertEqualLists(
                        listOf(expectedNames.take(25),
                               expectedNames.takeLast(25)),
                        batches
                    )
                }

            scopedCities.selectBatched(batchSize = 25) { scopedCities.id less 51 }
                .toList().map { it.map { batch -> batch[scopedCities.name] } }
                .let { batches ->
                    val expectedNames = names.take(50)
                    assertEqualLists(
                        listOf(expectedNames.take(25),
                               expectedNames.takeLast(25)),
                        batches
                    )
                }

            scopedCities.selectBatched(batchSize = 25) { scopedCities.id greater  51 }
                .toList().let { batches -> assertTrue(batches.isEmpty()) }
        }
    }

    @Test
    fun `when batch size is greater than the amount of available items, selectAllBatched should return 1 batch`() {
        withTables(Cities) {
            val names = List(25) { UUID.randomUUID().toString() }
            Cities.batchInsert(names) { name -> this[Cities.name] = name }

            val batches = Cities.selectAllBatched(batchSize = 100).toList().map { it.toCityNameList() }

            assertEqualLists(listOf(names), batches)
        }
    }

    @Test
    fun `when there are no items, selectAllBatched should return an empty iterable`() {
        withTables(Cities) {
            val batches = Cities.selectAllBatched().toList()

            assertEqualLists(batches, emptyList())
        }
    }

    @Test
    fun `when there are no items of the given condition, should return an empty iterable`() {
        withTables(Cities) {
            val names = List(25) { UUID.randomUUID().toString() }
            Cities.batchInsert(names) { name -> this[Cities.name] = name }

            val batches = Cities.selectBatched(batchSize = 100) { Cities.id greater 50 }
                .toList().map { it.toCityNameList() }

            assertEqualLists(emptyList(), batches)
        }
    }

    @Test(expected = java.lang.UnsupportedOperationException::class)
    fun `when the table doesn't have an autoinc column, selectAllBatched should throw an exception`() {
        UserData.selectAllBatched()
    }

    @Test(expected = IllegalArgumentException::class)
    fun `when batch size is 0 or less, should throw an exception`() {
        Cities.selectAllBatched(batchSize = -1)
    }
}
