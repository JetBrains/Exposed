package org.jetbrains.exposed.sql.tests.shared.dml

import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.exceptions.ExposedSQLException
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.tests.DatabaseTestsBase
import org.jetbrains.exposed.sql.tests.shared.assertEqualLists
import org.jetbrains.exposed.sql.tests.shared.assertEquals
import org.jetbrains.exposed.sql.tests.shared.expectException
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

    @Test
    fun nullOpUpdateAndSelectTest() {
        withCitiesAndUsers { _, users, _ ->
            val allUsers = users.selectAll().count()
            users.update {
                it[users.cityId] = Op.nullOp()
            }
            val nullUsers1 = users.select { users.cityId.isNull() }.count()
            assertEquals(allUsers, nullUsers1)

            val nullUsers2 = users.select { users.cityId eq Op.nullOp() }.count()
            assertEquals(allUsers, nullUsers2)
        }
    }

    @Test
    fun nullOpUpdateFailsTest() {
        withCitiesAndUsers { _, users, _ ->
            expectException<ExposedSQLException> {
                users.update {
                    it[users.name] = Op.nullOp()
                }
            }
        }
    }

    @Test
    fun nullOpInCaseTest() {
        withCitiesAndUsers { cities, _, _ ->
            val caseCondition = Case().
                When(Op.build { cities.id eq 1 }, Op.nullOp<String>()).
                Else(cities.name)
            var nullBranchWasExecuted = false
            cities.slice(cities.id, cities.name, caseCondition).selectAll().forEach {
                val result = it[caseCondition]
                if (it[cities.id] == 1) {
                    nullBranchWasExecuted = true
                    assertEquals(null, result)
                } else {
                    assertEquals(it[cities.name], result)
                }
            }
            assertEquals(true, nullBranchWasExecuted)
        }
    }
}
