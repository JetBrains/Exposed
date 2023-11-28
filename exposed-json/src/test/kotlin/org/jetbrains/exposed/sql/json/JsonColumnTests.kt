package org.jetbrains.exposed.sql.json

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ArraySerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.exceptions.UnsupportedByDialectException
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.greaterEq
import org.jetbrains.exposed.sql.tests.DatabaseTestsBase
import org.jetbrains.exposed.sql.tests.TestDB
import org.jetbrains.exposed.sql.tests.currentDialectTest
import org.jetbrains.exposed.sql.tests.shared.assertEqualCollections
import org.jetbrains.exposed.sql.tests.shared.assertEquals
import org.jetbrains.exposed.sql.tests.shared.assertTrue
import org.jetbrains.exposed.sql.tests.shared.expectException
import org.jetbrains.exposed.sql.vendors.OracleDialect
import org.jetbrains.exposed.sql.vendors.PostgreSQLDialect
import org.jetbrains.exposed.sql.vendors.SQLServerDialect
import org.junit.Test
import kotlin.test.assertContentEquals

class JsonColumnTests : DatabaseTestsBase() {
    @Test
    fun testInsertAndSelect() {
        withJsonTable { tester, _, _, _ ->
            val newData = DataHolder(User("Pro", "Alpha"), 999, true, "A")
            val newId = tester.insertAndGetId {
                it[jsonColumn] = newData
            }

            val newResult = tester.select { tester.id eq newId }.singleOrNull()
            assertEquals(newData, newResult?.get(tester.jsonColumn))
        }
    }

    @Test
    fun testUpdate() {
        withJsonTable { tester, _, data1, _ ->
            assertEquals(data1, tester.selectAll().single()[tester.jsonColumn])

            val updatedData = data1.copy(active = false)
            tester.update {
                it[jsonColumn] = updatedData
            }

            assertEquals(updatedData, tester.selectAll().single()[tester.jsonColumn])
        }
    }

    @Test
    fun testSelectWithSliceExtract() {
        withJsonTable(exclude = TestDB.allH2TestDB) { tester, user1, data1, _ ->
            val pathPrefix = if (currentDialectTest is PostgreSQLDialect) "" else "."
            // SQLServer & Oracle return null if extracted JSON is not scalar
            val requiresScalar = currentDialectTest is SQLServerDialect || currentDialectTest is OracleDialect
            val isActive = tester.jsonColumn.extract<Boolean>("${pathPrefix}active", toScalar = requiresScalar)
            val result1 = tester.slice(isActive).selectAll().singleOrNull()
            assertEquals(data1.active, result1?.get(isActive))

            val storedUser = tester.jsonColumn.extract<User>("${pathPrefix}user", toScalar = false)
            val result2 = tester.slice(storedUser).selectAll().singleOrNull()
            assertEquals(user1, result2?.get(storedUser))

            val path = if (currentDialectTest is PostgreSQLDialect) arrayOf("user", "name") else arrayOf(".user.name")
            val username = tester.jsonColumn.extract<String>(*path)
            val result3 = tester.slice(username).selectAll().singleOrNull()
            assertEquals(user1.name, result3?.get(username))
        }
    }

    @Test
    fun testSelectWhereWithExtract() {
        withJsonTable(exclude = TestDB.allH2TestDB) { tester, _, data1, _ ->
            val newId = tester.insertAndGetId {
                it[jsonColumn] = data1.copy(logins = 1000)
            }

            // Postgres requires type casting to compare json field as integer value in DB
            val logins = if (currentDialectTest is PostgreSQLDialect) {
                tester.jsonColumn.extract<Int>("logins").castTo(IntegerColumnType())
            } else {
                tester.jsonColumn.extract<Int>(".logins")
            }
            val tooManyLogins = logins greaterEq 1000

            val result = tester.slice(tester.id).select { tooManyLogins }.singleOrNull()
            assertEquals(newId, result?.get(tester.id))
        }
    }

    @Test
    fun testWithNonSerializableClass() {
        data class Fake(val number: Int)

        withDb { testDb ->
            excludingH2Version1(testDb) {
                expectException<SerializationException> {
                    // Throws with message: Serializer for class 'Fake' is not found.
                    // Please ensure that class is marked as '@Serializable' and that the serialization compiler plugin is applied.
                    val tester = object : Table("tester") {
                        val jCol = json<Fake>("j_col", Json)
                    }
                }
            }
        }
    }

