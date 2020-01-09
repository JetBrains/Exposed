package org.jetbrains.exposed.sql.tests.shared

import org.hamcrest.Matchers.containsInAnyOrder
import org.hamcrest.Matchers.not
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IdTable
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.exceptions.UnsupportedByDialectException
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.Random
import org.jetbrains.exposed.sql.tests.DatabaseTestsBase
import org.jetbrains.exposed.sql.tests.TestDB
import org.jetbrains.exposed.sql.tests.currentDialectTest
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.vendors.*
import org.junit.Assert.assertThat
import org.junit.Test
import java.math.BigDecimal
import java.util.*
import kotlin.reflect.KClass
import kotlin.test.*

object DMLTestsData {
    object Cities : Table() {
        val id = integer("cityId").autoIncrement("cities_seq").primaryKey() // PKColumn<Int>
        val name = varchar("name", 50) // Column<String>
    }

    object Users : Table() {
        val id = varchar("id", 10).primaryKey() // PKColumn<String>
        val name = varchar("name", length = 50) // Column<String>
        val cityId = (integer("city_id") references Cities.id).nullable() // Column<Int?>
    }

    object UserData : Table() {
        val user_id = varchar("user_id", 10) references Users.id
        val comment = varchar("comment", 30)
        val value = integer("value")
    }
}

fun DatabaseTestsBase.withCitiesAndUsers(exclude: List<TestDB> = emptyList(), statement: Transaction.(cities: DMLTestsData.Cities, users: DMLTestsData.Users, userData: DMLTestsData.UserData) -> Unit) {
    val Users = DMLTestsData.Users
    val Cities = DMLTestsData.Cities
    val UserData = DMLTestsData.UserData

    withTables(exclude, Cities, Users, UserData) {
        val saintPetersburgId = Cities.insert {
            it[name] = "St. Petersburg"
        } get Cities.id

        val munichId = Cities.insert {
            it[name] = "Munich"
        } get Cities.id

        Cities.insert {
            it[name] = "Prague"
        }

        Users.insert {
            it[id] = "andrey"
            it[name] = "Andrey"
            it[cityId] = saintPetersburgId
        }

        Users.insert {
            it[id] = "sergey"
            it[name] = "Sergey"
            it[cityId] = munichId
        }

        Users.insert {
            it[id] = "eugene"
            it[name] = "Eugene"
            it[cityId] = munichId
        }

        Users.insert {
            it[id] = "alex"
            it[name] = "Alex"
            it[cityId] = null
        }

        Users.insert {
            it[id] = "smth"
            it[name] = "Something"
            it[cityId] = null
        }

        UserData.insert {
            it[user_id] = "smth"
            it[comment] = "Something is here"
            it[value] = 10
        }

        UserData.insert {
            it[user_id] = "smth"
            it[comment] = "Comment #2"
            it[value] = 20
        }

        UserData.insert {
            it[user_id] = "eugene"
            it[comment] = "Comment for Eugene"
            it[value] = 20
        }

        UserData.insert {
            it[user_id] = "sergey"
            it[comment] = "Comment for Sergey"
            it[value] = 30
        }

        statement(Cities, Users, UserData)
    }
}

class DMLTests : DatabaseTestsBase() {

    @Test
    fun testUpdate01() {
        withCitiesAndUsers { cities, users, userData ->
            val alexId = "alex"
            val alexName = users.slice(users.name).select { users.id.eq(alexId) }.first()[users.name]
            assertEquals("Alex", alexName)

            val newName = "Alexey"
            users.update({ users.id.eq(alexId) }) {
                it[users.name] = newName
            }

            val alexNewName = users.slice(users.name).select { users.id.eq(alexId) }.first()[users.name]
            assertEquals(newName, alexNewName)
        }
    }

    @Test
    fun testUpdateWithLimit01() {
        withCitiesAndUsers(listOf(TestDB.SQLITE, TestDB.POSTGRESQL)) { cities, users, userData ->
            val aNames = users.slice(users.name).select { users.id like "a%" }.map { it[users.name] }
            assertEquals(2, aNames.size)

            users.update({ users.id like "a%" }, 1) {
                it[users.id] = "NewName"
            }

            val unchanged = users.slice(users.name).select { users.id like "a%" }.count()
            val changed = users.slice(users.name).select { users.id eq "NewName" }.count()
            assertEquals(1, unchanged)
            assertEquals(1, changed)
        }
    }

    @Test
    fun testUpdateWithLimit02() {
        val dialects = TestDB.values().toList() - listOf(TestDB.SQLITE, TestDB.POSTGRESQL)
        withCitiesAndUsers(dialects) { cities, users, userData ->
            expectException<UnsupportedByDialectException> {
                users.update({ users.id like "a%" }, 1) {
                    it[users.id] = "NewName"
                }
            }
        }
    }

    @Test
    fun testPreparedStatement() {
        withCitiesAndUsers { cities, users, userData ->
            val name = users.select { users.id eq "eugene" }.first()[users.name]
            assertEquals("Eugene", name)
        }
    }

    @Test
    fun testDelete01() {
        withCitiesAndUsers { cities, users, userData ->
            userData.deleteAll()
            val userDataExists = userData.selectAll().any()
            assertEquals(false, userDataExists)

            val smthId = users.slice(users.id).select { users.name.like("%thing") }.single()[users.id]
            assertEquals("smth", smthId)

            users.deleteWhere { users.name like "%thing" }
            val hasSmth = users.slice(users.id).select { users.name.like("%thing") }.any()
            assertEquals(false, hasSmth)
        }
    }

