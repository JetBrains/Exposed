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
            val allCities = cities.selectAll().toCityNameList()
            assertEquals(0L, cities.select { Op.FALSE }.count())
            assertEquals(allCities.size.toLong(), cities.select { Op.TRUE }.count())
        }
    }

    @Test
    fun testNullSafeEqualityOps() {
        val table = object : IntIdTable("foo") {
            val number1 = integer("number_1").nullable()
            val number2 = integer("number_2").nullable()
        }

        withTables(table) {
            val sameNumberId = table.insert {
                it[number1] = 0
                it[number2] = 0
            } get table.id
            val differentNumberId = table.insert {
                it[number1] = 0
                it[number2] = 1
            } get table.id
            val oneNullId = table.insert {
                it[number1] = 0
                it[number2] = null
            } get table.id
            val bothNullId = table.insert {
                it[number1] = null
                it[number2] = null
            } get table.id

            // null == null returns null
            assertEqualLists(
                table.select { table.number1 eq table.number2 }.map { it[table.id] },
                listOf(sameNumberId)
            )
            // null == null returns true
            assertEqualLists(
                table.select { table.number1 isNotDistinctFrom table.number2 }.map { it[table.id] },
                listOf(sameNumberId, bothNullId)
            )

            // number != null returns null
            assertEqualLists(
                table.select { table.number1 neq table.number2 }.map { it[table.id] },
                listOf(differentNumberId)
            )
            // number != null returns true
            assertEqualLists(
                table.select { table.number1 isDistinctFrom table.number2 }.map { it[table.id] },
                listOf(differentNumberId, oneNullId)
            )
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
            users.update {
                it[users.cityId] = null
            }
            val nullUsers1 = users.select { users.cityId.isNull() }.count()
            assertEquals(allUsers, nullUsers1)

            val nullUsers2 = users.select { users.cityId eq Op.nullOp() }.count()
            assertEquals(allUsers, nullUsers2)

            val nullUsers3 = users.select { users.cityId eq null }.count()
            assertEquals(allUsers, nullUsers3)
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
            val caseCondition = Case()
                .When(Op.build { cities.id eq 1 }, Op.nullOp<String>())
                .Else(cities.name)
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

    @Test
    fun testCaseWhenElseAsArgument() {
        withCitiesAndUsers { cities, _, _ ->
            val original = "ORIGINAL"
            val copy = "COPY"
            val condition = Op.build { cities.id eq 1 }

            val caseCondition1 = Case()
                .When(condition, stringLiteral(original))
                .Else(Op.nullOp())
            // Case().When().Else() invokes CaseWhenElse() so the 2 formats should be interchangeable as arguments
            val caseCondition2 = CaseWhenElse(
                Case().When(condition, stringLiteral(original)),
                Op.nullOp()
            )
            val function1 = Coalesce(caseCondition1, stringLiteral(copy))
            val function2 = Coalesce(caseCondition2, stringLiteral(copy))

            // confirm both formats produce identical SQL
            val query1 = cities.slice(cities.id, function1).selectAll().prepareSQL(this, prepared = false)
            val query2 = cities.slice(cities.id, function2).selectAll().prepareSQL(this, prepared = false)
            assertEquals(query1, query2)

            val results1 = cities.slice(cities.id, function1).selectAll().toList()
            cities.slice(cities.id, function2).selectAll().forEachIndexed { i, row ->
                val currentId = row[cities.id]
                val functionResult = row[function2]

                assertEquals(if (currentId == 1) original else copy, functionResult)
                assertEquals(currentId, results1[i][cities.id])
                assertEquals(functionResult, results1[i][function1])
            }
        }
    }
}