    @Test
    fun testDAOFunctionsWithJsonColumn() {
        val dataTable = JsonTestsData.JsonTable
        val dataEntity = JsonTestsData.JsonEntity

        withDb { testDb ->
            excludingH2Version1(testDb) {
                SchemaUtils.create(dataTable)

                val dataA = DataHolder(User("Admin", "Alpha"), 10, true, null)
                val newUser = dataEntity.new {
                    jsonColumn = dataA
                }

                assertEquals(dataA, dataEntity.findById(newUser.id)?.jsonColumn)

                val updatedUser = dataA.copy(user = User("Lead", "Beta"))
                dataTable.update {
                    it[jsonColumn] = updatedUser
                }

                assertEquals(updatedUser, dataEntity.all().single().jsonColumn)

                if (testDb !in TestDB.allH2TestDB) {
                    dataEntity.new { jsonColumn = dataA }
                    val path = if (currentDialectTest is PostgreSQLDialect) {
                        arrayOf("user", "team")
                    } else {
                        arrayOf(".user.team")
                    }
                    val userTeam = dataTable.jsonColumn.extract<String>(*path)
                    val userInTeamB = dataEntity.find { userTeam like "B%" }.single()

                    assertEquals(updatedUser, userInTeamB.jsonColumn)
                }

                SchemaUtils.drop(dataTable)
            }
        }
    }

    private val jsonContainsNotSupported = TestDB.entries -
        listOf(TestDB.POSTGRESQL, TestDB.POSTGRESQLNG, TestDB.MYSQL, TestDB.MARIADB)

    @Test
    fun testJsonContains() {
        withJsonTable(exclude = jsonContainsNotSupported) { tester, user1, data1, testDb ->
            val alphaTeamUser = user1.copy(team = "Alpha")
            val newId = tester.insertAndGetId {
                it[jsonColumn] = data1.copy(user = alphaTeamUser)
            }

            val userIsInactive = tester.jsonColumn.contains("{\"active\":false}")
            val result = tester.select { userIsInactive }.toList()
            assertEquals(0, result.size)

            val alphaTeamUserAsJson = "{\"user\":${Json.Default.encodeToString(alphaTeamUser)}}"
            var userIsInAlphaTeam = tester.jsonColumn.contains(stringLiteral(alphaTeamUserAsJson))
            assertEquals(1, tester.select { userIsInAlphaTeam }.count())

            // test target contains candidate at specified path
            if (testDb in TestDB.mySqlRelatedDB) {
                userIsInAlphaTeam = tester.jsonColumn.contains("\"Alpha\"", ".user.team")
                val alphaTeamUsers = tester.slice(tester.id).select { userIsInAlphaTeam }
                assertEquals(newId, alphaTeamUsers.single()[tester.id])
            }
        }
    }

    @Test
    fun testJsonExists() {
        withJsonTable(exclude = TestDB.allH2TestDB + TestDB.SQLSERVER) { tester, _, data1, testDb ->
            val maximumLogins = 1000
            val teamA = "A"
            val newId = tester.insertAndGetId {
                it[jsonColumn] = data1.copy(user = data1.user.copy(team = teamA), logins = maximumLogins)
            }

            val optional = if (testDb in TestDB.mySqlRelatedDB) "one" else null

            // test data at path root '$' exists by providing no path arguments
            val hasAnyData = tester.jsonColumn.exists(optional = optional)
            assertEquals(2, tester.select { hasAnyData }.count())

            val hasFakeKey = tester.jsonColumn.exists(".fakeKey", optional = optional)
            assertEquals(0, tester.select { hasFakeKey }.count())

            val hasLogins = tester.jsonColumn.exists(".logins", optional = optional)
            assertEquals(2, tester.select { hasLogins }.count())

            // test data at path exists with filter condition & optional arguments
            val testDialect = currentDialectTest
            if (testDialect is OracleDialect || testDialect is PostgreSQLDialect) {
                val filterPath = if (testDialect is OracleDialect) {
                    "?(@.logins == $maximumLogins)"
                } else {
                    ".logins ? (@ == $maximumLogins)"
                }
                val hasMaxLogins = tester.jsonColumn.exists(filterPath)
                val usersWithMaxLogin = tester.slice(tester.id).select { hasMaxLogins }
                assertEquals(newId, usersWithMaxLogin.single()[tester.id])

                val (jsonPath, optionalArg) = if (testDialect is OracleDialect) {
                    "?(@.user.team == \$team)" to "PASSING '$teamA' AS \"team\""
                } else {
                    ".user.team ? (@ == \$team)" to "{\"team\":\"$teamA\"}"
                }
                val isOnTeamA = tester.jsonColumn.exists(jsonPath, optional = optionalArg)
                val usersOnTeamA = tester.slice(tester.id).select { isOnTeamA }
                assertEquals(newId, usersOnTeamA.single()[tester.id])
            }
        }
    }