    @Test
    fun testDeleteWithLimitAndOffset01() {
        withCitiesAndUsers(exclude = listOf(TestDB.SQLITE, TestDB.POSTGRESQL, TestDB.ORACLE)) { cities, users, userData ->
            userData.deleteWhere(limit = 1) { userData.value eq 20 }
            userData.slice(userData.user_id, userData.value).select { userData.value eq 20 }.let {
                assertEquals(1, it.count())
                val expected = if (currentDialectTest is H2Dialect) "smth" else "eugene"
                assertEquals(expected, it.single()[userData.user_id])
            }
        }
    }

    // select expressions
    @Test
    fun testSelect() {
        withCitiesAndUsers { cities, users, userData ->
            users.select { users.id.eq("andrey") }.forEach {
                val userId = it[users.id]
                val userName = it[users.name]
                when (userId) {
                    "andrey" -> assertEquals("Andrey", userName)
                    else -> error("Unexpected user $userId")
                }
            }
        }
    }

    @Test
    fun testSelectAnd() {
        withCitiesAndUsers { cities, users, userData ->
            users.select { users.id.eq("andrey") and users.name.eq("Andrey") }.forEach {
                val userId = it[users.id]
                val userName = it[users.name]
                when (userId) {
                    "andrey" -> assertEquals("Andrey", userName)
                    else -> error("Unexpected user $userId")
                }
            }
        }
    }

    @Test
    fun testSelectOr() {
        withCitiesAndUsers { cities, users, userData ->
            users.select { users.id.eq("andrey") or users.name.eq("Andrey") }.forEach {
                val userId = it[users.id]
                val userName = it[users.name]
                when (userId) {
                    "andrey" -> assertEquals("Andrey", userName)
                    else -> error("Unexpected user $userId")
                }
            }
        }
    }

    @Test
    fun testSelectNot() {
        withCitiesAndUsers { cities, users, userData ->
            users.select { org.jetbrains.exposed.sql.not(users.id.eq("andrey")) }.forEach {
                val userId = it[users.id]
                val userName = it[users.name]
                if (userId == "andrey") {
                    error("Unexpected user $userId")
                }
            }
        }
    }

    // manual join
    @Test
    fun testJoin01() {
        withCitiesAndUsers { cities, users, userData ->
            (users innerJoin cities).slice(users.name, cities.name).select { (users.id.eq("andrey") or users.name.eq("Sergey")) and users.cityId.eq(cities.id) }.forEach {
                val userName = it[users.name]
                val cityName = it[cities.name]
                when (userName) {
                    "Andrey" -> assertEquals("St. Petersburg", cityName)
                    "Sergey" -> assertEquals("Munich", cityName)
                    else -> error("Unexpected user $userName")
                }
            }
        }
    }

    // join with foreign key
    @Test
    fun testJoin02() {
        withCitiesAndUsers { cities, users, userData ->
            val stPetersburgUser = (users innerJoin cities).slice(users.name, users.cityId, cities.name).select { cities.name.eq("St. Petersburg") or users.cityId.isNull() }.single()
            assertEquals("Andrey", stPetersburgUser[users.name])
            assertEquals("St. Petersburg", stPetersburgUser[cities.name])
        }
    }

    // triple join
    @Test
    fun testJoin03() {
        withCitiesAndUsers { cities, users, userData ->
            val r = (cities innerJoin users innerJoin userData).selectAll().orderBy(users.id).toList()
            assertEquals(2, r.size)
            assertEquals("Eugene", r[0][users.name])
            assertEquals("Comment for Eugene", r[0][userData.comment])
            assertEquals("Munich", r[0][cities.name])
            assertEquals("Sergey", r[1][users.name])
            assertEquals("Comment for Sergey", r[1][userData.comment])
            assertEquals("Munich", r[1][cities.name])
        }
    }

    // triple join
    @Test
    fun testJoin04() {
        val Numbers = object : Table() {
            val id = integer("id").primaryKey()
        }

        val Names = object : Table() {
            val name = varchar("name", 10).primaryKey()
        }

        val Map = object : Table() {
            val id_ref = integer("id_ref") references Numbers.id
            val name_ref = varchar("name_ref", 10) references Names.name
        }

        withTables(Numbers, Names, Map) {
            Numbers.insert { it[id] = 1 }
            Numbers.insert { it[id] = 2 }
            Names.insert { it[name] = "Foo" }
            Names.insert { it[name] = "Bar" }
            Map.insert {
                it[id_ref] = 2
                it[name_ref] = "Foo"
            }

            val r = (Numbers innerJoin Map innerJoin Names).selectAll().toList()
            assertEquals(1, r.size)
            assertEquals(2, r[0][Numbers.id])
            assertEquals("Foo", r[0][Names.name])
        }
    }

    // cross join
    @Test
    fun testJoin05() {
        withCitiesAndUsers { cities, users, _ ->
            val allUsersToStPetersburg = (users crossJoin cities).slice(users.name, users.cityId, cities.name).select { cities.name.eq("St. Petersburg") }.map {
                it[users.name] to it[cities.name]
            }
            val allUsers = setOf(
                "Andrey",
                "Sergey",
                "Eugene",
                "Alex",
                "Something"
            )
            assertTrue(allUsersToStPetersburg.all { it.second == "St. Petersburg" })
            assertEquals(allUsers, allUsersToStPetersburg.map { it.first }.toSet())
        }
    }

