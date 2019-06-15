package org.jetbrains.exposed.sql.tests.shared

import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.Function
import org.jetbrains.exposed.sql.SqlExpressionBuilder.concat
import org.jetbrains.exposed.sql.SqlExpressionBuilder.div
import org.jetbrains.exposed.sql.SqlExpressionBuilder.mod
import org.jetbrains.exposed.sql.SqlExpressionBuilder.plus
import org.jetbrains.exposed.sql.SqlExpressionBuilder.rem
import org.jetbrains.exposed.sql.SqlExpressionBuilder.times
import org.jetbrains.exposed.sql.tests.DatabaseTestsBase
import org.jetbrains.exposed.sql.tests.TestDB
import org.jetbrains.exposed.sql.vendors.SQLServerDialect
import org.jetbrains.exposed.sql.vendors.currentDialect
import org.junit.Test
import java.math.BigDecimal
import kotlin.test.assertNotNull

class FunctionsTests : DatabaseTestsBase() {

    @Test
    fun testCalc01() {
        withCitiesAndUsers { cities, users, userData ->
            val r = cities.slice(cities.id.sum()).selectAll().toList()
            assertEquals(1, r.size)
            assertEquals(6, r[0][cities.id.sum()])
        }
    }

    @Test
    fun testCalc02() {
        withCitiesAndUsers { cities, users, userData ->
            val sum = Expression.build {
                Sum(cities.id + userData.value, IntegerColumnType())
            }
            val r = (users innerJoin userData innerJoin cities).slice(users.id, sum)
                    .selectAll().groupBy(users.id).orderBy(users.id).toList()
            assertEquals(2, r.size)
            assertEquals("eugene", r[0][users.id])
            assertEquals(22, r[0][sum])
            assertEquals("sergey", r[1][users.id])
            assertEquals(32, r[1][sum])
        }
    }

    @Test
    fun testCalc03() {
        withCitiesAndUsers { cities, users, userData ->
            val sum = Expression.build { Sum(cities.id * 100 + userData.value / 10, IntegerColumnType()) }
            val mod1 = Expression.build { sum % 100 }
            val mod2 = Expression.build { sum mod 100 }
            val r = (users innerJoin userData innerJoin cities).slice(users.id, sum, mod1, mod1)
                    .selectAll().groupBy(users.id).orderBy(users.id).toList()
            assertEquals(2, r.size)
            assertEquals("eugene", r[0][users.id])
            assertEquals(202, r[0][sum])
            assertEquals(2, r[0][mod1])
            assertEquals(2, r[0][mod2])
            assertEquals("sergey", r[1][users.id])
            assertEquals(203, r[1][sum])
            assertEquals(3, r[1][mod1])
            assertEquals(3, r[1][mod2])
        }
    }

    @Test
    fun testSubstring01() {
        withCitiesAndUsers { cities, users, userData ->
            val substring = users.name.substring(1, 2)
            val r = (users).slice(users.id, substring)
                    .selectAll().orderBy(users.id).toList()
            assertEquals(5, r.size)
            assertEquals("Al", r[0][substring])
            assertEquals("An", r[1][substring])
            assertEquals("Eu", r[2][substring])
            assertEquals("Se", r[3][substring])
            assertEquals("So", r[4][substring])
        }
    }

    @Test
    fun testLengthWithCount01() {
        class LengthFunction<T: ExpressionWithColumnType<String>>(val exp: T) : Function<Int>(IntegerColumnType()) {
            override fun toSQL(queryBuilder: QueryBuilder): String
                    = if (currentDialect is SQLServerDialect) "LEN(${exp.toSQL(queryBuilder)})"
            else "LENGTH(${exp.toSQL(queryBuilder)})"
        }
        withCitiesAndUsers { cities, _, _ ->
            val sumOfLength = LengthFunction(cities.name).sum()
            val expectedValue = cities.selectAll().sumBy{ it[cities.name].length }

            val results = cities.slice(sumOfLength).selectAll().toList()
            assertEquals(1, results.size)
            assertEquals(expectedValue, results.single()[sumOfLength])
        }
    }


    @Test
    fun testSelectCase01() {
        withCitiesAndUsers { cities, users, userData ->
            val field = Expression.build { case().When(users.id eq "alex", stringLiteral("11")).Else(stringLiteral("22")) }
            val r = users.slice(users.id, field).selectAll().orderBy(users.id).limit(2).toList()
            assertEquals(2, r.size)
            assertEquals("11", r[0][field])
            assertEquals("alex", r[0][users.id])
            assertEquals("22", r[1][field])
            assertEquals("andrey", r[1][users.id])
        }
    }

