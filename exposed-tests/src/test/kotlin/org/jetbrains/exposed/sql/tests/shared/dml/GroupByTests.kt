package org.jetbrains.exposed.sql.tests.shared.dml

import org.jetbrains.exposed.exceptions.UnsupportedByDialectException
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.tests.DatabaseTestsBase
import org.jetbrains.exposed.sql.tests.TestDB
import org.jetbrains.exposed.sql.tests.currentDialectTest
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
        withCitiesAndUsers {
            val cAlias = users.id.count().alias("c")
            ((cities innerJoin users)
                .slice(cities.name, users.id.count(), cAlias)
                .selectAll()
                .groupBy(cities.name))
                .forEach {
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

            val dAlias = scopedUsers.id.count().alias("d")
            ((cities innerJoin scopedUsers)
                .slice(cities.name, scopedUsers.id.count(), dAlias)
                .selectAll()
                .groupBy(cities.name))
                .forEach {
                    val cityName = it[cities.name]
                    val userCount = it[scopedUsers.id.count()]
                    val userCountAlias = it[dAlias]
                    when (cityName) {
                        "Munich" -> {
                            assertEquals(2, userCount)
                            assertEquals(2, userCountAlias)
                        }

                        else -> error("Unknow city $cityName")
                    }
                }

            ((cities innerJoin scopedUsers.stripDefaultFilter())
                .slice(cities.name, scopedUsers.id.count(), dAlias)
                .selectAll()
                .groupBy(cities.name))
                .forEach {
                    val cityName = it[cities.name]
                    val userCount = it[scopedUsers.id.count()]
                    val userCountAlias = it[dAlias]
                    when (cityName) {
                        "Munich" -> {
                            assertEquals(2, userCount)
                            assertEquals(2, userCountAlias)
                        }
                        "Prague" -> {
                            assertEquals(0, userCount)
                            assertEquals(0, userCountAlias)
                        }
                        "St. Petersburg" -> {
                            assertEquals(1, userCount)
                            assertEquals(1, userCountAlias)
                        }
                        else -> error("Unknow city $cityName")
                    }
                }
        }
    }

    @Test
    fun testGroupBy02() {
        withCitiesAndUsers {
            (cities innerJoin users)
                .slice(cities.name, users.id.count())
                .selectAll()
                .groupBy(cities.name)
                .having { users.id.count() eq 1 }
                .toList().let { r ->
                    assertEquals(1, r.size)
                    assertEquals("St. Petersburg", r[0][cities.name])
                    val count = r[0][users.id.count()]
                    assertEquals(1, count)
                }

            (cities innerJoin scopedUsers)
                .slice(cities.name, scopedUsers.id.count())
                .selectAll()
                .groupBy(cities.name)
                .having { scopedUsers.id.count() eq 2 }
                .toList().let { r ->
                    assertEquals(1, r.size)
                    assertEquals("Munich", r[0][cities.name])
                    val count = r[0][scopedUsers.id.count()]
                    assertEquals(2, count)
                }

            (cities innerJoin scopedUsers)
                .slice(cities.name, scopedUsers.id.count())
                .selectAll()
                .groupBy(cities.name)
                .having { scopedUsers.id.count() eq 1 }
                .toList().let { r -> assertEquals(0, r.size) }

            (cities innerJoin scopedUsers.stripDefaultFilter())
                .slice(cities.name, scopedUsers.id.count())
                .selectAll()
                .groupBy(cities.name)
                .having { scopedUsers.id.count() eq 1 }
                .toList().let { r ->
                    assertEquals(1, r.size)
                    assertEquals("St. Petersburg", r[0][cities.name])
                    assertEquals(1, r[0][scopedUsers.id.count()])
                }
        }
    }

    @Test
    fun testGroupBy03() {
        withCitiesAndUsers {
            val maxExpr = cities.id.max()
            (cities innerJoin users)
                .slice(cities.name, users.id.count(), maxExpr)
                .selectAll()
                .groupBy(cities.name)
                .having { users.id.count().eq(maxExpr) }
                .orderBy(cities.name)
                .toList().let { r ->
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

            (cities innerJoin scopedUsers)
                .slice(cities.name, scopedUsers.id.count(), maxExpr)
                .selectAll()
                .groupBy(cities.name)
                .having { scopedUsers.id.count().eq(maxExpr) }
                .orderBy(cities.name)
                .toList().let { r ->
                    assertEquals(1, r.size)
                    0.let {
                        assertEquals("Munich", r[it][cities.name])
                        val count = r[it][scopedUsers.id.count()]
                        assertEquals(2, count)
                        val max = r[it][maxExpr]
                        assertEquals(2, max)
                    }
                }

            (cities innerJoin scopedUsers.stripDefaultFilter())
                .slice(cities.name, scopedUsers.id.count(), maxExpr)
                .selectAll()
                .groupBy(cities.name)
                .having { scopedUsers.id.count().eq(maxExpr) }
                .orderBy(cities.name)
                .toList().let { r ->
                    assertEquals(2, r.size)
                    0.let {
                        assertEquals("Munich", r[it][cities.name])
                        val count = r[it][scopedUsers.id.count()]
                        assertEquals(2, count)
                        val max = r[it][maxExpr]
                        assertEquals(2, max)
                    }
                    1.let {
                        assertEquals("St. Petersburg", r[it][cities.name])
                        val count = r[it][scopedUsers.id.count()]
                        assertEquals(1, count)
                        val max = r[it][maxExpr]
                        assertEquals(1, max)
                    }
                }
        }
    }

    @Test
    fun testGroupBy04() {
        withCitiesAndUsers {
            (cities innerJoin users)
                .slice(cities.name, users.id.count(), cities.id.max())
                .selectAll()
                .groupBy(cities.name)
                .having { users.id.count() lessEq 42L }
                .orderBy(cities.name)
                .toList().let { r ->
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

            (cities innerJoin scopedUsers)
                .slice(cities.name, scopedUsers.id.count(), cities.id.max())
                .selectAll()
                .groupBy(cities.name)
                .having { scopedUsers.id.count() lessEq 42L }
                .orderBy(cities.name)
                .toList().let { r ->
                    assertEquals(1, r.size)
                    r[0].let {
                        assertEquals("Munich", it[cities.name])
                        val count = it[scopedUsers.id.count()]
                        assertEquals(2, count)
                    }
                }

            (cities innerJoin scopedUsers.stripDefaultFilter())
                .slice(cities.name, scopedUsers.id.count(), cities.id.max())
                .selectAll()
                .groupBy(cities.name)
                .having { scopedUsers.id.count() lessEq 42L }
                .orderBy(cities.name)
                .toList().let { r ->
                    assertEquals(2, r.size)
                    r[0].let {
                        assertEquals("Munich", it[cities.name])
                        val count = it[scopedUsers.id.count()]
                        assertEquals(2, count)
                    }
                    r[1].let {
                        assertEquals("St. Petersburg", it[cities.name])
                        val count = it[scopedUsers.id.count()]
                        assertEquals(1, count)
                    }
                }
        }
    }

    @Test
    fun testGroupBy05() {
        withCitiesAndUsers {
            users.cityId.max().let { maxNullableCityId ->
                users.slice(maxNullableCityId)
                    .selectAll()
                    .map { it[maxNullableCityId] }
                    .let { result ->
                        assertEquals(result.size, 1)
                        assertNotNull(result.single())
                    }

                users.slice(maxNullableCityId)
                    .select { users.cityId.isNull() }
                    .map { it[maxNullableCityId] }.let { result ->
                        assertEquals(result.size, 1)
                        assertNull(result.single())
                    }
            }

            scopedUsers.cityId.max().let { scopedMaxNullableCityId ->
                scopedUsers.slice(scopedMaxNullableCityId)
                    .selectAll()
                    .map { it[scopedMaxNullableCityId] }
                    .let { result ->
                        assertEquals(result.size, 1)
                        assertNotNull(result.single())
                    }

                scopedUsers.slice(scopedMaxNullableCityId)
                    .select { scopedUsers.cityId.isNull() }
                    .map { it[scopedMaxNullableCityId] }
                    .let { result ->
                        assertEquals(result.size, 1)
                        assertNull(result.single())
                    }
            }

        }
    }

    @Test
    fun testGroupBy06() {
        withCitiesAndUsers {
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
        withCitiesAndUsers {
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
        withCitiesAndUsers(listOf(TestDB.SQLITE)) {
            fun <T : String?> GroupConcat<T>.checkExcept(vararg dialects: KClass<out DatabaseDialect>, assert: (Map<String, String?>) -> Unit) {
                try {
                    cities.leftJoin(users)
                        .slice(cities.name, this)
                        .selectAll()
                        .groupBy(cities.id, cities.name)
                        .associate { it[cities.name] to it[this] }
                        .let { result -> assert(result) }
                } catch (e: UnsupportedByDialectException) {
                    assertTrue(e.dialect::class in dialects, e.message!!)
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

            users.name.groupConcat(separator = " | ", distinct = true).checkExcept(OracleDialect::class) {
                assertEquals(3, it.size)
                assertEquals("Andrey", it["St. Petersburg"])
                when (currentDialectTest) {
                    is MariaDBDialect -> assertEquals(true, it["Munich"] in listOf("Sergey | Eugene", "Eugene | Sergey"))
                    is MysqlDialect, is SQLServerDialect, is H2Dialect, is PostgreSQLDialect, is PostgreSQLNGDialect ->
                        assertEquals("Eugene | Sergey", it["Munich"])
                    else -> assertEquals("Sergey | Eugene", it["Munich"])
                }
                assertNull(it["Prague"])
            }

            users.name.groupConcat(separator = " | ", orderBy = users.name to SortOrder.ASC).checkExcept{
                assertEquals(3, it.size)
                assertEquals("Andrey", it["St. Petersburg"])
                assertEquals("Eugene | Sergey", it["Munich"])
                assertNull(it["Prague"])
            }

            users.name.groupConcat(separator = " | ", orderBy = users.name to SortOrder.DESC).checkExcept{
                assertEquals(3, it.size)
                assertEquals("Andrey", it["St. Petersburg"])
                assertEquals("Sergey | Eugene", it["Munich"])
                assertNull(it["Prague"])
            }
        }
    }

    @Test
    fun testGroupConcatWithADefaultFilter() {
        withCitiesAndUsers(listOf(TestDB.SQLITE)) {
            fun <T : String?> GroupConcat<T>.checkExcept(vararg dialects: KClass<out DatabaseDialect>, assert: (Map<String, String?>) -> Unit) {
                try {
                    cities.leftJoin(scopedUsers)
                        .slice(cities.name, this)
                        .selectAll()
                        .groupBy(cities.id, cities.name)
                        .associate { it[cities.name] to it[this] }
                        .let { result -> assert(result) }
                } catch (e: UnsupportedByDialectException) {
                    assertTrue(e.dialect::class in dialects, e.message!!)
                }
            }

            scopedUsers.name
                .groupConcat()
                .checkExcept(
                    PostgreSQLDialect::class,
                    PostgreSQLNGDialect::class,
                    SQLServerDialect::class,
                    OracleDialect::class
                ) { assertEquals(1, it.size) }

            scopedUsers.name
                .groupConcat(separator = ", ")
                .checkExcept(OracleDialect::class) {
                    assertEquals(1, it.size)
                    when (currentDialectTest) {
                        is MariaDBDialect -> assertEquals(true, it["Munich"] in listOf("Sergey, Eugene", "Eugene, Sergey"))
                        is MysqlDialect, is SQLServerDialect -> assertEquals("Eugene, Sergey", it["Munich"])
                        else -> assertEquals("Sergey, Eugene", it["Munich"])
                    }
                assertNull(it["Prague"])
            }

            scopedUsers.name
                .groupConcat(separator = " | ", distinct = true)
                .checkExcept(OracleDialect::class) {
                    assertEquals(1, it.size)
                    when (currentDialectTest) {
                        is MariaDBDialect -> assertEquals(true, it["Munich"] in listOf("Sergey | Eugene", "Eugene | Sergey"))
                        is MysqlDialect,
                        is SQLServerDialect,
                        is H2Dialect,
                        is PostgreSQLDialect,
                        is PostgreSQLNGDialect -> assertEquals("Eugene | Sergey", it["Munich"])
                        else -> assertEquals("Sergey | Eugene", it["Munich"])
                    }
                    assertNull(it["Prague"])
                }

            scopedUsers.name
                .groupConcat(separator = " | ", orderBy = scopedUsers.name to SortOrder.ASC)
                .checkExcept{
                    assertEquals(1, it.size)
                    assertEquals("Eugene | Sergey", it["Munich"])
                }

            scopedUsers.name
                .groupConcat(separator = " | ", orderBy = scopedUsers.name to SortOrder.DESC)
                .checkExcept{
                    assertEquals(1, it.size)
                    assertEquals("Sergey | Eugene", it["Munich"])
            }
        }
    }
}
