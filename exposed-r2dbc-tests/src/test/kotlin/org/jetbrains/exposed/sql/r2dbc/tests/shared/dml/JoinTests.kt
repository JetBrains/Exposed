package org.jetbrains.exposed.sql.r2dbc.tests.shared.dml

import kotlinx.coroutines.flow.all
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.single
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.flow.toSet
import nl.altindag.log.LogCaptor
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.r2dbc.sql.insert
import org.jetbrains.exposed.r2dbc.sql.insertAndGetId
import org.jetbrains.exposed.r2dbc.sql.select
import org.jetbrains.exposed.r2dbc.sql.selectAll
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.tests.R2dbcDatabaseTestsBase
import org.jetbrains.exposed.sql.tests.forEach
import org.jetbrains.exposed.sql.tests.shared.assertEquals
import org.jetbrains.exposed.sql.tests.shared.expectException
import org.junit.Test
import kotlin.test.assertTrue

class JoinTests : R2dbcDatabaseTestsBase() {
    // manual join
    @Test
    fun testJoin01() {
        withCitiesAndUsers { cities, users, _ ->
            (users innerJoin cities).select(users.name, cities.name)
                .where { (users.id.eq("andrey") or users.name.eq("Sergey")) and users.cityId.eq(cities.id) }
                .forEach {
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
        withCitiesAndUsers { cities, users, _ ->
            val stPetersburgUser = (users innerJoin cities).select(users.name, users.cityId, cities.name)
                .where { cities.name.eq("St. Petersburg") or users.cityId.isNull() }.single()
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
        val numbers = object : Table() {
            val id = integer("id")

            override val primaryKey = PrimaryKey(id)
        }

        val names = object : Table() {
            val name = varchar("name", 10)

            override val primaryKey = PrimaryKey(name)
        }

        val map = object : Table() {
            val id_ref = integer("id_ref") references numbers.id
            val name_ref = varchar("name_ref", 10) references names.name
        }

        withTables(numbers, names, map) {
            numbers.insert { it[id] = 1 }
            numbers.insert { it[id] = 2 }
            names.insert { it[name] = "Foo" }
            names.insert { it[name] = "Bar" }
            map.insert {
                it[id_ref] = 2
                it[name_ref] = "Foo"
            }

            val r = (numbers innerJoin map innerJoin names).selectAll().toList()
            assertEquals(1, r.size)
            assertEquals(2, r[0][numbers.id])
            assertEquals("Foo", r[0][names.name])
        }
    }

    // cross join
    @Test
    fun testJoin05() {
        withCitiesAndUsers { cities, users, _ ->
            val allUsersToStPetersburg = (users crossJoin cities).select(users.name, users.cityId, cities.name)
                .where { cities.name.eq("St. Petersburg") }.map {
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
            assertEquals(1L, result.count())
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
                assertEquals(1L, result.count())
            }
        }
    }

    @Test
    fun testJoinWithAlias01() {
        withCitiesAndUsers { _, users, _ ->
            val usersAlias = users.alias("u2")
            val resultRow = Join(users).join(usersAlias, JoinType.LEFT, usersAlias[users.id], stringLiteral("smth"))
                .selectAll().where { users.id eq "alex" }.single()

            assert(resultRow[users.name] == "Alex")
            assert(resultRow[usersAlias[users.name]] == "Something")
        }
    }

    @Test
    fun testJoinWithJoin01() {
        withCitiesAndUsers { cities, users, userData ->
            val rows = (cities innerJoin (users innerJoin userData)).selectAll()
            assertEquals(2L, rows.count())
        }
    }

    @Test
    fun testJoinWithAdditionalConstraint() {
        withCitiesAndUsers { cities, users, _ ->
            val usersAlias = users.alias("name")
            val join = cities.join(usersAlias, JoinType.INNER, cities.id, usersAlias[users.cityId]) {
                cities.id greater 1 and (cities.name.neq(usersAlias[users.name]))
            }

            assertEquals(2L, join.selectAll().count())
        }
    }

    @Test
    fun testNoWarningsOnLeftJoinRegression() {
        val logCaptor = LogCaptor.forName(exposedLogger.name)

        val mainTable = object : Table("maintable") {
            val id = integer("idCol")
        }
        val joinTable = object : Table("jointable") {
            val id = integer("idCol")
            val data = integer("dataCol").default(42)
        }

        withTables(mainTable, joinTable) {
            mainTable.insert { it[id] = 2 }

            mainTable.join(joinTable, JoinType.LEFT, joinTable.id, mainTable.id)
                .select(joinTable.data)
                .single()
                .getOrNull(joinTable.data)

            // Assert no logging took place
            assertTrue(logCaptor.warnLogs.isEmpty())
            assertTrue(logCaptor.errorLogs.isEmpty())
        }
    }
}