    @Test
    fun testMultipleReferenceJoin01() {
        val foo = object : IntIdTable("foo") {
            val baz = integer("baz").uniqueIndex()
        }
        val bar = object : IntIdTable("bar") {
            val foo = reference("foo", foo)
            val baz = integer("baz") references foo.baz
        }
        withTables(foo, bar) {
            val fooId = foo.insertAndGetId {
                it[baz] = 5
            }

            bar.insert {
                it[this.foo] = fooId
                it[baz] = 5
            }

            val result = foo.innerJoin(bar).selectAll()
            assertEquals(1, result.count())
        }
    }

    @Test
    fun testMultipleReferenceJoin02() {
        val foo = object : IntIdTable("foo") {
            val baz = integer("baz").uniqueIndex()
        }
        val bar = object : IntIdTable("bar") {
            val foo = reference("foo", foo)
            val foo2 = reference("foo2", foo)
            val baz = integer("baz") references foo.baz
        }
        withTables(foo, bar) {
            expectException<IllegalStateException> {
                val fooId = foo.insertAndGetId {
                    it[baz] = 5
                }

                bar.insert {
                    it[this.foo] = fooId
                    it[this.foo2] = fooId
                    it[baz] = 5
                }

                val result = foo.innerJoin(bar).selectAll()
                assertEquals(1, result.count())
            }
        }
    }

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
                .having { users.id.count() lessEq 42 }
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
            users.name.groupConcat().checkExcept(PostgreSQLDialect::class, SQLServerDialect::class, OracleDialect::class) {
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

            users.name.groupConcat(separator = " | ", distinct = true).checkExcept(PostgreSQLDialect::class, OracleDialect::class) {
                assertEquals(3, it.size)
                assertEquals("Andrey", it["St. Petersburg"])
                when (currentDialectTest) {
                    is MariaDBDialect -> assertEquals(true, it["Munich"] in listOf("Sergey | Eugene", "Eugene | Sergey"))
                    is MysqlDialect, is SQLServerDialect, is H2Dialect -> assertEquals("Eugene | Sergey", it["Munich"])
                    else -> assertEquals("Sergey | Eugene", it["Munich"])
                }
                assertNull(it["Prague"])
            }

            users.name.groupConcat(separator = " | ", orderBy = users.name to SortOrder.ASC).checkExcept(PostgreSQLDialect::class) {
                assertEquals(3, it.size)
                assertEquals("Andrey", it["St. Petersburg"])
                assertEquals("Eugene | Sergey", it["Munich"])
                assertNull(it["Prague"])
            }

            users.name.groupConcat(separator = " | ", orderBy = users.name to SortOrder.DESC).checkExcept(PostgreSQLDialect::class) {
                assertEquals(3, it.size)
                assertEquals("Andrey", it["St. Petersburg"])
                assertEquals("Sergey | Eugene", it["Munich"])
                assertNull(it["Prague"])
            }
        }
    }

    @Test
    fun orderBy01() {
        withCitiesAndUsers { cities, users, userData ->
            val r = users.selectAll().orderBy(users.id).toList()
            assertEquals(5, r.size)
            assertEquals("alex", r[0][users.id])
            assertEquals("andrey", r[1][users.id])
            assertEquals("eugene", r[2][users.id])
            assertEquals("sergey", r[3][users.id])
            assertEquals("smth", r[4][users.id])
        }
    }

    private fun isNullFirst() = when (currentDialectTest) {
        is OracleDialect, is PostgreSQLDialect -> true
        else -> false
    }

    @Test
    fun orderBy02() {
        withCitiesAndUsers { _, users, _ ->
            val r = users.selectAll().orderBy(users.cityId, SortOrder.DESC).orderBy(users.id).toList()
            assertEquals(5, r.size)
            val usersWithoutCities = listOf("alex", "smth")
            val otherUsers = listOf("eugene", "sergey", "andrey")
            val expected = if(isNullFirst()) usersWithoutCities + otherUsers
                else otherUsers + usersWithoutCities
            expected.forEachIndexed { index, e ->
                assertEquals(e, r[index][users.id])
            }
        }
    }

    @Test
    fun orderBy03() {
        withCitiesAndUsers { cities, users, userData ->
            val r = users.selectAll().orderBy(users.cityId to SortOrder.DESC, users.id to SortOrder.ASC).toList()
            assertEquals(5, r.size)
            val usersWithoutCities = listOf("alex", "smth")
            val otherUsers = listOf("eugene", "sergey", "andrey")
            val expected = if(isNullFirst()) usersWithoutCities + otherUsers
                else otherUsers + usersWithoutCities
            expected.forEachIndexed { index, e ->
                assertEquals(e, r[index][users.id])
            }
        }
    }

    @Test
    fun testOrderBy04() {
        withCitiesAndUsers { cities, users, userData ->
            val r = (cities innerJoin users).slice(cities.name, users.id.count()).selectAll().groupBy(cities.name).orderBy(cities.name).toList()
            assertEquals(2, r.size)
            assertEquals("Munich", r[0][cities.name])
            assertEquals(2, r[0][users.id.count()])
            assertEquals("St. Petersburg", r[1][cities.name])
            assertEquals(1, r[1][users.id.count()])
        }
    }

    @Test
    fun orderBy05() {
        withCitiesAndUsers { cities, users, userData ->
            val r = users.selectAll().orderBy(users.cityId to SortOrder.DESC, users.id to SortOrder.ASC).toList()
            assertEquals(5, r.size)
            val usersWithoutCities = listOf("alex", "smth")
            val otherUsers = listOf("eugene", "sergey", "andrey")
            val expected = if(isNullFirst()) usersWithoutCities + otherUsers
                else otherUsers + usersWithoutCities
            expected.forEachIndexed { index, e ->
                assertEquals(e, r[index][users.id])
            }
        }
    }

    @Test
    fun orderBy06() {
        withCitiesAndUsers { cities, users, userData ->
            val orderByExpression = users.id.substring(2, 1)
            val r = users.selectAll().orderBy(orderByExpression to SortOrder.ASC).toList()
            assertEquals(5, r.size)
            assertEquals("sergey", r[0][users.id])
            assertEquals("alex", r[1][users.id])
            assertEquals("smth", r[2][users.id])
            assertEquals("andrey", r[3][users.id])
            assertEquals("eugene", r[4][users.id])
        }
    }

    @Test
    fun testSizedIterable() {
        withCitiesAndUsers { cities, users, userData ->
            assertEquals(false, cities.selectAll().empty())
            assertEquals(true, cities.select { cities.name eq "Qwertt" }.empty())
            assertEquals(0, cities.select { cities.name eq "Qwertt" }.count())
            assertEquals(3, cities.selectAll().count())
        }
    }

    @Test
    fun testExists01() {
        withCitiesAndUsers { cities, users, userData ->
            val r = users.select { exists(userData.select((userData.user_id eq users.id) and (userData.comment like "%here%"))) }.toList()
            assertEquals(1, r.size)
            assertEquals("Something", r[0][users.name])
        }
    }

    @Test
    fun testExists02() {
        withCitiesAndUsers { cities, users, userData ->
            val r = users.select { exists(userData.select((userData.user_id eq users.id) and ((userData.comment like "%here%") or (userData.comment like "%Sergey")))) }
                .orderBy(users.id).toList()
            assertEquals(2, r.size)
            assertEquals("Sergey", r[0][users.name])
            assertEquals("Something", r[1][users.name])
        }
    }

    @Test
    fun testExists03() {
        withCitiesAndUsers { cities, users, userData ->
            val r = users.select {
                exists(userData.select((userData.user_id eq users.id) and (userData.comment like "%here%"))) or
                    exists(userData.select((userData.user_id eq users.id) and (userData.comment like "%Sergey")))
            }
                .orderBy(users.id).toList()
            assertEquals(2, r.size)
            assertEquals("Sergey", r[0][users.name])
            assertEquals("Something", r[1][users.name])
        }
    }

    @Test
    fun testInList01() {
        withCitiesAndUsers { cities, users, userData ->
            val r = users.select { users.id inList listOf("andrey", "alex") }.orderBy(users.name).toList()

            assertEquals(2, r.size)
            assertEquals("Alex", r[0][users.name])
            assertEquals("Andrey", r[1][users.name])
        }
    }

    @Test
    fun testInList02() {
        withCitiesAndUsers { cities, users, userData ->
            val cityIds = cities.selectAll().map { it[cities.id] }.take(2)
            val r = cities.select { cities.id inList cityIds }

            assertEquals(2, r.count())
        }
    }

    @Test
    fun testInSubQuery01() {
        withCitiesAndUsers { cities, users, userData ->
            val r = cities.select { cities.id inSubQuery cities.slice(cities.id).select { cities.id eq 2 } }
            assertEquals(1, r.count())
        }
    }

    @Test
    fun testInsertSelect01() {
        withCitiesAndUsers(exclude = listOf(TestDB.ORACLE)) { cities, users, userData ->
            val substring = users.name.substring(1, 2)
            cities.insert(users.slice(substring).selectAll().orderBy(users.id).limit(2))

            val r = cities.slice(cities.name).selectAll().orderBy(cities.id, SortOrder.DESC).limit(2).toList()
            assertEquals(2, r.size)
            assertEquals("An", r[0][cities.name])
            assertEquals("Al", r[1][cities.name])
        }
    }

    @Test
    fun testInsertSelect02() {
        withCitiesAndUsers { cities, users, userData ->
            val allUserData = userData.selectAll().count()
            userData.insert(userData.slice(userData.user_id, userData.comment, intParam(42)).selectAll())

            val r = userData.select { userData.value eq 42 }.orderBy(userData.user_id).toList()
            assertEquals(allUserData, r.size)
        }
    }

    @Test
    fun testInsertSelect03() {
        withCitiesAndUsers { cities, users, userData ->
            val userCount = users.selectAll().count()
            users.insert(users.slice(Random().castTo<String>(VarCharColumnType()).substring(1, 10), stringParam("Foo"), intParam(1)).selectAll())
            val r = users.select { users.name eq "Foo" }.toList()
            assertEquals(userCount, r.size)
        }
    }

    @Test
    fun testInsertSelect04() {
        withCitiesAndUsers { cities, users, userData ->
            val userCount = users.selectAll().count()
            users.insert(users.slice(stringParam("Foo"), Random().castTo<String>(VarCharColumnType()).substring(1, 10)).selectAll(), columns = listOf(users.name, users.id))
            val r = users.select { users.name eq "Foo" }.toList()
            assertEquals(userCount, r.size)
        }
    }

    @Test
    fun testInsertAndGetId01() {
        val idTable = object : IntIdTable("tmp") {
            val name = varchar("foo", 10).uniqueIndex()
        }

        withTables(idTable) {
            idTable.insertAndGetId {
                it[idTable.name] = "1"
            }

            assertEquals(1, idTable.selectAll().count())

            idTable.insertAndGetId {
                it[idTable.name] = "2"
            }

            assertEquals(2, idTable.selectAll().count())

            assertFailAndRollback("Unique constraint") {
                idTable.insertAndGetId {
                    it[idTable.name] = "2"
                }
            }
        }
    }

    private val insertIgnoreSupportedDB = TestDB.values().toList() -
            listOf(TestDB.SQLITE, TestDB.MYSQL, TestDB.H2_MYSQL, TestDB.POSTGRESQL)

    @Test
    fun testInsertIgnoreAndGetId01() {
        val idTable = object : IntIdTable("tmp") {
            val name = varchar("foo", 10).uniqueIndex()
        }


        withTables(insertIgnoreSupportedDB, idTable) {
            idTable.insertIgnoreAndGetId {
                it[idTable.name] = "1"
            }

            assertEquals(1, idTable.selectAll().count())

            idTable.insertIgnoreAndGetId {
                it[idTable.name] = "2"
            }

            assertEquals(2, idTable.selectAll().count())

            val idNull = idTable.insertIgnoreAndGetId {
                it[idTable.name] = "2"
            }

            assertEquals(null, idNull)
        }
    }

    @Test
    fun testInsertIgnoreAndGetIdWithPredefinedId() {
        val idTable = object : IntIdTable("tmp") {
            val name = varchar("foo", 10).uniqueIndex()
        }

        val insertIgnoreSupportedDB = TestDB.values().toList() -
                listOf(TestDB.SQLITE, TestDB.MYSQL, TestDB.H2_MYSQL, TestDB.POSTGRESQL)
        withTables(insertIgnoreSupportedDB, idTable) {
            val id = idTable.insertIgnore {
                it[idTable.id] = EntityID(1, idTable)
                it[idTable.name] = "1"
            } get idTable.id
            assertEquals(1, id?.value)
        }
    }


    @Test
    fun testBatchInsert01() {
        withCitiesAndUsers { cities, users, _ ->
            val cityNames = listOf("Paris", "Moscow", "Helsinki")
            val allCitiesID = cities.batchInsert(cityNames) { name ->
                this[cities.name] = name
            }
            assertEquals(cityNames.size, allCitiesID.size)

            val userNamesWithCityIds = allCitiesID.mapIndexed { index, id ->
                "UserFrom${cityNames[index]}" to id[cities.id] as Number
            }

            val generatedIds = users.batchInsert(userNamesWithCityIds) { (userName, cityId) ->
                this[users.id] = java.util.Random().nextInt().toString().take(6)
                this[users.name] = userName
                this[users.cityId] = cityId.toInt()
            }

            assertEquals(userNamesWithCityIds.size, generatedIds.size)
            assertEquals(userNamesWithCityIds.size, users.select { users.name inList userNamesWithCityIds.map { it.first } }.count())
        }
    }

    @Test
    fun testGeneratedKey01() {
        withTables(DMLTestsData.Cities) {
            val id = DMLTestsData.Cities.insert {
                it[DMLTestsData.Cities.name] = "FooCity"
            } get DMLTestsData.Cities.id
            assertEquals(DMLTestsData.Cities.selectAll().last()[DMLTestsData.Cities.id], id)
        }
    }

    object LongIdTable : Table() {
        val id = long("id").autoIncrement("long_id_seq").primaryKey()
        val name = text("name")
    }

    @Test
    fun testGeneratedKey02() {
        withTables(LongIdTable) {
            val id = LongIdTable.insert {
                it[LongIdTable.name] = "Foo"
            } get LongIdTable.id
            assertEquals(LongIdTable.selectAll().last()[LongIdTable.id], id)
        }
    }

    object IntIdTestTable : IntIdTable() {
        val name = text("name")
    }

    @Test
    fun testGeneratedKey03() {
        withTables(IntIdTestTable) {
            val id = IntIdTestTable.insertAndGetId {
                it[IntIdTestTable.name] = "Foo"
            }
            assertEquals(IntIdTestTable.selectAll().last()[IntIdTestTable.id], id)
        }
    }

    /*
    @Test fun testGeneratedKey04() {
        val CharIdTable = object : IdTable<String>("charId") {
            override val id = varchar("id", 50).primaryKey()
                    .clientDefault { UUID.randomUUID().toString() }
                    .entityId()
            val foo = integer("foo")
        }
        withTables(CharIdTable){
            val id = IntIdTestTable.insertAndGetId {
                it[CharIdTable.foo] = 5
            }
            assertNotNull(id?.value)
        }
    } */

