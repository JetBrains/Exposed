package org.jetbrains.exposed.v1.tests.shared.dml

import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.dao.id.IntIdTable
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.intParam
import org.jetbrains.exposed.v1.core.like
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.core.statements.Statement
import org.jetbrains.exposed.v1.core.statements.StatementBuilder
import org.jetbrains.exposed.v1.core.vendors.H2Dialect
import org.jetbrains.exposed.v1.core.vendors.MysqlDialect
import org.jetbrains.exposed.v1.core.vendors.SQLiteDialect
import org.jetbrains.exposed.v1.jdbc.*
import org.jetbrains.exposed.v1.jdbc.deleteAll
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.tests.DatabaseTestsBase
import org.jetbrains.exposed.v1.tests.TestDB
import org.jetbrains.exposed.v1.tests.currentDialectTest
import org.jetbrains.exposed.v1.tests.shared.assertEquals
import org.jetbrains.exposed.v1.tests.shared.assertTrue
import org.junit.jupiter.api.Test

class ExplainTests : DatabaseTestsBase() {
    private val explainUnsupportedDb = TestDB.ALL_SQLSERVER_LIKE + TestDB.ALL_ORACLE_LIKE

    private object Countries : IntIdTable("countries") {
        val code = varchar("country_code", 8)
    }

    @Test
    fun testExplainWithStatementsNotExecuted() {
        withTables(excludeSettings = explainUnsupportedDb, Countries) {
            val originalCode = "ABC"

            // any statements with explain should not be executed
            explain { Countries.insert { it[code] = originalCode } }.toList()
            assertTrue(Countries.selectAll().empty())

            Countries.insert { it[code] = originalCode }
            assertEquals(1, Countries.selectAll().count())

            explain { Countries.update { it[code] = "DEF" } }.toList()
            assertEquals(originalCode, Countries.selectAll().single()[Countries.code])

            explain { Countries.deleteAll() }.toList()
            assertEquals(1, Countries.selectAll().count())

            Countries.deleteAll()
            assertTrue(Countries.selectAll().empty())
        }
    }

    @Test
    fun testExplainWithAllValidStatementsNotExecuted() {
        var explainCount = 0
        val cityName = "City A"

        fun JdbcTransaction.explainAndIncrement(body: StatementBuilder.() -> Statement<*>) = explain(body = body).also {
            it.toList() // as with select queries, explain is only executed when iterated over
            explainCount++
        }

        withCitiesAndUsers(exclude = explainUnsupportedDb) { cities, users, userData ->
            val testDb = currentDialectTest
            debug = true
            statementCount = 0

            // select statements
            explainAndIncrement {
                cities.select(cities.id).where { cities.name like "A%" }
            }
            explainAndIncrement {
                (users innerJoin cities)
                    .select(users.name, cities.name)
                    .where { (users.id.eq("andrey") or users.name.eq("sergey")) and users.cityId.eq(cities.id) }
            }
            explainAndIncrement {
                val query1 = users.selectAll().where { users.id eq "andrey" }
                val query2 = users.selectAll().where { users.id eq "sergey" }
                query1.union(query2).limit(1)
            }
            // insert statements
            explainAndIncrement { cities.insert { it[name] = cityName } }
            val subquery = userData.select(userData.user_id, userData.comment, intParam(42))
            explainAndIncrement { userData.insert(subquery) }
            // insert or... statements
            if (testDb !is H2Dialect) {
                explainAndIncrement { cities.insertIgnore { it[name] = cityName } }
                explainAndIncrement { userData.insertIgnore(subquery) }
            }
            if (testDb is MysqlDialect || testDb is SQLiteDialect) {
                explainAndIncrement { cities.replace { it[name] = cityName } }
            }
            explainAndIncrement {
                cities.upsert {
                    it[id] = 1
                    it[name] = cityName
                }
            }
            // update statements
            explainAndIncrement { cities.update { it[name] = cityName } }
            if (testDb !is SQLiteDialect) {
                explainAndIncrement {
                    val join = users.innerJoin(userData)
                    join.update { it[userData.value] = 123 }
                }
            }
            // delete statements
            explainAndIncrement { cities.deleteWhere { cities.id eq 1 } }
            if (testDb is MysqlDialect) {
                explainAndIncrement { cities.deleteIgnoreWhere { cities.id eq 1 } }
            }
            explainAndIncrement { cities.deleteAll() }

            assertEquals(explainCount, statementCount)
            assertTrue(statementStats.keys.all { it.startsWith("EXPLAIN ") })

            debug = false
        }
    }

    @Test
    fun testExplainWithAnalyze() {
        val noAnalyzeDb = explainUnsupportedDb + TestDB.SQLITE
        withTables(excludeSettings = noAnalyzeDb, Countries) { testDb ->
            val originalCode = "ABC"

            // MySQL only allows ANALYZE with SELECT queries
            if (testDb !in TestDB.ALL_MYSQL) {
                // analyze means all wrapped statements should also be executed
                explain(analyze = true) { Countries.insert { it[code] = originalCode } }.toList()
                assertEquals(1, Countries.selectAll().count())

                explain(analyze = true) { Countries.update { it[code] = "DEF" } }.toList()
                assertEquals("DEF", Countries.selectAll().single()[Countries.code])

                explain(analyze = true) { Countries.deleteAll() }.toList()
                assertTrue(Countries.selectAll().empty())
            }

            // In MySql prior 8 the EXPLAIN command should be used without ANALYZE modifier
            val analyze = testDb != TestDB.MYSQL_V5

            explain(analyze) { Countries.selectAll() }.toList()
        }
    }

    @Test
    fun testExplainWithOptions() {
        val optionsAvailableDb = TestDB.ALL_POSTGRES + TestDB.ALL_MYSQL_MARIADB
        withTables(excludeSettings = TestDB.ALL - optionsAvailableDb, Countries) { testDB ->
            val formatOption = when (testDB) {
                in TestDB.ALL_MYSQL_LIKE -> "FORMAT=JSON"
                in TestDB.ALL_POSTGRES -> "FORMAT JSON"
                else -> throw UnsupportedOperationException("Format option not provided for this dialect")
            }

            val query = Countries.select(Countries.id).where { Countries.code like "A%" }
            val result = explain(options = formatOption) { query }.single()
            val jsonString = result.toString().substringAfter("=")
            when (testDB) {
                in TestDB.ALL_MYSQL_LIKE -> assertTrue(jsonString.startsWith('{') && jsonString.endsWith('}'))
                else -> assertTrue(jsonString.startsWith('[') && jsonString.endsWith(']'))
            }

            // test multiple options only
            if (testDB in TestDB.ALL_POSTGRES) {
                explain(options = "VERBOSE TRUE, COSTS FALSE") { query }.toList()
            }

            // test analyze + options
            val analyze = testDB != TestDB.MYSQL_V5
            val combinedOption = if (testDB == TestDB.MYSQL_V8) "FORMAT=TREE" else formatOption
            explain(analyze, combinedOption) { query }.toList()
        }
    }

    @Test
    fun testExplainWithInvalidStatements() {
        withTables(excludeSettings = explainUnsupportedDb, Countries) {
            debug = true
            statementCount = 0

            // only the last statement will be executed with explain
            explain {
                Countries.deleteAll()
                Countries.selectAll()
            }.toList()

            assertEquals(1, statementCount)
            val executed = statementStats.keys.single()
            assertTrue(executed.startsWith("EXPLAIN ") && "SELECT " in executed && "DELETE " !in executed)

            debug = false
        }
    }
}
