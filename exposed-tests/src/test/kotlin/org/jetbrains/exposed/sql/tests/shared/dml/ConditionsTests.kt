package org.jetbrains.exposed.sql.tests.shared.dml

import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.dao.id.LongIdTable
import org.jetbrains.exposed.exceptions.ExposedSQLException
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.greater
import org.jetbrains.exposed.sql.SqlExpressionBuilder.greaterEq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.less
import org.jetbrains.exposed.sql.SqlExpressionBuilder.lessEq
import org.jetbrains.exposed.sql.tests.DatabaseTestsBase
import org.jetbrains.exposed.sql.tests.TestDB
import org.jetbrains.exposed.sql.tests.shared.assertEqualLists
import org.jetbrains.exposed.sql.tests.shared.assertEquals
import org.jetbrains.exposed.sql.tests.shared.expectException
import org.junit.Test
import kotlin.test.assertFailsWith

class ConditionsTests : DatabaseTestsBase() {
    @Test
    fun testTRUEandFALSEOps() {
        withCitiesAndUsers { cities, _, _ ->
            val allCities = cities.selectAll().toCityNameList()
            assertEquals(0L, cities.selectAll().where { Op.FALSE }.count())
            assertEquals(allCities.size.toLong(), cities.selectAll().where { Op.TRUE }.count())
        }
    }