    @Test
    fun testJsonExtractWithArrays() {
        withJsonArrays(exclude = TestDB.allH2TestDB) { tester, singleId, _, _ ->
            val path1 = if (currentDialectTest is PostgreSQLDialect) {
                arrayOf("users", "0", "team")
            } else {
                arrayOf(".users[0].team")
            }
            val firstIsOnTeamA = tester.groups.extract<String>(*path1) eq "Team A"
            assertEquals(singleId, tester.select { firstIsOnTeamA }.single()[tester.id])

            // older MySQL and MariaDB versions require non-scalar extracted value from JSON Array
            val path2 = if (currentDialectTest is PostgreSQLDialect) "0" else "[0]"
            val firstNumber = tester.numbers.extract<Int>(path2, toScalar = !isOldMySql())
            assertEqualCollections(listOf(100, 3), tester.slice(firstNumber).selectAll().map { it[firstNumber] })
        }
    }

    @Test
    fun testJsonContainsWithArrays() {
        withJsonArrays(exclude = jsonContainsNotSupported) { tester, _, tripleId, testDb ->
            val hasSmallNumbers = tester.numbers.contains("[3, 5]")
            assertEquals(tripleId, tester.select { hasSmallNumbers }.single()[tester.id])

            if (testDb in TestDB.mySqlRelatedDB) {
                val hasUserNameB = tester.groups.contains("\"B\"", ".users[0].name")
                assertEquals(tripleId, tester.select { hasUserNameB }.single()[tester.id])
            }
        }
    }

    @Test
    fun testJsonExistsWithArrays() {
        withJsonArrays(exclude = TestDB.allH2TestDB + TestDB.SQLSERVER) { tester, _, tripleId, testDb ->
            val optional = if (testDb in TestDB.mySqlRelatedDB) "one" else null

            val hasMultipleUsers = tester.groups.exists(".users[1]", optional = optional)
            assertEquals(tripleId, tester.select { hasMultipleUsers }.single()[tester.id])

            val hasAtLeast3Numbers = tester.numbers.exists("[2]", optional = optional)
            assertEquals(tripleId, tester.select { hasAtLeast3Numbers }.single()[tester.id])
        }
    }

    @Test
    fun testJsonWithDefaults() {
        val defaultUser = User("UNKNOWN", "UNASSIGNED")
        val defaultTester = object : Table("default_tester") {
            val user1 = json<User>("user_1", Json.Default).default(defaultUser)
            val user2 = json<User>("user_2", Json.Default).clientDefault { defaultUser }
        }

        withDb { testDb ->
            excludingH2Version1(testDb) {
                if (isOldMySql()) {
                    expectException<UnsupportedByDialectException> {
                        SchemaUtils.createMissingTablesAndColumns(defaultTester)
                    }
                } else {
                    SchemaUtils.createMissingTablesAndColumns(defaultTester)
                    assertTrue(defaultTester.exists())
                    // ensure defaults match returned metadata defaults
                    val alters = SchemaUtils.statementsRequiredToActualizeScheme(defaultTester)
                    assertTrue(alters.isEmpty())

                    defaultTester.insert {}

                    defaultTester.selectAll().single().also {
                        assertEquals(defaultUser.name, it[defaultTester.user1].name)
                        assertEquals(defaultUser.team, it[defaultTester.user1].team)

                        assertEquals(defaultUser.name, it[defaultTester.user2].name)
                        assertEquals(defaultUser.team, it[defaultTester.user2].team)
                    }

                    SchemaUtils.drop(defaultTester)
                }
            }
        }
    }

    @OptIn(ExperimentalSerializationApi::class)
    @Test
    fun testLoggerWithJsonCollections() {
        val iterables = object : Table("iterables_tester") {
            val userList = json("user_list", Json.Default, ListSerializer(User.serializer()))
            val intList = json<List<Int>>("int_list", Json.Default)
            val userArray = json("user_array", Json.Default, ArraySerializer(User.serializer()))
            val intArray = json<IntArray>("int_array", Json.Default)
        }

        withDb { testDb ->
            excludingH2Version1(testDb) {
                // the logger is left in to test that it does not throw ClassCastException on insertion of iterables
                addLogger(StdOutSqlLogger)
                SchemaUtils.create(iterables)

                val user1 = User("A", "Team A")
                val user2 = User("B", "Team B")
                val integerList = listOf(1, 2, 3)
                val integerArray = intArrayOf(1, 2, 3)
                iterables.insert {
                    it[userList] = listOf(user1, user2)
                    it[intList] = integerList
                    it[userArray] = arrayOf(user1, user2)
                    it[intArray] = integerArray
                }

                val result = iterables.selectAll().single()
                assertEqualCollections(listOf(user1, user2), result[iterables.userList])
                assertEqualCollections(integerList, result[iterables.intList])
                assertContentEquals(arrayOf(user1, user2), result[iterables.userArray])
                assertContentEquals(integerArray, result[iterables.intArray])

                SchemaUtils.drop(iterables)
            }
        }
    }
}
