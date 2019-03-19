package org.jetbrains.exposed.sql.tests.shared

import org.hamcrest.Matchers.containsInAnyOrder
import org.hamcrest.Matchers.not
import org.jetbrains.exposed.dao.*
import org.jetbrains.exposed.exceptions.UnsupportedByDialectException
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.Function
import org.jetbrains.exposed.sql.Random
import org.jetbrains.exposed.sql.statements.BatchDataInconsistentException
import org.jetbrains.exposed.sql.statements.BatchInsertStatement
import org.jetbrains.exposed.sql.statements.InsertStatement
import org.jetbrains.exposed.sql.tests.DatabaseTestsBase
import org.jetbrains.exposed.sql.tests.TestDB
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.vendors.*
import org.joda.time.DateTime
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

    enum class E {
        ONE,
        TWO,
        THREE
    }

    object Misc : Table() {
        val n = integer("n")
        val nn = integer("nn").nullable()

        val d = date("d")
        val dn = date("dn").nullable()

        val t = datetime("t")
        val tn = datetime("tn").nullable()

        val e = enumeration("e", E::class)
        val en = enumeration("en", E::class).nullable()

        val es = enumerationByName("es", 5, E::class)
        val esn = enumerationByName("esn", 5, E::class).nullable()

        val s = varchar("s", 100)
        val sn = varchar("sn", 100).nullable()

        val dc = decimal("dc", 12, 2)
        val dcn = decimal("dcn", 12, 2).nullable()

        val fcn = float("fcn").nullable()
        val dblcn = double("dblcn").nullable()

        val char = char("char").nullable()
    }
}

