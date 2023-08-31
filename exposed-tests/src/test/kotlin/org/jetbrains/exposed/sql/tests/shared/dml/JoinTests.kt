package org.jetbrains.exposed.sql.tests.shared.dml

import org.apache.logging.log4j.Level
import org.apache.logging.log4j.core.Appender
import org.apache.logging.log4j.core.Filter
import org.apache.logging.log4j.core.LoggerContext
import org.apache.logging.log4j.core.config.Configuration
import org.apache.logging.log4j.core.layout.PatternLayout
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.tests.DatabaseTestsBase
import org.jetbrains.exposed.sql.tests.TestAppender
import org.jetbrains.exposed.sql.tests.shared.assertEquals
import org.jetbrains.exposed.sql.tests.shared.expectException
import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class JoinTests : DatabaseTestsBase() {
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
            val id = integer("id")

            override val primaryKey = PrimaryKey(id)
        }

        val Names = object : Table() {
            val name = varchar("name", 10)

            override val primaryKey = PrimaryKey(name)
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
            assertEquals(2L, rows.count())
        }
    }

    @Test
    fun testJoinWithAdditionalConstraint() {
        withCitiesAndUsers { cities, users, userData ->
            val usersAlias = users.alias("name")
            val join = cities.join(usersAlias, JoinType.INNER, cities.id, usersAlias[users.cityId]) {
                cities.id greater 1 and (cities.name.neq(usersAlias[users.name]))
            }

            assertEquals(2L, join.selectAll().count())
        }
    }

    @Test
    fun testNoWarningsOnLeftJoinRegression() {
        val testAppender = addAppender(Level.WARN)

        val MainTable = object : Table("maintable") {
            val id = integer("idCol")
        }
        val JoinTable = object : Table("jointable") {
            val id = integer("idCol")
            val data = integer("dataCol").default(42)
        }

        withTables(MainTable, JoinTable) {
            MainTable.insert { it[id] = 2 }

            MainTable.join(JoinTable, JoinType.LEFT, JoinTable.id, MainTable.id)
                .slice(JoinTable.data)
                .selectAll()
                .single()
                .getOrNull(JoinTable.data)

            // Assert no logging took place whose source is the ResultRow.getInternal method because that is where the
            // warning comes from
            assertFalse(testAppender.getLog().any { it.source.methodName == "getInternal" })
        }

        removeAppender(testAppender)
    }

    private fun addAppender(level: Level): TestAppender {
        val context = LoggerContext.getContext(false)
        val config = context.configuration
        val layout = PatternLayout.createDefaultLayout(config)
        val testAppender = TestAppender.createAppender(layout, level)
        testAppender.start()
        config.addAppender(testAppender)
        updateLoggers(testAppender, config, level)
        return testAppender
    }

    private fun updateLoggers(appender: Appender, config: Configuration, level: Level, remove: Boolean = false) {
        val filter: Filter? = null
        for (loggerConfig in config.loggers.values) {
            if (remove) {
                loggerConfig.removeAppender(appender.name)
            } else {
                loggerConfig.addAppender(appender, level, filter)
            }
        }
        if (remove) {
            config.rootLogger.removeAppender(appender.name)
        } else {
            config.rootLogger.addAppender(appender, level, filter)
        }
    }

    private fun removeAppender(testAppender: TestAppender) {
        val context = LoggerContext.getContext(false)
        val config = context.configuration
        testAppender.stop()
        updateLoggers(testAppender, config, testAppender.level, remove = true)
    }
}
