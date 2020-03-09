package org.jetbrains.exposed.sql.tests.shared.dml

import org.jetbrains.exposed.exceptions.UnsupportedByDialectException
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.tests.DatabaseTestsBase
import org.jetbrains.exposed.sql.tests.TestDB
import org.jetbrains.exposed.sql.tests.currentDialectTest
import org.jetbrains.exposed.sql.tests.shared.assertEquals
import org.jetbrains.exposed.sql.vendors.*
import org.junit.Test
import java.math.BigDecimal
import kotlin.reflect.KClass
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GroupByTests : DatabaseTestsBase() {
    @Test
    fun testGroupBy01() {
        withCitiesAndUsers { cities, users, userData ->
            val cAlias = users.id.count().alias("c")
            ((cities innerJoin users).slice(cities.name, users.id.count(), cAlias).selectAll().groupBy(cities.name)).forEach {
                val cityName = it[cities.name]
                val userCount = it[users.id.count()]
                val userCountAlias = it[cAlias]
                when (cityName) {
                    "Munich" -> assertEquals(2, userCount)
                    "Prague" -> assertEquals(0, userCount)
                    "St. Petersburg" -> assertEquals(1, userCount)
                    else -> error("Unknow city $cityName")
                }
            }
        }
    }

    @Test
    fun testGroupBy02() {
        withCitiesAndUsers { cities, users, userData ->
            val r = (cities innerJoin users).slice(cities.name, users.id.count()).selectAll().groupBy(cities.name).having { users.id.count() eq 1 }.toList()
            assertEquals(1, r.size)
            assertEquals("St. Petersburg", r[0][cities.name])
            val count = r[0][users.id.count()]
            assertEquals(1, count)
        }
    }

    @Test
    fun testGroupBy03() {
        withCitiesAndUsers { cities, users, userData ->
            val maxExpr = cities.id.max()
            val r = (cities innerJoin users).slice(cities.name, users.id.count(), maxExpr).selectAll()
                .groupBy(cities.name)
                .having{users.id.count().eq(maxExpr)}
                .orderBy(cities.name)
                .toList()

            assertEquals(2, r.size)
            0.let {
                assertEquals("Munich", r[it][cities.name])
                val count = r[it][users.id.count()]
                assertEquals(2, count)
                val max = r[it][maxExpr]
                assertEquals(2, max)
            }
            1.let {
                assertEquals("St. Petersburg", r[it][cities.name])
                val count = r[it][users.id.count()]
                assertEquals(1, count)
                val max = r[it][maxExpr]
                assertEquals(1, max)
            }
        }
    }

    @Test
    fun testGroupBy04() {
        withCitiesAndUsers { cities, users, userData ->
            val r = (cities innerJoin users).slice(cities.name, users.id.count(), cities.id.max()).selectAll()
                .groupBy(cities.name)
                .having { users.id.count() lessEq 42L }
                .orderBy(cities.name)
                .toList()

            assertEquals(2, r.size)
            0.let {
                assertEquals("Munich", r[it][cities.name])
                val count = r[it][users.id.count()]
                assertEquals(2, count)
            }
            1.let {
                assertEquals("St. Petersburg", r[it][cities.name])
                val count = r[it][users.id.count()]
                assertEquals(1, count)
            }
        }
    }

    @Test
    fun testGroupBy05() {
        withCitiesAndUsers { cities, users, userData ->
            val maxNullableCityId = users.cityId.max()

            users.slice(maxNullableCityId).selectAll()
                .map { it[maxNullableCityId] }.let { result ->
                    assertEquals(result.size, 1)
                    assertNotNull(result.single())
                }

            users.slice(maxNullableCityId).select { users.cityId.isNull() }
                .map { it[maxNullableCityId] }.let { result ->
                    assertEquals(result.size, 1)
                    assertNull(result.single())
                }
        }
    }

    @Test
    fun testGroupBy06() {
        withCitiesAndUsers { cities, users, userData ->
            val maxNullableId = cities.id.max()

            cities.slice(maxNullableId).selectAll()
                .map { it[maxNullableId] }.let { result ->
                    assertEquals(result.size, 1)
                    assertNotNull(result.single())
                }

            cities.slice(maxNullableId).select { cities.id.isNull() }
                .map { it[maxNullableId] }.let { result: List<Int?> ->
                    assertEquals(result.size, 1)
                    assertNull(result.single())
                }
        }
    }

    @Test
    fun testGroupBy07() {
        withCitiesAndUsers { cities, users, userData ->
            val avgIdExpr = cities.id.avg()
            val avgId = BigDecimal.valueOf(cities.selectAll().map { it[cities.id] }.average())

            cities.slice(avgIdExpr).selectAll()
                .map { it[avgIdExpr] }.let { result ->
                    assertEquals(result.size, 1)
                    assertEquals(result.single()!!.compareTo(avgId), 0)
                }

            cities.slice(avgIdExpr).select { cities.id.isNull() }
                .map { it[avgIdExpr] }.let { result ->
                    assertEquals(result.size, 1)
                    assertNull(result.single())
                }
        }
    }

    @Test
    fun testGroupConcat() {
        withCitiesAndUsers(listOf(TestDB.SQLITE)) { cities, users, _ ->
            fun <T : String?> GroupConcat<T>.checkExcept(vararg dialects: KClass<out DatabaseDialect>, assert: (Map<String, String?>) ->Unit) {
                try {
                    val result = cities.leftJoin(users)
                        .slice(cities.name, this)
                        .selectAll()
                        .groupBy(cities.id, cities.name).associate {
                            it[cities.name] to it[this]
                        }
                    assert(result)
                } catch (e: UnsupportedByDialectException) {
                    assertTrue(e.dialect::class in dialects, e.message!! )
                }
            }
            users.name.groupConcat().checkExcept(PostgreSQLDialect::class, PostgreSQLNGDialect::class, SQLServerDialect::class, OracleDialect::class) {
                assertEquals(3, it.size)
            }

            users.name.groupConcat(separator = ", ").checkExcept(OracleDialect::class) {
                assertEquals(3, it.size)
                assertEquals("Andrey", it["St. Petersburg"])
                when (currentDialectTest) {
                    is MariaDBDialect -> assertEquals(true, it["Munich"] in listOf("Sergey, Eugene", "Eugene, Sergey"))
                    is MysqlDialect, is SQLServerDialect -> assertEquals("Eugene, Sergey", it["Munich"])
                    else -> assertEquals("Sergey, Eugene", it["Munich"])
                }

                assertNull(it["Prague"])
            }

            users.name.groupConcat(separator = " | ", distinct = true).checkExcept(PostgreSQLDialect::class, PostgreSQLNGDialect::class, OracleDialect::class) {
                assertEquals(3, it.size)
                assertEquals("Andrey", it["St. Petersburg"])
                when (currentDialectTest) {
                    is MariaDBDialect -> assertEquals(true, it["Munich"] in listOf("Sergey | Eugene", "Eugene | Sergey"))
                    is MysqlDialect, is SQLServerDialect, is H2Dialect -> assertEquals("Eugene | Sergey", it["Munich"])
                    else -> assertEquals("Sergey | Eugene", it["Munich"])
                }
                assertNull(it["Prague"])
            }

            users.name.groupConcat(separator = " | ", orderBy = users.name to SortOrder.ASC).checkExcept(
                PostgreSQLDialect::class, PostgreSQLNGDialect::class) {
                assertEquals(3, it.size)
                assertEquals("Andrey", it["St. Petersburg"])
                assertEquals("Eugene | Sergey", it["Munich"])
                assertNull(it["Prague"])
            }

            users.name.groupConcat(separator = " | ", orderBy = users.name to SortOrder.DESC).checkExcept(
                PostgreSQLDialect::class, PostgreSQLNGDialect::class) {
                assertEquals(3, it.size)
                assertEquals("Andrey", it["St. Petersburg"])
                assertEquals("Sergey | Eugene", it["Munich"])
                assertNull(it["Prague"])
            }
        }
    }
}