    @Test
    fun testStringFunctions() {
        withCitiesAndUsers { cities, users, userData ->

            val lcase = DMLTestsData.Cities.name.lowerCase()
            assert(cities.slice(lcase).selectAll().any { it[lcase] == "prague" })

            val ucase = DMLTestsData.Cities.name.upperCase()
            assert(cities.slice(ucase).selectAll().any { it[ucase] == "PRAGUE" })
        }
    }

    @Test
    fun testRandomFunction01() {
        val t = DMLTestsData.Cities
        withTables(t) {
            if (t.selectAll().count() == 0) {
                t.insert { it[t.name] = "city-1" }
            }

            val rand = Random()
            val resultRow = t.slice(rand).selectAll().limit(1).single()
            assertNotNull(resultRow[rand])
        }
    }

    @Test fun testRegexp01() {
        withCitiesAndUsers(listOf(TestDB.SQLITE, TestDB.SQLSERVER)) { _, users, _ ->
            assertEquals(2, users.select { users.id regexp "a.+" }.count())
            assertEquals(1, users.select { users.id regexp "an.+" }.count())
            assertEquals(users.selectAll().count(), users.select { users.id regexp ".*" }.count())
            assertEquals(2, users.select { users.id regexp ".+y" }.count())
        }
    }

    @Test fun testRegexp02() {
        withCitiesAndUsers(listOf(TestDB.SQLITE, TestDB.SQLSERVER)) { _, users, _ ->
            assertEquals(2, users.select { users.id.regexp(stringLiteral("a.+")) }.count())
            assertEquals(1, users.select { users.id.regexp(stringLiteral("an.+")) }.count())
            assertEquals(users.selectAll().count(), users.select { users.id.regexp(stringLiteral(".*")) }.count())
            assertEquals(2, users.select { users.id.regexp(stringLiteral(".+y")) }.count())
        }
    }

    @Test fun testConcat01() {
        withCitiesAndUsers { cities, _, _ ->
            val concatField = concat(stringLiteral("Foo"), stringLiteral("Bar"))
            val result = cities.slice(concatField).selectAll().limit(1).single()
            assertEquals("FooBar", result[concatField])

            val concatField2 = concat("!", listOf(stringLiteral("Foo"), stringLiteral("Bar")))
            val result2 = cities.slice(concatField2).selectAll().limit(1).single()
            assertEquals("Foo!Bar", result2[concatField2])
        }
    }

    @Test fun testConcat02() {
        withCitiesAndUsers { _, users, _ ->
            val concatField = concat(users.id, stringLiteral(" - "), users.name)
            val result = users.slice(concatField).select{ users.id eq "andrey" }.single()
            assertEquals("andrey - Andrey", result[concatField])

            val concatField2 = concat("!", listOf(users.id, users.name))
            val result2 = users.slice(concatField2).select{ users.id eq "andrey" }.single()
            assertEquals("andrey!Andrey", result2[concatField2])
        }
    }

    @Test
    fun testCustomStringFunctions01() {
        withCitiesAndUsers { cities, _, _ ->
            val customLower = DMLTestsData.Cities.name.function("lower")
            assert(cities.slice(customLower).selectAll().any { it[customLower] == "prague" })

            val customUpper = DMLTestsData.Cities.name.function("UPPER")
            assert(cities.slice(customUpper).selectAll().any { it[customUpper] == "PRAGUE" })
        }
    }

    @Test
    fun testCustomStringFunctions02() {
        withCitiesAndUsers { cities, _, _ ->
            val replace = CustomStringFunction("REPLACE", cities.name, stringParam("gue"), stringParam("foo"))
            val result = cities.slice(replace).select { cities.name eq "Prague" }.singleOrNull()
            assertEquals("Prafoo", result?.get(replace))
        }
    }

    @Test
    fun testCustomIntegerFunctions01() {
        withCitiesAndUsers { cities, _, _ ->
            val ids = cities.selectAll().map { it[DMLTestsData.Cities.id] }.toList()
            assertEqualCollections(listOf(1,2,3), ids)

            val sqrt = DMLTestsData.Cities.id.function("SQRT")
            val sqrtIds = cities.slice(sqrt).selectAll().map { it[sqrt] }.toList()
            assertEqualCollections(listOf(1,1,1), sqrtIds)
        }
    }

    @Test
    fun testCustomIntegerFunctions02() {
        withCitiesAndUsers { cities, _, _ ->
            val power = CustomLongFunction("POWER", cities.id, intParam(2))
            val ids = cities.slice(power).selectAll().map { it[power] }
            assertEqualCollections(listOf(1L,4L,9L), ids)
        }
    }
}