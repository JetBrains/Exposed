package org.jetbrains.exposed.sql.tests.shared.types

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.greaterEq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.jsonContains
import org.jetbrains.exposed.sql.SqlExpressionBuilder.jsonExists
import org.jetbrains.exposed.sql.tests.DatabaseTestsBase
import org.jetbrains.exposed.sql.tests.TestDB
import org.jetbrains.exposed.sql.tests.currentDialectTest
import org.jetbrains.exposed.sql.tests.currentTestDB
import org.jetbrains.exposed.sql.tests.shared.assertEquals
import org.jetbrains.exposed.sql.vendors.PostgreSQLDialect
import org.junit.Test

class JsonBColumnTypeTests : DatabaseTestsBase() {
    private val binaryJsonNotSupportedDB = listOf(TestDB.SQLITE, TestDB.SQLSERVER) + TestDB.ORACLE

    @Test
    fun testInsertAndSelect() {
        withJsonBTable(exclude = binaryJsonNotSupportedDB) { tester, _, _ ->
            val newData = DataHolder(User("Pro", "Alpha"), 999, true, "A")
            val newId = tester.insertAndGetId {
                it[jsonBColumn] = newData
            }

            val newResult = tester.select { tester.id eq newId }.singleOrNull()
            assertEquals(newData, newResult?.get(tester.jsonBColumn))
        }
    }

    @Test
    fun testUpdate() {
        withJsonBTable(exclude = binaryJsonNotSupportedDB) { tester, _, data1 ->
            assertEquals(data1, tester.selectAll().single()[tester.jsonBColumn])

            val updatedData = data1.copy(active = false)
            tester.update {
                it[jsonBColumn] = updatedData
            }

            assertEquals(updatedData, tester.selectAll().single()[tester.jsonBColumn])
        }
    }

    @Test
    fun testSelectWithSliceExtract() {
        withJsonBTable(exclude = binaryJsonNotSupportedDB + TestDB.allH2TestDB) { tester, user1, data1 ->
            val pathPrefix = if (currentDialectTest is PostgreSQLDialect) "" else "."
            val isActive = tester.jsonBColumn.jsonExtract<Boolean>("${pathPrefix}active", toScalar = false)
            val result1 = tester.slice(isActive).selectAll().singleOrNull()
            assertEquals(data1.active, result1?.get(isActive))

            val storedUser = tester.jsonBColumn.jsonExtract<User>("${pathPrefix}user", toScalar = false)
            val result2 = tester.slice(storedUser).selectAll().singleOrNull()
            assertEquals(user1, result2?.get(storedUser))

            val path = if (currentDialectTest is PostgreSQLDialect) arrayOf("user", "name") else arrayOf(".user.name")
            val username = tester.jsonBColumn.jsonExtract<String>(*path)
            val result3 = tester.slice(username).selectAll().singleOrNull()
            assertEquals(user1.name, result3?.get(username))
        }
    }

    @Test
    fun testSelectWhereWithExtract() {
        withJsonBTable(exclude = binaryJsonNotSupportedDB + TestDB.allH2TestDB) { tester, _, data1 ->
            val newId = tester.insertAndGetId {
                it[jsonBColumn] = data1.copy(logins = 1000)
            }

            // Postgres requires type casting to compare jsonb field as integer value in DB ???
            val logins = if (currentDialectTest is PostgreSQLDialect) {
                tester.jsonBColumn.jsonExtract<Int>("logins").castTo(IntegerColumnType())
            } else {
                tester.jsonBColumn.jsonExtract<Int>(".logins")
            }
            val tooManyLogins = logins greaterEq 1000

            val result = tester.slice(tester.id).select { tooManyLogins }.singleOrNull()
            assertEquals(newId, result?.get(tester.id))
        }
    }

