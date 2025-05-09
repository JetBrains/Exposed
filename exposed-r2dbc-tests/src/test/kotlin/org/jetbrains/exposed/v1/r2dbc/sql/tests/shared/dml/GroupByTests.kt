package org.jetbrains.exposed.v1.r2dbc.sql.tests.shared.dml

import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.core.vendors.*
import org.jetbrains.exposed.v1.exceptions.UnsupportedByDialectException
import org.jetbrains.exposed.v1.r2dbc.sql.select
import org.jetbrains.exposed.v1.r2dbc.sql.selectAll
import org.jetbrains.exposed.v1.r2dbc.sql.tests.R2dbcDatabaseTestsBase
import org.jetbrains.exposed.v1.r2dbc.sql.tests.currentDialectTest
import org.jetbrains.exposed.v1.r2dbc.sql.tests.forEach
import org.jetbrains.exposed.v1.r2dbc.sql.tests.shared.assertEquals
import org.jetbrains.exposed.v1.sql.*
import org.junit.Test
import java.math.BigDecimal
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GroupByTests : R2dbcDatabaseTestsBase() {
    @Test
    fun testGroupBy01() {
        withCitiesAndUsers { cities, users, _ ->
            val cAlias = users.id.count().alias("c")
            ((cities innerJoin users).select(cities.name, users.id.count(), cAlias).groupBy(cities.name)).forEach {
                val cityName = it[cities.name]
                val userCount = it[users.id.count()]
                val userCountAlias = it[cAlias]
                when (cityName) {
                    "Munich" -> assertEquals(2, userCount)
                    "Prague" -> assertEquals(0, userCount)
                    "St. Petersburg" -> assertEquals(1, userCount)
                    else -> error("Unknown city $cityName")
                }
                assertEquals(userCount, userCountAlias)
            }
        }
    }

    @Test
    fun testGroupBy02() {
        withCitiesAndUsers { cities, users, _ ->
            val r = (cities innerJoin users).select(cities.name, users.id.count())
                .groupBy(cities.name)
                .having { users.id.count() eq 1 }
                .toList()
            assertEquals(1, r.size)
            assertEquals("St. Petersburg", r[0][cities.name])
            val count = r[0][users.id.count()]
            assertEquals(1, count)
        }
    }

    @Test
    fun testGroupBy03() {
        withCitiesAndUsers { cities, users, _ ->
            val maxExpr = cities.id.max()
            val r = (cities innerJoin users).select(cities.name, users.id.count(), maxExpr)
                .groupBy(cities.name)
                .having { users.id.count().eq<Number, Long, Int>(maxExpr) }
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
        withCitiesAndUsers { cities, users, _ ->
            val r = (cities innerJoin users).select(cities.name, users.id.count(), cities.id.max())
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
        withCitiesAndUsers { _, users, _ ->
            val maxNullableCityId = users.cityId.max()

            users.select(maxNullableCityId)
                .map { it[maxNullableCityId] }.toList().let { result ->
                    assertEquals(result.size, 1)
                    assertNotNull(result.single())
                }

            users.select(maxNullableCityId).where { users.cityId.isNull() }
                .map { it[maxNullableCityId] }.toList().let { result ->
                    assertEquals(result.size, 1)
                    assertNull(result.single())
                }
        }
    }

    @Test
    fun testGroupBy06() {
        withCitiesAndUsers { cities, _, _ ->
            val maxNullableId = cities.id.max()

            cities.select(maxNullableId)
                .map { it[maxNullableId] }.toList().let { result ->
                    assertEquals(result.size, 1)
                    assertNotNull(result.single())
                }

            cities.select(maxNullableId).where { cities.id.isNull() }
                .map { it[maxNullableId] }.toList().let { result: List<Int?> ->
                    assertEquals(result.size, 1)
                    assertNull(result.single())
                }
        }
    }

    @Test
    fun testGroupBy07() {
        withCitiesAndUsers { cities, _, _ ->
            val avgIdExpr = cities.id.avg()
            val avgId = BigDecimal.valueOf(cities.selectAll().map { it[cities.id] }.toList().average())

            cities.select(avgIdExpr)
                .map { it[avgIdExpr] }.toList().let { result ->
                    assertEquals(result.size, 1)
                    assertEquals(result.single()!!.compareTo(avgId), 0)
                }

            cities.select(avgIdExpr).where { cities.id.isNull() }
                .map { it[avgIdExpr] }.toList().let { result ->
                    assertEquals(result.size, 1)
                    assertNull(result.single())
                }
        }
    }

    @Test
    fun testGroupConcat() {
        withCitiesAndUsers { cities, users, _ ->
            suspend fun <T : String?> GroupConcat<T>.checkExcept(vararg dialects: VendorDialect.DialectNameProvider, assert: (Map<String, String?>) -> Unit) {
                try {
                    val result = cities.leftJoin(users)
                        .select(cities.name, this)
                        .groupBy(cities.id, cities.name).toList().associate {
                            it[cities.name] to it[this]
                        }
                    assert(result)
                } catch (e: UnsupportedByDialectException) {
                    val dialectNames = dialects.map { it.dialectName }
                    val dialect = e.dialect
                    val check = when {
                        dialect.name in dialectNames -> true
                        dialect is H2Dialect && dialect.delegatedDialectNameProvider != null -> dialect.delegatedDialectNameProvider!!.dialectName in dialectNames
                        else -> false
                    }
                    assertTrue(check, e.message!!)
                }
            }

            // separator must be specified by PostgreSQL and SQL Server
            users.name.groupConcat().checkExcept(PostgreSQLDialect, PostgreSQLNGDialect, SQLServerDialect) {
                assertEquals(3, it.size)
            }

            users.name.groupConcat(separator = ", ").checkExcept {
                assertEquals(3, it.size)
                assertEquals("Andrey", it["St. Petersburg"])
                when (currentDialectTest) {
                    // return order is arbitrary if no ORDER BY is specified
                    is MariaDBDialect -> assertTrue(it["Munich"] in listOf("Sergey, Eugene", "Eugene, Sergey"))
                    is MysqlDialect, is SQLServerDialect -> assertEquals("Eugene, Sergey", it["Munich"])
                    else -> assertEquals("Sergey, Eugene", it["Munich"])
                }

                assertNull(it["Prague"])
            }

            users.name.groupConcat(separator = " | ", distinct = true).checkExcept(OracleDialect, SQLServerDialect) {
                assertEquals(3, it.size)
                assertEquals("Andrey", it["St. Petersburg"])
                when (currentDialectTest) {
                    is MariaDBDialect -> assertEquals(true, it["Munich"] in listOf("Sergey | Eugene", "Eugene | Sergey"))
                    is MysqlDialect, is PostgreSQLDialect -> assertEquals("Eugene | Sergey", it["Munich"])
                    is H2Dialect -> {
                        if (currentDialect.h2Mode == H2Dialect.H2CompatibilityMode.SQLServer) {
                            assertEquals("Sergey | Eugene", it["Munich"])
                        } else {
                            assertEquals("Eugene | Sergey", it["Munich"])
                        }
                    }
                    else -> assertEquals("Sergey | Eugene", it["Munich"])
                }
                assertNull(it["Prague"])
            }

            users.name.groupConcat(separator = " | ", orderBy = users.name to SortOrder.ASC).checkExcept {
                assertEquals(3, it.size)
                assertEquals("Andrey", it["St. Petersburg"])
                assertEquals("Eugene | Sergey", it["Munich"])
                assertNull(it["Prague"])
            }

            users.name.groupConcat(separator = " | ", orderBy = users.name to SortOrder.DESC).checkExcept {
                assertEquals(3, it.size)
                assertEquals("Andrey", it["St. Petersburg"])
                assertEquals("Sergey | Eugene", it["Munich"])
                assertNull(it["Prague"])
            }
        }
    }
}