class DMLTests : DatabaseTestsBase() {
    fun withCitiesAndUsers(exclude: List<TestDB> = emptyList(), statement: Transaction.(cities: DMLTestsData.Cities, users: DMLTestsData.Users, userData: DMLTestsData.UserData) -> Unit) {
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
                assertEquals("eugene", it.single()[userData.user_id])
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
                assertTrue(userCountAlias is Int)
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
    fun `test_github_issue_379_count_alias_ClassCastException`() {
        val Stables = object : UUIDTable("Stables") {
            val name = varchar("name", 256).uniqueIndex()
        }

        val Facilities = object : UUIDTable("Facilities") {
            val stableId = reference("stable_id", Stables)
            val name = varchar("name", 256)
        }

        withTables(Facilities, Stables) {
            val stable1Id = Stables.insertAndGetId {
                it[Stables.name] = "Stables1"
            }
            Stables.insertAndGetId {
                it[Stables.name] = "Stables2"
            }
            Facilities.insertAndGetId {
                it[Facilities.stableId] = stable1Id
                it[Facilities.name] = "Facility1"
            }
            val fcAlias = Facilities.name.count().alias("fc")
            val fAlias = Facilities.slice(Facilities.stableId, fcAlias).selectAll().groupBy(Facilities.stableId).alias("f")
            val sliceColumns = Stables.columns + fAlias[fcAlias]
            val stats = Stables.join(fAlias, JoinType.LEFT, Stables.id, fAlias[Facilities.stableId])
                    .slice(sliceColumns)
                    .selectAll()
                    .groupBy(*sliceColumns.toTypedArray()).map {
                        it[Stables.name] to it[fAlias[fcAlias]]
                    }.toMap()
            assertEquals(2, stats.size)
            assertEquals(1, stats["Stables1"])
            assertNull(stats["Stables2"])
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
                val sorted = if (currentDialect is MysqlDialect || currentDialect is SQLServerDialect) "Eugene, Sergey" else "Sergey, Eugene"
                assertEquals(sorted, it["Munich"])
                assertNull(it["Prague"])
            }

            users.name.groupConcat(separator = " | ", distinct = true).checkExcept(PostgreSQLDialect::class, OracleDialect::class) {
                assertEquals(3, it.size)
                assertEquals("Andrey", it["St. Petersburg"])
                val sorted = if (currentDialect is MysqlDialect || currentDialect is SQLServerDialect) "Eugene | Sergey" else "Sergey | Eugene"
                assertEquals(sorted, it["Munich"])
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

    private fun isNullFirst() = when (currentDialect) {
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
            val r = users.selectAll().orderBy(users.cityId to false, users.id to true).toList()
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

    private fun DMLTestsData.Misc.checkRow(row: ResultRow, n: Int, nn: Int?, d: DateTime, dn: DateTime?,
                                           t: DateTime, tn: DateTime?, e: DMLTestsData.E, en: DMLTestsData.E?,
                                           es: DMLTestsData.E, esn: DMLTestsData.E?, s: String, sn: String?,
                                           dc: BigDecimal, dcn: BigDecimal?, fcn: Float?, dblcn: Double?) {
        assertEquals(row[this.n], n)
        assertEquals(row[this.nn], nn)
        assertEqualDateTime(row[this.d], d)
        assertEqualDateTime(row[this.dn], dn)
        assertEqualDateTime(row[this.t], t)
        assertEqualDateTime(row[this.tn], tn)
        assertEquals(row[this.e], e)
        assertEquals(row[this.en], en)
        assertEquals(row[this.es], es)
        assertEquals(row[this.esn], esn)
        assertEquals(row[this.s], s)
        assertEquals(row[this.sn], sn)
        assertEquals(row[this.dc], dc)
        assertEquals(row[this.dcn], dcn)
        assertEquals(row[this.fcn], fcn)
        assertEquals(row[this.dblcn], dblcn)
    }
    private fun DMLTestsData.Misc.checkInsert(row: InsertStatement<Number>, n: Int, nn: Int?, d: DateTime, dn: DateTime?,
                                           t: DateTime, tn: DateTime?, e: DMLTestsData.E, en: DMLTestsData.E?,
                                           es: DMLTestsData.E, esn: DMLTestsData.E?, s: String, sn: String?,
                                           dc: BigDecimal, dcn: BigDecimal?, fcn: Float?, dblcn: Double?) {
        assertEquals(row[this.n], n)
        assertEquals(row[this.nn], nn)
        assertEqualDateTime(row[this.d], d)
        assertEqualDateTime(row[this.dn], dn)
        assertEqualDateTime(row[this.t], t)
        assertEqualDateTime(row[this.tn], tn)
        assertEquals(row[this.e], e)
        assertEquals(row[this.en], en)
        assertEquals(row[this.es], es)
        assertEquals(row[this.esn], esn)
        assertEquals(row[this.s], s)
        assertEquals(row[this.sn], sn)
        assertEquals(row[this.dc], dc)
        assertEquals(row[this.dcn], dcn)
        assertEquals(row[this.fcn], fcn)
        assertEquals(row[this.dblcn], dblcn)
    }

    @Test
    fun testInsert01() {
        val tbl = DMLTestsData.Misc
        val date = today
        val time = DateTime.now()

        withTables(tbl) {
            tbl.insert {
                it[n] = 42
                it[d] = date
                it[t] = time
                it[e] = DMLTestsData.E.ONE
                it[es] = DMLTestsData.E.ONE
                it[s] = "test"
                it[dc] = BigDecimal("239.42")
                it[char] = '('
            }

            val row = tbl.selectAll().single()
            tbl.checkRow(row, 42, null, date, null, time, null, DMLTestsData.E.ONE, null, DMLTestsData.E.ONE,
                    null, "test", null, BigDecimal("239.42"), null, null, null)
            assertEquals('(', row[tbl.char])
        }
    }

    @Test
    fun testInsert02() {
        val tbl = DMLTestsData.Misc
        val date = today
        val time = DateTime.now()

        withTables(tbl) {
            tbl.insert {
                it[n] = 42
                it[nn] = null
                it[d] = date
                it[dn] = null
                it[t] = time
                it[tn] = null
                it[e] = DMLTestsData.E.ONE
                it[en] = null
                it[es] = DMLTestsData.E.ONE
                it[esn] = null
                it[s] = "test"
                it[sn] = null
                it[dc] = BigDecimal("239.42")
                it[dcn] = null
                it[fcn] = null
            }

            val row = tbl.selectAll().single()
            tbl.checkRow(row, 42, null, date, null, time, null, DMLTestsData.E.ONE, null, DMLTestsData.E.ONE,
                    null, "test", null, BigDecimal("239.42"), null, null, null)
        }
    }

    @Test
    fun testInsert03() {
        val tbl = DMLTestsData.Misc
        val date = today
        val time = DateTime.now()

        withTables(tbl) {
            tbl.insert {
                it[n] = 42
                it[nn] = 42
                it[d] = date
                it[dn] = date
                it[t] = time
                it[tn] = time
                it[e] = DMLTestsData.E.ONE
                it[en] = DMLTestsData.E.ONE
                it[es] = DMLTestsData.E.ONE
                it[esn] = DMLTestsData.E.ONE
                it[s] = "test"
                it[sn] = "test"
                it[dc] = BigDecimal("239.42")
                it[dcn] = BigDecimal("239.42")
                it[fcn] = 239.42f
                it[dblcn] = 567.89
            }

            val row = tbl.selectAll().single()
            tbl.checkRow(row, 42, 42, date, date, time, time, DMLTestsData.E.ONE, DMLTestsData.E.ONE, DMLTestsData.E.ONE, DMLTestsData.E.ONE,
                    "test", "test", BigDecimal("239.42"), BigDecimal("239.42"), 239.42f, 567.89)
        }
    }

    @Test
    fun testInsert04() {
        val stringThatNeedsEscaping = "A'braham Barakhyahu"
        val tbl = DMLTestsData.Misc
        val date = today
        val time = DateTime.now()
        withTables(tbl) {
            tbl.insert {
                it[n] = 42
                it[d] = date
                it[t] = time
                it[e] = DMLTestsData.E.ONE
                it[es] = DMLTestsData.E.ONE
                it[s] = stringThatNeedsEscaping
                it[dc] = BigDecimal("239.42")
            }

            val row = tbl.selectAll().single()
            tbl.checkRow(row, 42, null, date, null, time, null, DMLTestsData.E.ONE, null, DMLTestsData.E.ONE, null, stringThatNeedsEscaping, null,
                    BigDecimal("239.42"), null, null, null)
        }
    }

    @Test
    fun testInsertGet01() {
        val tbl = DMLTestsData.Misc
        val date = today
        val time = DateTime.now()

        withTables(tbl) {
            val row = tbl.insert {
                it[n] = 42
                it[d] = date
                it[t] = time
                it[e] = DMLTestsData.E.ONE
                it[es] = DMLTestsData.E.ONE
                it[s] = "test"
                it[dc] = BigDecimal("239.42")
                it[char] = '('
            }

            tbl.checkInsert(row, 42, null, date, null, time, null, DMLTestsData.E.ONE, null, DMLTestsData.E.ONE,
                    null, "test", null, BigDecimal("239.42"), null, null, null)
            assertEquals('(', row[tbl.char])
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

    @Test
    fun testInsertIgnoreAndGetId01() {
        val idTable = object : IntIdTable("tmp") {
            val name = varchar("foo", 10).uniqueIndex()
        }

        val insertIgnoreSupportedDB = TestDB.values().toList() -
                listOf(TestDB.SQLITE, TestDB.MYSQL, TestDB.H2_MYSQL, TestDB.POSTGRESQL)
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

    private val initBatch = listOf<(BatchInsertStatement) -> Unit>({
        it[EntityTests.TableWithDBDefault.field] = "1"
    }, {
        it[EntityTests.TableWithDBDefault.field] = "2"
        it[EntityTests.TableWithDBDefault.t1] = DateTime.now()
    })

    @Test
    fun testRawBatchInsertFails01() {
        withTables(EntityTests.TableWithDBDefault) {
            expectException<BatchDataInconsistentException> {
                BatchInsertStatement(EntityTests.TableWithDBDefault).run {
                    initBatch.forEach {
                        addBatch()
                        it(this)
                    }
                }
            }
        }
    }

    @Test
    fun testRawBatchInsertFails02() {
        withTables(EntityTests.TableWithDBDefault) {
            EntityTests.TableWithDBDefault.batchInsert(initBatch) { foo ->
                foo(this)
            }
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
    fun testSelect01() {
        val tbl = DMLTestsData.Misc
        withTables(tbl) {
            val date = today
            val time = DateTime.now()
            val sTest = "test"
            val dec = BigDecimal("239.42")
            tbl.insert {
                it[n] = 42
                it[d] = date
                it[t] = time
                it[e] = DMLTestsData.E.ONE
                it[es] = DMLTestsData.E.ONE
                it[s] = sTest
                it[dc] = dec
            }

            tbl.checkRow(tbl.select { tbl.n.eq(42) }.single(), 42, null, date, null, time, null, DMLTestsData.E.ONE, null, DMLTestsData.E.ONE, null, sTest, null, dec, null, null, null)
            tbl.checkRow(tbl.select { tbl.nn.isNull() }.single(), 42, null, date, null, time, null, DMLTestsData.E.ONE, null, DMLTestsData.E.ONE, null, sTest, null, dec, null, null, null)
            tbl.checkRow(tbl.select { tbl.nn.eq(null as Int?) }.single(), 42, null, date, null, time, null, DMLTestsData.E.ONE, null, DMLTestsData.E.ONE, null, sTest, null, dec, null, null, null)

            tbl.checkRow(tbl.select { tbl.d.eq(date) }.single(), 42, null, date, null, time, null, DMLTestsData.E.ONE, null, DMLTestsData.E.ONE, null, sTest, null, dec, null, null, null)
            tbl.checkRow(tbl.select { tbl.dn.isNull() }.single(), 42, null, date, null, time, null, DMLTestsData.E.ONE, null, DMLTestsData.E.ONE, null, sTest, null, dec, null, null, null)
            tbl.checkRow(tbl.select { tbl.dn.eq(null as DateTime?) }.single(), 42, null, date, null, time, null, DMLTestsData.E.ONE, null, DMLTestsData.E.ONE, null, sTest, null, dec, null, null, null)

            tbl.checkRow(tbl.select { tbl.t.eq(time) }.single(), 42, null, date, null, time, null, DMLTestsData.E.ONE, null, DMLTestsData.E.ONE, null, sTest, null, dec, null, null, null)
            tbl.checkRow(tbl.select { tbl.tn.isNull() }.single(), 42, null, date, null, time, null, DMLTestsData.E.ONE, null, DMLTestsData.E.ONE, null, sTest, null, dec, null, null, null)
            tbl.checkRow(tbl.select { tbl.tn.eq(null as DateTime?) }.single(), 42, null, date, null, time, null, DMLTestsData.E.ONE, null, DMLTestsData.E.ONE, null, sTest, null, dec, null, null, null)

            tbl.checkRow(tbl.select { tbl.e.eq(DMLTestsData.E.ONE) }.single(), 42, null, date, null, time, null, DMLTestsData.E.ONE, null, DMLTestsData.E.ONE, null, sTest, null, dec, null, null, null)
            tbl.checkRow(tbl.select { tbl.en.isNull() }.single(), 42, null, date, null, time, null, DMLTestsData.E.ONE, null, DMLTestsData.E.ONE, null, sTest, null, dec, null, null, null)
            tbl.checkRow(tbl.select { tbl.en.eq(null as DMLTestsData.E?) }.single(), 42, null, date, null, time, null, DMLTestsData.E.ONE, null, DMLTestsData.E.ONE, null, sTest, null, dec, null, null, null)

            tbl.checkRow(tbl.select { tbl.s.eq(sTest) }.single(), 42, null, date, null, time, null, DMLTestsData.E.ONE, null, DMLTestsData.E.ONE, null, sTest, null, dec, null, null, null)
            tbl.checkRow(tbl.select { tbl.sn.isNull() }.single(), 42, null, date, null, time, null, DMLTestsData.E.ONE, null, DMLTestsData.E.ONE, null, sTest, null, dec, null, null, null)
            tbl.checkRow(tbl.select { tbl.sn.eq(null as String?) }.single(), 42, null, date, null, time, null, DMLTestsData.E.ONE, null, DMLTestsData.E.ONE, null, sTest, null, dec, null, null, null)
        }
    }

    @Test
    fun testSelect02() {
        val tbl = DMLTestsData.Misc
        withTables(tbl) {
            val date = today
            val time = DateTime.now()
            val sTest = "test"
            val eOne = DMLTestsData.E.ONE
            val dec = BigDecimal("239.42")
            tbl.insert {
                it[n] = 42
                it[nn] = 42
                it[d] = date
                it[dn] = date
                it[t] = time
                it[tn] = time
                it[e] = eOne
                it[en] = eOne
                it[es] = eOne
                it[esn] = eOne
                it[s] = sTest
                it[sn] = sTest
                it[dc] = dec
                it[dcn] = dec
                it[fcn] = 239.42f
                it[dblcn] = 567.89
            }

            tbl.checkRow(tbl.select { tbl.nn.eq(42) }.single(), 42, 42, date, date, time, time, eOne, eOne, eOne, eOne, sTest, sTest, dec, dec, 239.42f, 567.89)
            tbl.checkRow(tbl.select { tbl.nn.neq<Int?>(null) }.single(), 42, 42, date, date, time, time, eOne, eOne, eOne, eOne, sTest, sTest, dec, dec, 239.42f, 567.89)

            tbl.checkRow(tbl.select { tbl.dn.eq(date) }.single(), 42, 42, date, date, time, time, eOne, eOne, eOne, eOne, sTest, sTest, dec, dec, 239.42f, 567.89)
            tbl.checkRow(tbl.select { tbl.dn.isNotNull() }.single(), 42, 42, date, date, time, time, eOne, eOne, eOne, eOne, sTest, sTest, dec, dec, 239.42f, 567.89)

            tbl.checkRow(tbl.select { tbl.t.eq(time) }.single(), 42, 42, date, date, time, time, eOne, eOne, eOne, eOne, sTest, sTest, dec, dec, 239.42f, 567.89)
            tbl.checkRow(tbl.select { tbl.tn.isNotNull() }.single(), 42, 42, date, date, time, time, eOne, eOne, eOne, eOne, sTest, sTest, dec, dec, 239.42f, 567.89)

            tbl.checkRow(tbl.select { tbl.en.eq(eOne) }.single(), 42, 42, date, date, time, time, eOne, eOne, eOne, eOne, sTest, sTest, dec, dec, 239.42f, 567.89)
            tbl.checkRow(tbl.select { tbl.en.isNotNull() }.single(), 42, 42, date, date, time, time, eOne, eOne, eOne, eOne, sTest, sTest, dec, dec, 239.42f, 567.89)

            tbl.checkRow(tbl.select { tbl.sn.eq(sTest) }.single(), 42, 42, date, date, time, time, eOne, eOne, eOne, eOne, sTest, sTest, dec, dec, 239.42f, 567.89)
            tbl.checkRow(tbl.select { tbl.sn.isNotNull() }.single(), 42, 42, date, date, time, time, eOne, eOne, eOne, eOne, sTest, sTest, dec, dec, 239.42f, 567.89)
        }
    }

    @Test
    fun testUpdate02() {
        val tbl = DMLTestsData.Misc
        withTables(tbl) {
            val date = today
            val time = DateTime.now()
            val eOne = DMLTestsData.E.ONE
            val sTest = "test"
            val dec = BigDecimal("239.42")
            tbl.insert {
                it[n] = 42
                it[nn] = 42
                it[d] = date
                it[dn] = date
                it[t] = time
                it[tn] = time
                it[e] = eOne
                it[en] = eOne
                it[es] = eOne
                it[esn] = eOne
                it[s] = sTest
                it[sn] = sTest
                it[dc] = dec
                it[dcn] = dec
                it[fcn] = 239.42f
            }

            tbl.update({ tbl.n.eq(42) }) {
                it[nn] = null
                it[dn] = null
                it[tn] = null
                it[en] = null
                it[esn] = null
                it[sn] = null
                it[dcn] = null
                it[fcn] = null
            }

            val row = tbl.selectAll().single()
            tbl.checkRow(row, 42, null, date, null, time, null, eOne, null, eOne, null, sTest, null, dec, null, null, null)
        }
    }

    @Test
    fun testUpdate03() {
        val tbl = DMLTestsData.Misc
        val date = today
        val time = DateTime.now()
        val eOne = DMLTestsData.E.ONE
        val dec = BigDecimal("239.42")
        withTables(excludeSettings = listOf(TestDB.MYSQL, TestDB.MARIADB), tables = *arrayOf(tbl)) {
            tbl.insert {
                it[n] = 101
                it[s] = "123456789"
                it[sn] = "123456789"
                it[d] = date
                it[t] = time
                it[e] = eOne
                it[es] = eOne
                it[dc] = dec
            }

            tbl.update({ tbl.n.eq(101) }) {
                it.update(s, tbl.s.substring(2, 255))
                it.update(sn, tbl.s.substring(3, 255))
            }

            val row = tbl.select { tbl.n eq 101 }.single()
            tbl.checkRow(row, 101, null, date, null, time, null, eOne, null, eOne, null, "23456789", "3456789", dec, null, null, null)
        }
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
    fun testStringFunctions() {
        withCitiesAndUsers { cities, users, userData ->

            val lcase = DMLTestsData.Cities.name.lowerCase()
            assert(cities.slice(lcase).selectAll().any { it[lcase] == "prague" })

            val ucase = DMLTestsData.Cities.name.upperCase()
            assert(cities.slice(ucase).selectAll().any { it[ucase] == "PRAGUE" })
        }
    }

    @Test
    fun testJoinSubQuery01() {
        withCitiesAndUsers { cities, users, userData ->
            val expAlias = users.name.max().alias("m")
            val usersAlias = users.slice(users.cityId, expAlias).selectAll().groupBy(users.cityId).alias("u2")
            val resultRows = Join(users).join(usersAlias, JoinType.INNER, usersAlias[expAlias], users.name).selectAll().toList()
            assertEquals(3, resultRows.size)
        }
    }

    @Test
    fun testJoinSubQuery02() {
        withCitiesAndUsers { cities, users, userData ->
            val expAlias = users.name.max().alias("m")

            val query = Join(users).joinQuery(on = { it[expAlias].eq(users.name) }) {
                users.slice(users.cityId, expAlias).selectAll().groupBy(users.cityId)
            }
            val innerExp = query.lastQueryAlias!![expAlias]

            assertEquals("q0", query.lastQueryAlias?.alias)
            assertEquals(3, query.selectAll().count())
            assertNotNull(query.slice(users.columns + innerExp).selectAll().first()[innerExp])
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

    @Test
    fun testDefaultExpressions01() {

        fun abs(value: Int) = object : ExpressionWithColumnType<Int>() {
            override fun toSQL(queryBuilder: QueryBuilder): String = "ABS($value)"

            override val columnType: IColumnType = IntegerColumnType()
        }

        val foo = object : IntIdTable("foo") {
            val name = text("name")
            val defaultDateTime = datetime("defaultDateTime").defaultExpression(CurrentDateTime())
            val defaultInt = integer("defaultInteger").defaultExpression(abs(-100))
        }

        withTables(foo) {
            val id = foo.insertAndGetId {
                it[foo.name] = "bar"
            }
            val result = foo.select { foo.id eq id }.single()

            assertEquals(today, result[foo.defaultDateTime].withTimeAtStartOfDay())
            assertEquals(100, result[foo.defaultInt])
        }
    }

    @Test
    fun testDefaultExpressions02() {
        val foo = object : IntIdTable("foo") {
            val name = text("name")
            val defaultDateTime = datetime("defaultDateTime").defaultExpression(CurrentDateTime())
        }

        val nonDefaultDate = DateTime.parse("2000-01-01")

        withTables(foo) {
            val id = foo.insertAndGetId {
                it[foo.name] = "bar"
                it[foo.defaultDateTime] = nonDefaultDate
            }

            val result = foo.select { foo.id eq id }.single()

            assertEquals("bar", result[foo.name])
            assertEqualDateTime(nonDefaultDate, result[foo.defaultDateTime])

            foo.update({foo.id eq id}) {
                it[foo.name] = "baz"
            }

            val result2 = foo.select { foo.id eq id }.single()
            assertEquals("baz", result2[foo.name])
            assertEqualDateTime(nonDefaultDate, result2[foo.defaultDateTime])
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
            assert(resultRow[rand] is BigDecimal)
            println(resultRow[rand])
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
            fun ColumnSet.repr(): String = this.describe(TransactionManager.current(), QueryBuilder(false))

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
            fun Op<Boolean>.repr(): String = this.toSQL(QueryBuilder(false))

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
            fun Op<Boolean>.repr(): String = this.toSQL(QueryBuilder(false))

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
            val id = stringTable.insertAndGetId {
                it[id] = entityID
                it[name] = "foo"
            }

            assertEquals(id, entityID)
            val row1 = stringTable.select { stringTable.id eq entityID }.singleOrNull()
            assertNotNull(row1)
            assertEquals(row1[stringTable.id], entityID)
        }
    }

    @Test fun testTRUEandFALSEOps() {
        withCitiesAndUsers { cities, _, _ ->
            val allSities = cities.selectAll().map { it[cities.name] }
            assertEquals(0, cities.select { Op.FALSE }.count())
            assertEquals(allSities.size, cities.select { Op.TRUE }.count())
        }
    }
}

private val today: DateTime = DateTime.now().withTimeAtStartOfDay()

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