/*
    Test fun testInsert05() {
        val stringThatNeedsEscaping = "multi\r\nline"
        val t = DMLTestsData.Misc
        withTables(t) {
            t.insert {
                it[n] = 42
                it[d] = today
                it[e] = DMLTestsData.E.ONE
                it[s] = stringThatNeedsEscaping
            }

            val row = t.selectAll().single()
            t.checkRow(row, 42, null, today, null, DMLTestsData.E.ONE, null, stringThatNeedsEscaping, null)
        }
    }
*/

    @Test
    fun testSelectDistinct() {
        val tbl = DMLTestsData.Cities
        withTables(tbl) {
            tbl.insert { it[tbl.name] = "test" }
            tbl.insert { it[tbl.name] = "test" }

            assertEquals(2, tbl.selectAll().count())
            assertEquals(2, tbl.selectAll().withDistinct().count())
            assertEquals(1, tbl.slice(tbl.name).selectAll().withDistinct().count())
            assertEquals("test", tbl.slice(tbl.name).selectAll().withDistinct().single()[tbl.name])
        }
    }

    @Test
    fun `selectBatched should respect 'where' expression and the provided batch size`() {
        val Cities = DMLTestsData.Cities
        withTables(Cities) {
            val names = List(100) { UUID.randomUUID().toString() }
            Cities.batchInsert(names) { name -> this[Cities.name] = name }

            val batches = Cities.selectBatched(batchSize = 25) { Cities.id less 51 }
                    .toList().map { it.toCityNameList() }

            val expectedNames = names.take(50)
            assertEqualLists(listOf(
                    expectedNames.take(25),
                    expectedNames.takeLast(25)
            ), batches)
        }
    }

    @Test
    fun `when batch size is greater than the amount of available items, selectAllBatched should return 1 batch`() {
        val Cities = DMLTestsData.Cities
        withTables(Cities) {
            val names = List(25) { UUID.randomUUID().toString() }
            Cities.batchInsert(names) { name -> this[Cities.name] = name }

            val batches = Cities.selectAllBatched(batchSize = 100).toList().map { it.toCityNameList() }

            assertEqualLists(listOf(names), batches)
        }
    }

    @Test
    fun `when there are no items, selectAllBatched should return an empty iterable`() {
        val Cities = DMLTestsData.Cities
        withTables(Cities) {
            val batches = Cities.selectAllBatched().toList()

            assertEqualLists(batches, emptyList())
        }
    }

    @Test
    fun `when there are no items of the given condition, should return an empty iterable`() {
        val Cities = DMLTestsData.Cities
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
        DMLTestsData.UserData.selectAllBatched()
    }

    @Test(expected = IllegalArgumentException::class)
    fun `when batch size is 0 or less, should throw an exception`() {
        DMLTestsData.Cities.selectAllBatched(batchSize = -1)
    }

    @Test
    fun testJoinWithAlias01() {
        withCitiesAndUsers { cities, users, userData ->
            val usersAlias = users.alias("u2")
            val resultRow = Join(users).join(usersAlias, JoinType.LEFT, usersAlias[users.id], stringLiteral("smth"))
                .select { users.id eq "alex" }.single()

            assert(resultRow[users.name] == "Alex")
            assert(resultRow[usersAlias[users.name]] == "Something")
        }
    }

    @Test
    fun testJoinWithJoin01() {
        withCitiesAndUsers { cities, users, userData ->
            val rows = (cities innerJoin (users innerJoin userData)).selectAll()
            assertEquals(2, rows.count())
        }
    }

    @Test
    fun testJoinWithAdditionalConstraint() {
        withCitiesAndUsers { cities, users, userData ->
            val usersAlias = users.alias("name")
            val join = cities.join(usersAlias, JoinType.INNER, cities.id, usersAlias[users.cityId]) {
                cities.id greater 1 and (cities.name.neq(usersAlias[users.name]))
            }

            assertEquals(2, join.selectAll().count())
        }
    }

    // GitHub issue #98: Parameter index out of range when using Table.replace
    @Test
    fun testReplace01() {
        val NewAuth = object : Table() {
            val username = varchar("username", 16).primaryKey()
            val session = binary("session", 64)
            val timestamp = long("timestamp").default(0)
            val serverID = varchar("serverID", 64).default("")
        }
        // Only MySQL supp
        withTables(TestDB.values().toList() - listOf(TestDB.MYSQL, TestDB.POSTGRESQL), NewAuth) {
            NewAuth.replace {
                it[username] = "username"
                it[session] = "session".toByteArray()
            }
        }
    }

    private fun assertQueryResultValid(query: Query) {
        val users = DMLTestsData.Users
        val cities = DMLTestsData.Cities
        query.forEach { row ->
            val userName = row[users.name]
            val cityName = row[cities.name]
            when (userName) {
                "Andrey" -> assertEquals("St. Petersburg", cityName)
                "Sergey" -> assertEquals("Munich", cityName)
                else -> error("Unexpected user $userName")
            }
        }
    }

    private val predicate = Op.build {
        val nameCheck = (DMLTestsData.Users.id eq "andrey") or (DMLTestsData.Users.name eq "Sergey")
        val cityCheck = DMLTestsData.Users.cityId eq DMLTestsData.Cities.id
        nameCheck and cityCheck
    }

    @Test
    fun testAdjustQuerySlice() {
        withCitiesAndUsers { cities, users, _ ->
            val queryAdjusted = (users innerJoin cities)
                .slice(users.name)
                .select(predicate)

            fun Query.sliceIt(): FieldSet = this.set.source.slice(users.name, cities.name)
            val oldSlice = queryAdjusted.set.fields
            val expectedSlice = queryAdjusted.sliceIt().fields
            queryAdjusted.adjustSlice { slice(users.name, cities.name) }
            val actualSlice = queryAdjusted.set.fields
            fun containsInAnyOrder(list: List<*>) = containsInAnyOrder(*list.toTypedArray())

            assertThat(oldSlice, not(containsInAnyOrder(actualSlice)))
            assertThat(actualSlice, containsInAnyOrder(expectedSlice))
            assertQueryResultValid(queryAdjusted)
        }
    }

    @Test
    fun testAdjustQueryColumnSet() {
        withCitiesAndUsers { cities, users, _ ->
            val queryAdjusted = users
                .slice(users.name, cities.name)
                .select(predicate)
            val oldColumnSet = queryAdjusted.set.source
            val expectedColumnSet = users innerJoin cities
            queryAdjusted.adjustColumnSet { innerJoin(cities) }
            val actualColumnSet = queryAdjusted.set.source
            fun ColumnSet.repr(): String = QueryBuilder(false).also { this.describe(TransactionManager.current(), it ) }.toString()

            assertNotEquals(oldColumnSet.repr(), actualColumnSet.repr())
            assertEquals(expectedColumnSet.repr(), actualColumnSet.repr())
            assertQueryResultValid(queryAdjusted)
        }
    }

    @Test
    fun testAdjustQueryWhere() {
        withCitiesAndUsers { cities, users, _ ->
            val queryAdjusted = (users innerJoin cities)
                .slice(users.name, cities.name)
                .selectAll()
            queryAdjusted.adjustWhere {
                assertNull(this)
                predicate
            }
            val actualWhere = queryAdjusted.where
            fun Op<Boolean>.repr(): String {
                val builder = QueryBuilder(false)
                builder.append(this)
                return builder.toString()
            }

            assertEquals(predicate.repr(), actualWhere!!.repr())
            assertQueryResultValid(queryAdjusted)
        }
    }

    @Test
    fun testQueryAndWhere() {
        withCitiesAndUsers { cities, users, _ ->
            val queryAdjusted = (users innerJoin cities)
                    .slice(users.name, cities.name)
                    .select{ predicate }

            queryAdjusted.andWhere {
                predicate
            }
            val actualWhere = queryAdjusted.where
            fun Op<Boolean>.repr(): String {
                val builder = QueryBuilder(false)
                builder.append(this)
                return builder.toString()
            }

            assertEquals((predicate.and(predicate)).repr(), actualWhere!!.repr())
            assertQueryResultValid(queryAdjusted)
        }
    }

    @Test
    fun `test that count() works with Query that contains distinct and columns with same name from different tables`() {
        withCitiesAndUsers { cities, users, _ ->
            assertEquals(3, cities.innerJoin(users).selectAll().withDistinct().count())
        }
    }

    @Test
    fun `test that count() works with Query that contains distinct and columns with same name from different tables and already defined alias`() {
        withCitiesAndUsers { cities, users, _ ->
            assertEquals(3, cities.innerJoin(users).slice(users.id.alias("usersId"), cities.id).selectAll().withDistinct().count())
        }
    }

    @Test
    fun `test that count() returns right value for Query with group by`() {
        withCitiesAndUsers { _, user, userData ->
            val uniqueUsersInData = userData.slice(userData.user_id).selectAll().withDistinct().count()
            val sameQueryWithGrouping = userData.slice(userData.value.max()).selectAll().groupBy(userData.user_id).count()
            assertEquals(uniqueUsersInData, sameQueryWithGrouping)
        }

        withTables(OrgMemberships, Orgs) {
            val org1 = Org.new {
                name = "FOo"
            }
            val membership = OrgMembership.new {
                org = org1
            }

            assertEquals(1, OrgMemberships.selectAll().count())
        }
    }

    @Test
    fun testCompoundOp() {
        withCitiesAndUsers { cities, users, _ ->
            val allUsers = setOf(
                    "Andrey",
                    "Sergey",
                    "Eugene",
                    "Alex",
                    "Something"
            )
            val orOp = allUsers.map { Op.build { users.name eq it } }.compoundOr()
            val userNamesOr = users.select(orOp).map { it[users.name] }.toSet()
            assertEquals(allUsers, userNamesOr)

            val andOp = allUsers.map { Op.build { users.name eq it } }.compoundAnd()
            assertEquals(0, users.select(andOp).count())
        }
    }

    @Test
    fun testOrderByExpressions() {
        withCitiesAndUsers { cities, users, userData ->
            val expression = wrapAsExpression<Int>(users
                    .slice(users.id.count())
                    .select {
                        cities.id eq users.cityId
                    })

            val result = cities
                    .selectAll()
                    .orderBy(expression, SortOrder.DESC)
                    .toList()

            // Munich - 2 users
            // St. Petersburg - 1 user
            // Prague - 0 users
            println(result)
        }
    }

    @Test fun testInsertWithPredefinedId() {
        val stringTable = object : IdTable<String>("stringTable") {
            override val id = varchar("id", 15).entityId()
            val name = varchar("name", 10)
        }
        withTables(stringTable) {
            val entityID = EntityID("id1", stringTable)
            val id1 = stringTable.insertAndGetId {
                it[id] = entityID
                it[name] = "foo"
            }

            stringTable.insertAndGetId {
                it[id] = EntityID("testId", stringTable)
                it[name] = "bar"
            }

            assertEquals(id1, entityID)
            val row1 = stringTable.select { stringTable.id eq entityID }.singleOrNull()
            assertEquals(row1?.get(stringTable.id), entityID)

            val row2 = stringTable.select { stringTable.id like "id%" }.singleOrNull()
            assertEquals(row2?.get(stringTable.id), entityID)
        }
    }

    @Test fun testInsertWithExpression() {

        val tbl = object : IntIdTable("testInsert") {
            val nullableInt = integer("nullableIntCol").nullable()
            val string = varchar("stringCol", 20)
        }

        fun expression(value: String) = stringLiteral(value).trim().substring(2, 4)

        fun verify(value: String) {
            val row = tbl.select{ tbl.string eq value }.single()
            assertEquals(row[tbl.string], value)
        }

        withTables(tbl) {
            addLogger(StdOutSqlLogger)
            tbl.insert {
                it[string] = expression(" _exp1_ ")
            }

            verify("exp1")

            tbl.insert {
                it[string] = expression(" _exp2_ ")
                it[nullableInt] = 5
            }

            verify("exp2")

            tbl.insert {
                it[string] = expression(" _exp3_ ")
                it[nullableInt] = null
            }

            verify("exp3")
        }
    }

    @Test fun testTRUEandFALSEOps() {
        withCitiesAndUsers { cities, _, _ ->
            val allSities = cities.selectAll().toCityNameList()
            assertEquals(0, cities.select { Op.FALSE }.count())
            assertEquals(allSities.size, cities.select { Op.TRUE }.count())
        }
    }

    @Test fun testNoWarningsOnLeftJoinRegression(){
        val MainTable = object : Table("maintable"){
            val id = integer("idCol")
        }
        val JoinTable = object : Table("jointable"){
            val id = integer("idCol")
            val data = integer("dataCol").default(42)
        }

        withTables(MainTable, JoinTable){
            MainTable.insert { it[id] = 2 }

            MainTable.join(JoinTable, JoinType.LEFT, JoinTable.id, MainTable.id)
                    .slice(JoinTable.data)
                    .selectAll()
                    .single()
                    .getOrNull(JoinTable.data)

            // Assert no logging took place. No idea how to.
        }
    }

    private object OrderedDataTable : IntIdTable()
    {
        val name = text("name")
        val order = integer("order")
    }

    class OrderedData(id : EntityID<Int>) : IntEntity(id)
    {
        companion object : IntEntityClass<OrderedData>(OrderedDataTable)

        var name by OrderedDataTable.name
        var order by OrderedDataTable.order
    }

    // https://github.com/JetBrains/Exposed/issues/192
    @Test fun testInsertWithColumnNamedWithKeyword() {
        withTables(OrderedDataTable) {

            val foo = OrderedData.new {
                name = "foo"
                order = 20
            }
            val bar = OrderedData.new {
                name = "bar"
                order = 10
            }

            assertEqualLists(listOf(bar, foo), OrderedData.all().orderBy(OrderedDataTable.order to SortOrder.ASC).toList())
        }
    }

    // https://github.com/JetBrains/Exposed/issues/581
    @Test fun sameColumnUsedInSliceMultipleTimes() {
        withCitiesAndUsers { city, _, _ ->
            val row = city.slice(city.name, city.name, city.id).select { city.name eq "Munich" }.toList().single()
            assertEquals(2, row[city.id])
            assertEquals("Munich", row[city.name])
        }
    }

    // https://github.com/JetBrains/Exposed/issues/693
    @Test fun compareToNullableColumn() {
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

    @Test fun testInsertEmojis() {
        val table = object : Table("tmp") {
            val emoji = varchar("emoji", 16)
        }
        val emojis = "\uD83D\uDC68\uD83C\uDFFF\u200D\uD83D\uDC69\uD83C\uDFFF\u200D\uD83D\uDC67\uD83C\uDFFF\u200D\uD83D\uDC66\uD83C\uDFFF"

        withTables(listOf(TestDB.H2, TestDB.H2_MYSQL, TestDB.SQLSERVER), table) {
            val isOldMySQL = currentDialectTest is MysqlDialect && db.isVersionCovers(BigDecimal("5.5"))
            if (isOldMySQL) {
                exec("ALTER TABLE ${table.nameInDatabaseCase()} DEFAULT CHARSET utf8mb4, MODIFY emoji VARCHAR(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;")
            }
            table.insert {
                it[table.emoji] = emojis
            }

            assertEquals(1, table.selectAll().count())
        }
    }

    @Test fun testInsertEmojisWithInvalidLength() {
        val table = object : Table("tmp") {
            val emoji = varchar("emoji", 10)
        }
        val emojis = "\uD83D\uDC68\uD83C\uDFFF\u200D\uD83D\uDC69\uD83C\uDFFF\u200D\uD83D\uDC67\uD83C\uDFFF\u200D\uD83D\uDC66\uD83C\uDFFF"

        withTables(listOf(TestDB.SQLITE, TestDB.H2, TestDB.H2_MYSQL, TestDB.POSTGRESQL), table) {
            expectException<IllegalStateException> {
                table.insert {
                    it[table.emoji] = emojis
                }
            }
        }
    }

    private fun Iterable<ResultRow>.toCityNameList(): List<String> = map { it[DMLTestsData.Cities.name] }
}

object OrgMemberships : IntIdTable() {
    val orgId = reference("org", Orgs.uid)
}

class OrgMembership(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<OrgMembership>(OrgMemberships)

    val orgId by OrgMemberships.orgId
    var org by Org referencedOn OrgMemberships.orgId
}

object Orgs : IntIdTable() {
    val uid = varchar("uid", 36).uniqueIndex().clientDefault { UUID.randomUUID().toString() }
    val name = varchar("name", 256)
}

class Org(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<Org>(Orgs)

    var uid by Orgs.uid
    var name by Orgs.name
}
