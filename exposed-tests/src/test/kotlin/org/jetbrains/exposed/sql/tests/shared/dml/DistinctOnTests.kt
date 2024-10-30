package org.jetbrains.exposed.sql.tests.shared.dml

import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.tests.DatabaseTestsBase
import org.jetbrains.exposed.sql.tests.TestDB
import org.jetbrains.exposed.sql.tests.shared.assertEqualLists
import org.jetbrains.exposed.sql.tests.shared.assertEquals
import org.jetbrains.exposed.sql.tests.shared.expectException
import org.junit.Test
import kotlin.test.assertNull

class DistinctOnTests : DatabaseTestsBase() {

    private val distinctOnSupportedDb = TestDB.ALL_POSTGRES + TestDB.ALL_H2

    @Test
    fun testDistinctOn() {
        val tester = object : IntIdTable("distinct_function_test") {
            val value1 = integer("value1")
            val value2 = integer("value2")
        }

        withTables(excludeSettings = TestDB.ALL - distinctOnSupportedDb, tester) {
            tester.batchInsert(
                listOf(
                    listOf(1, 1), listOf(1, 2), listOf(1, 2),
                    listOf(2, 1), listOf(2, 2), listOf(2, 2),
                    listOf(4, 4), listOf(4, 4), listOf(4, 4),
                )
            ) {
                this[tester.value1] = it[0]
                this[tester.value2] = it[1]
            }

            val distinctValue1 = tester.selectAll()
                .withDistinctOn(tester.value1)
                .orderBy(tester.value1 to SortOrder.ASC, tester.value2 to SortOrder.ASC)
                .map { it[tester.value1] to it[tester.value2] }
            assertEqualLists(listOf(1 to 1, 2 to 1, 4 to 4), distinctValue1)

            val distinctValue2 = tester.selectAll()
                .withDistinctOn(tester.value2)
                .orderBy(tester.value2 to SortOrder.ASC, tester.value1 to SortOrder.ASC)
                .map { it[tester.value1] to it[tester.value2] }
            assertEqualLists(listOf(1 to 1, 1 to 2, 4 to 4), distinctValue2)

            val distinctBoth = tester.selectAll()
                .withDistinctOn(tester.value1, tester.value2)
                .orderBy(tester.value1 to SortOrder.ASC, tester.value2 to SortOrder.ASC)
                .map { it[tester.value1] to it[tester.value2] }
            assertEqualLists(listOf(1 to 1, 1 to 2, 2 to 1, 2 to 2, 4 to 4), distinctBoth)

            val distinctSequential = tester.selectAll()
                .withDistinctOn(tester.value1 to SortOrder.ASC)
                .withDistinctOn(tester.value2 to SortOrder.ASC)
                .map { it[tester.value1] to it[tester.value2] }
            assertEqualLists(distinctBoth, distinctSequential)
        }
    }

    @Test
    fun testExceptions() {
        val tester = object : IntIdTable("distinct_function_test") {
            val value = integer("value1")
        }

        withTables(excludeSettings = TestDB.ALL - distinctOnSupportedDb, tester) {
            val query1 = tester.selectAll()
                .withDistinct()
            expectException<IllegalArgumentException> {
                query1.withDistinctOn(tester.value)
            }

            val query2 = tester.selectAll()
                .withDistinctOn(tester.value)
            expectException<IllegalArgumentException> {
                query2.withDistinct()
            }
        }
    }

    @Test
    fun testEmptyDistinctOn() {
        val tester = object : IntIdTable("distinct_function_test") {
            val value = integer("value1")
        }

        withTables(excludeSettings = TestDB.ALL - distinctOnSupportedDb, tester) {
            // Empty list of columns should not cause exception
            tester.insert {
                it[value] = 1
            }

            val query = tester.selectAll()
                .withDistinctOn(columns = emptyArray<Column<*>>())
            assertNull(query.distinctOn)

            val value = query
                .first()[tester.value]
            assertEquals(1, value)
        }
    }
}
