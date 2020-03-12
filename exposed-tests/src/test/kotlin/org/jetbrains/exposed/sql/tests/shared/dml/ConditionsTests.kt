package org.jetbrains.exposed.sql.tests.shared.dml

import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.Op
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.tests.DatabaseTestsBase
import org.jetbrains.exposed.sql.tests.shared.assertEqualLists
import org.jetbrains.exposed.sql.tests.shared.assertEquals
import org.junit.Test

class ConditionsTests : DatabaseTestsBase() {
    @Test
    fun testTRUEandFALSEOps() {
        withCitiesAndUsers { cities, _, _ ->
            val allSities = cities.selectAll().toCityNameList()
            assertEquals(0L, cities.select { Op.FALSE }.count())
            assertEquals(allSities.size.toLong(), cities.select { Op.TRUE }.count())
        }
    }

    // https://github.com/JetBrains/Exposed/issues/581
    @Test
    fun sameColumnUsedInSliceMultipleTimes() {
        withCitiesAndUsers { city, _, _ ->
            val row = city.slice(city.name, city.name, city.id).select { city.name eq "Munich" }.toList().single()
            assertEquals(2, row[city.id])
            assertEquals("Munich", row[city.name])
        }
    }

    // https://github.com/JetBrains/Exposed/issues/693
    @Test
    fun compareToNullableColumn() {
        val table = object : IntIdTable("foo") {
            val c1 = integer("c1")
            val c2 = integer("c2").nullable()
        }
        withTables(table) {
            table.insert {
                it[c1] = 0
                it[c2] = 0
            }
            table.insert {
                it[c1] = 1
                it[c2] = 2
            }
            table.insert {
                it[c1] = 2
                it[c2] = 1
            }

            assertEquals(1, table.select { table.c1.less(table.c2) }.single()[table.c1])
            assertEqualLists(
                listOf(0, 1),
                table.select { table.c1.lessEq(table.c2) }.orderBy(table.c1).map { it[table.c1] }
            )
            assertEquals(2, table.select { table.c1.greater(table.c2) }.single()[table.c1])
            assertEqualLists(
                listOf(0, 2),
                table.select { table.c1.greaterEq(table.c2) }.orderBy(table.c1).map { it[table.c1] }
            )

            assertEquals(2, table.select { table.c2.less(table.c1) }.single()[table.c1])
            assertEqualLists(
                listOf(0, 2),
                table.select { table.c2.lessEq(table.c1) }.orderBy(table.c1).map { it[table.c1] }
            )
            assertEquals(1, table.select { table.c2.greater(table.c1) }.single()[table.c1])
            assertEqualLists(
                listOf(0, 1),
                table.select { table.c2.greaterEq(table.c1) }.orderBy(table.c1).map { it[table.c1] }
            )
        }
    }
}