    @Test
    fun testDAOFunctionsWithJsonBColumn() {
        val dataTable = JsonTestsData.JsonBTable
        val dataEntity = JsonTestsData.JsonBEntity

        withDb(excludeSettings = binaryJsonNotSupportedDB) { testDb ->
            excludingH2Version1(testDb) {
                SchemaUtils.create(dataTable)

                val dataA = DataHolder(User("Admin", "Alpha"), 10, true, null)
                val newUser = dataEntity.new {
                    jsonBColumn = dataA
                }

                assertEquals(dataA, dataEntity.findById(newUser.id)?.jsonBColumn)

                val updatedUser = dataA.copy(logins = 99)
                dataTable.update {
                    it[jsonBColumn] = updatedUser
                }

                assertEquals(updatedUser, dataEntity.all().single().jsonBColumn)

                if (testDb !in TestDB.allH2TestDB) {
                    dataEntity.new { jsonBColumn = dataA }
                    val loginCount = if (currentDialectTest is PostgreSQLDialect) {
                        dataTable.jsonBColumn.jsonExtract<Int>("logins").castTo(IntegerColumnType())
                    } else {
                        dataTable.jsonBColumn.jsonExtract<Int>(".logins")
                    }
                    val frequentUser = dataEntity.find { loginCount greaterEq 50 }.single()
                    assertEquals(updatedUser, frequentUser.jsonBColumn)
                }

                SchemaUtils.drop(dataTable)
            }
        }
    }

    @Test
    fun testJsonContains() {
        withJsonBTable(exclude = binaryJsonNotSupportedDB + TestDB.allH2TestDB) { tester, user1, data1 ->
            val alphaTeamUser = user1.copy(team = "Alpha")
            val newId = tester.insertAndGetId {
                it[jsonBColumn] = data1.copy(user = alphaTeamUser)
            }

            val userIsInactive = tester.jsonBColumn.jsonContains(stringLiteral("{\"active\":false}"))
            assertEquals(0, tester.select { userIsInactive }.count())

            val alphaTeamUserAsJson = "{\"user\":${Json.Default.encodeToString(alphaTeamUser)}}"
            var userIsInAlphaTeam = tester.jsonBColumn.jsonContains(stringLiteral(alphaTeamUserAsJson))
            assertEquals(1, tester.select { userIsInAlphaTeam }.count())

            // test target contains candidate at specified path
            if (currentTestDB in TestDB.mySqlRelatedDB) {
                userIsInAlphaTeam = tester.jsonBColumn.jsonContains(stringLiteral("\"Alpha\""), ".user.team")
                val alphaTeamUsers = tester.slice(tester.id).select { userIsInAlphaTeam }
                assertEquals(newId, alphaTeamUsers.single()[tester.id])
            }
        }
    }

    @Test
    fun testJsonExists() {
        withJsonBTable(exclude = binaryJsonNotSupportedDB + TestDB.allH2TestDB) { tester, _, data1 ->
            val maximumLogins = 1000
            val teamA = "A"
            val newId = tester.insertAndGetId {
                it[jsonBColumn] = data1.copy(user = data1.user.copy(team = teamA), logins = maximumLogins)
            }

            val optional = if (currentTestDB in TestDB.mySqlRelatedDB) "one" else null

            // test data at path root '$' exists by providing no path arguments (or an empty string)
            val hasAnyData = tester.jsonBColumn.jsonExists(optional = optional)
            assertEquals(2, tester.select { hasAnyData }.count())

            val hasFakeKey = tester.jsonBColumn.jsonExists(".fakeKey", optional = optional)
            assertEquals(0, tester.select { hasFakeKey }.count())

            val hasLogins = tester.jsonBColumn.jsonExists(".logins", optional = optional)
            assertEquals(2, tester.select { hasLogins }.count())

            // test data at path exists with filter condition & optional arguments
            if (currentDialectTest is PostgreSQLDialect) {
                val filterPath = ".logins ? (@ == $maximumLogins)"
                val hasMaxLogins = tester.jsonBColumn.jsonExists(filterPath)
                val usersWithMaxLogin = tester.slice(tester.id).select { hasMaxLogins }
                assertEquals(newId, usersWithMaxLogin.single()[tester.id])

                val (jsonPath, optionalArg) = ".user.team ? (@ == \$team)" to "{\"team\":\"$teamA\"}"
                val isOnTeamA = tester.jsonBColumn.jsonExists(jsonPath, optional = optionalArg)
                val usersOnTeamA = tester.slice(tester.id).select { isOnTeamA }
                assertEquals(newId, usersOnTeamA.single()[tester.id])
            }
        }
    }
}