    @Test
    fun testNullSafeEqualityOps() {
        val table = object : IntIdTable("foo") {
            val number1 = integer("number_1").nullable()
            val number2 = integer("number_2").nullable()
        }
        // remove SQL Server exclusion once test container supports SQL Server 2022
        withTables(excludeSettings = listOf(TestDB.SQLSERVER), table) { testDb ->
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
                table.selectAll().where { table.number1 eq table.number2 }.map { it[table.id] },
                listOf(sameNumberId)
            )
            // null == null returns true
            assertEqualLists(
                table.selectAll().where { table.number1 isNotDistinctFrom table.number2 }.map { it[table.id] },
                listOf(sameNumberId, bothNullId)
            )

            // number != null returns null
            assertEqualLists(
                table.selectAll().where { table.number1 neq table.number2 }.map { it[table.id] },
                listOf(differentNumberId)
            )
            // number != null returns true
            assertEqualLists(
                table.selectAll().where { table.number1 isDistinctFrom table.number2 }.map { it[table.id] },
                listOf(differentNumberId, oneNullId)
            )

            // Oracle does not support complex expressions in DECODE()
            if (testDb != TestDB.ORACLE) {
                // (number1 is not null) != (number2 is null) returns true when both are null or neither is null
                assertEqualLists(
                    table.selectAll().where { table.number1.isNotNull() isDistinctFrom table.number2.isNull() }.map { it[table.id] },
                    listOf(sameNumberId, differentNumberId, bothNullId)
                )
                // (number1 is not null) == (number2 is null) returns true when only 1 is null
                assertEqualLists(
                    table.selectAll().where { table.number1.isNotNull() isNotDistinctFrom table.number2.isNull() }.map { it[table.id] },
                    listOf(oneNullId)
                )
            }
        }
    }

    @Test
    fun testComparisonOperatorsWithEntityIDColumns() {
        val longTable = object : LongIdTable("long_table") {
            val amount = long("amount")
        }

        fun selectIdWhere(condition: SqlExpressionBuilder.() -> Op<Boolean>): List<Long> {
            val query = longTable.select(longTable.id).where(SqlExpressionBuilder.condition())
            return query.map { it[longTable.id].value }
        }

        // SQL Server doesn't support an explicit id for auto-increment table
        withTables(excludeSettings = listOf(TestDB.SQLSERVER), longTable) {
            val id1 = longTable.insertAndGetId {
                it[id] = 1
                it[amount] = 9999
            }.value
            val id2 = longTable.insertAndGetId {
                it[id] = 2
                it[amount] = 2
            }.value
            val id3 = longTable.insertAndGetId {
                it[id] = 3
                it[amount] = 1
            }.value

            // the incorrect overload operator would previously throw an exception and
            // a warning would show about 'Type argument ... cannot be inferred ... incompatible upper bounds'
            val id2Only = listOf(id2)
            assertEqualLists(id2Only, selectIdWhere { longTable.id eq longTable.amount })

            val id1AndId3 = listOf(id1, id3)
            assertEqualLists(id1AndId3, selectIdWhere { longTable.id neq longTable.amount })

            val id1Only = listOf(id1)
            assertEqualLists(id1Only, selectIdWhere { longTable.id less longTable.amount })

            val id1AndId2 = listOf(id1, id2)
            assertEqualLists(id1AndId2, selectIdWhere { longTable.id lessEq longTable.amount })

            val id3Only = listOf(id3)
            assertEqualLists(id3Only, selectIdWhere { longTable.id greater longTable.amount })

            val id2AndId3 = listOf(id2, id3)
            assertEqualLists(id2AndId3, selectIdWhere { longTable.id greaterEq longTable.amount })

            // symmetric operators (EntityID value on right) should not show a warning either
            assertEqualLists(id2Only, selectIdWhere { longTable.amount eq longTable.id })
            assertEqualLists(id1AndId3, selectIdWhere { longTable.amount neq longTable.id })
            assertEqualLists(id3Only, selectIdWhere { longTable.amount less longTable.id })
            assertEqualLists(id2AndId3, selectIdWhere { longTable.amount lessEq longTable.id })
            assertEqualLists(id1Only, selectIdWhere { longTable.amount greater longTable.id })
            assertEqualLists(id1AndId2, selectIdWhere { longTable.amount greaterEq longTable.id })
        }
    }

    // https://github.com/JetBrains/Exposed/issues/581
    @Test
    fun sameColumnUsedInSliceMultipleTimes() {
        withCitiesAndUsers { city, _, _ ->
            val row = city.select(city.name, city.name, city.id).where { city.name eq "Munich" }.toList().single()
            assertEquals(2, row[city.id])
            assertEquals("Munich", row[city.name])
        }
    }

    @Test
    fun testSliceWithEmptyListThrows() {
        withCitiesAndUsers { cities, _, _ ->
            assertFailsWith<IllegalArgumentException> {
                cities.select(emptyList()).toList()
            }
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

            assertEquals(1, table.selectAll().where { table.c1.less(table.c2) }.single()[table.c1])
            assertEqualLists(
                listOf(0, 1),
                table.selectAll().where { table.c1.lessEq(table.c2) }.orderBy(table.c1).map { it[table.c1] }
            )
            assertEquals(2, table.selectAll().where { table.c1.greater(table.c2) }.single()[table.c1])
            assertEqualLists(
                listOf(0, 2),
                table.selectAll().where { table.c1.greaterEq(table.c2) }.orderBy(table.c1).map { it[table.c1] }
            )

            assertEquals(2, table.selectAll().where { table.c2.less(table.c1) }.single()[table.c1])
            assertEqualLists(
                listOf(0, 2),
                table.selectAll().where { table.c2.lessEq(table.c1) }.orderBy(table.c1).map { it[table.c1] }
            )
            assertEquals(1, table.selectAll().where { table.c2.greater(table.c1) }.single()[table.c1])
            assertEqualLists(
                listOf(0, 1),
                table.selectAll().where { table.c2.greaterEq(table.c1) }.orderBy(table.c1).map { it[table.c1] }
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
            val nullUsers1 = users.selectAll().where { users.cityId.isNull() }.count()
            assertEquals(allUsers, nullUsers1)

            val nullUsers2 = users.selectAll().where { users.cityId eq Op.nullOp() }.count()
            assertEquals(allUsers, nullUsers2)

            val nullUsers3 = users.selectAll().where { users.cityId eq null }.count()
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
            cities.select(cities.id, cities.name, caseCondition).forEach {
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
            val query1 = cities.select(cities.id, function1).prepareSQL(this, prepared = false)
            val query2 = cities.select(cities.id, function2).prepareSQL(this, prepared = false)
            assertEquals(query1, query2)

            val results1 = cities.select(cities.id, function1).toList()
            cities.select(cities.id, function2).forEachIndexed { i, row ->
                val currentId = row[cities.id]
                val functionResult = row[function2]

                assertEquals(if (currentId == 1) original else copy, functionResult)
                assertEquals(currentId, results1[i][cities.id])
                assertEquals(functionResult, results1[i][function1])
            }
        }
    }

    @Test
    fun testChainedAndNestedCaseWhenElseSyntax() {
        withCitiesAndUsers { cities, _, _ ->
            val nestedCondition = Case()
                .When(Op.build { cities.id eq 1 }, intLiteral(1))
                .Else(intLiteral(-1))
            val chainedCondition = Case()
                .When(Op.build { cities.name like "M%" }, intLiteral(0))
                .When(Op.build { cities.name like "St. %" }, nestedCondition)
                .When(Op.build { cities.name like "P%" }, intLiteral(2))
                .Else(intLiteral(-1))

            val results = cities.select(cities.name, chainedCondition)
            results.forEach {
                val cityName = it[cities.name]
                val expectedNumber = when {
                    cityName.startsWith("M") -> 0
                    cityName.startsWith("St. ") -> 1
                    cityName.startsWith("P") -> 2
                    else -> -1
                }
                assertEquals(expectedNumber, it[chainedCondition])
            }
        }
    }

    @Test
    fun selectAliasedComparisonResult() {
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

            val c1LtC2 = table.c1.less(table.c2).alias("c1ltc2")
            assertEqualLists(
                listOf(false, true, false),
                table.select(table.c1, c1LtC2).orderBy(table.c1).map { it[c1LtC2] }
            )
            val c1LteC2 = table.c1.lessEq(table.c2).alias("c1ltec2")
            assertEqualLists(
                listOf(true, true, false),
                table.select(table.c1, c1LteC2).orderBy(table.c1).map { it[c1LteC2] }
            )
            val c1GtC2 = table.c1.greater(table.c2).alias("c1gt2")
            assertEqualLists(
                listOf(false, false, true),
                table.select(table.c1, c1GtC2).orderBy(table.c1).map { it[c1GtC2] }
            )
            val c1GteC2 = table.c1.greaterEq(table.c2).alias("c1gtec2")
            assertEqualLists(
                listOf(true, false, true),
                table.select(table.c1, c1GteC2).orderBy(table.c1).map { it[c1GteC2] }
            )
        }
    }
}
