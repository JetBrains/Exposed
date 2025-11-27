package org.jetbrains.exposed.v1.json

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ArraySerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.core.dao.id.IntIdTable
import org.jetbrains.exposed.v1.core.vendors.PostgreSQLDialect
import org.jetbrains.exposed.v1.core.vendors.SQLiteDialect
import org.jetbrains.exposed.v1.exceptions.UnsupportedByDialectException
import org.jetbrains.exposed.v1.jdbc.*
import org.jetbrains.exposed.v1.tests.DatabaseTestsBase
import org.jetbrains.exposed.v1.tests.TestDB
import org.jetbrains.exposed.v1.tests.currentDialectTest
import org.jetbrains.exposed.v1.tests.shared.assertEqualCollections
import org.jetbrains.exposed.v1.tests.shared.assertEquals
import org.jetbrains.exposed.v1.tests.shared.assertTrue
import org.jetbrains.exposed.v1.tests.shared.expectException
import org.junit.jupiter.api.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class JsonBColumnTests : DatabaseTestsBase() {
    private val binaryJsonNotSupportedDB = listOf(TestDB.SQLSERVER, TestDB.ORACLE)

    @Test
    fun testInsertAndSelect() {
        withJsonBTable(exclude = binaryJsonNotSupportedDB) { tester, _, _, _ ->
            val newData = DataHolder(User("Pro", "Alpha"), 999, true, "A")
            val newId = tester.insertAndGetId {
                it[jsonBColumn] = newData
            }

            assertJsonBColumnEquals(newData, tester.selectAll().where { tester.id eq newId })
        }
    }

    @Test
    fun testUpdate() {
        withJsonBTable(exclude = binaryJsonNotSupportedDB) { tester, _, data1, _ ->
            assertJsonBColumnEquals(data1, tester.selectAll())

            val updatedData = data1.copy(active = false)
            tester.update {
                it[jsonBColumn] = updatedData
            }

            assertJsonBColumnEquals(updatedData, tester.selectAll())
        }
    }

    @Test
    fun testSelectWithSliceExtract() {
        withJsonBTable(exclude = binaryJsonNotSupportedDB + TestDB.ALL_H2_V2) { tester, user1, data1, _ ->
            val pathPrefix = if (currentDialectTest is PostgreSQLDialect) "" else "."
            val isActive = JsonTestsData.JsonBTable.jsonBColumn.extract<Boolean>("${pathPrefix}active", toScalar = false)
            val result1 = tester.select(isActive).singleOrNull()
            assertEquals(data1.active, result1?.get(isActive))

            val storedUser = JsonTestsData.JsonBTable.jsonBColumn.extract<User>("${pathPrefix}user", toScalar = false)
            val result2 = tester.select(storedUser).singleOrNull()
            assertEquals(user1, result2?.get(storedUser))

            val path = if (currentDialectTest is PostgreSQLDialect) arrayOf("user", "name") else arrayOf(".user.name")
            val username = JsonTestsData.JsonBTable.jsonBColumn.extract<String>(*path)
            val result3 = tester.select(username).singleOrNull()
            assertEquals(user1.name, result3?.get(username))
        }
    }

    @Test
    fun testSelectWhereWithExtract() {
        withJsonBTable(exclude = binaryJsonNotSupportedDB + TestDB.ALL_H2_V2) { tester, _, data1, _ ->
            val newId = tester.insertAndGetId {
                it[jsonBColumn] = data1.copy(logins = 1000)
            }

            // Postgres requires type casting to compare jsonb field as integer value in DB ???
            val logins = if (currentDialectTest is PostgreSQLDialect) {
                JsonTestsData.JsonBTable.jsonBColumn.extract<Int>("logins").castTo(IntegerColumnType())
            } else {
                JsonTestsData.JsonBTable.jsonBColumn.extract<Int>(".logins")
            }
            val tooManyLogins = logins greaterEq 1000

            val result = tester.select(tester.id).where { tooManyLogins }.singleOrNull()
            assertEquals(newId, result?.get(tester.id))
        }
    }

    // TODO
    @Test
    fun testDAOFunctionsWithJsonBColumn() {
        val dataTable = JsonTestsData.JsonBTable
        val dataEntity = JsonTestsData.JsonBEntity

        withTables(excludeSettings = binaryJsonNotSupportedDB, dataTable) { testDb ->
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

            if (testDb !in TestDB.ALL_H2_V2) {
                dataEntity.new { jsonBColumn = dataA }
                val loginCount = if (currentDialectTest is PostgreSQLDialect) {
                    JsonTestsData.JsonBTable.jsonBColumn.extract<Int>("logins").castTo(IntegerColumnType())
                } else {
                    JsonTestsData.JsonBTable.jsonBColumn.extract<Int>(".logins")
                }
                val frequentUser = dataEntity.find { loginCount greaterEq 50 }.single()
                assertEquals(updatedUser, frequentUser.jsonBColumn)
            }
        }
    }

    @Test
    fun testJsonContains() {
        withJsonBTable(exclude = binaryJsonNotSupportedDB + TestDB.ALL_H2_V2 + TestDB.SQLITE) { tester, user1, data1, testDb ->
            val alphaTeamUser = user1.copy(team = "Alpha")
            val newId = tester.insertAndGetId {
                it[jsonBColumn] = data1.copy(user = alphaTeamUser)
            }

            val userIsInactive = JsonTestsData.JsonBTable.jsonBColumn.contains("{\"active\":false}")
            assertEquals(0, tester.selectAll().where { userIsInactive }.count())

            val alphaTeamUserAsJson = "{\"user\":${Json.encodeToString(alphaTeamUser)}}"
            var userIsInAlphaTeam = JsonTestsData.JsonBTable.jsonBColumn.contains(stringLiteral(alphaTeamUserAsJson))
            assertEquals(1, tester.selectAll().where { userIsInAlphaTeam }.count())

            // test target contains candidate at specified path
            if (testDb in TestDB.ALL_MYSQL_LIKE) {
                userIsInAlphaTeam = JsonTestsData.JsonBTable.jsonBColumn.contains("\"Alpha\"", ".user.team")
                val alphaTeamUsers = tester.select(tester.id).where { userIsInAlphaTeam }
                assertEquals(newId, alphaTeamUsers.single()[tester.id])
            }
        }
    }

    @Test
    fun testJsonExists() {
        withJsonBTable(exclude = binaryJsonNotSupportedDB + TestDB.ALL_H2_V2) { tester, _, data1, testDb ->
            val maximumLogins = 1000
            val teamA = "A"
            val newId = tester.insertAndGetId {
                it[jsonBColumn] = data1.copy(user = data1.user.copy(team = teamA), logins = maximumLogins)
            }

            val optional = if (testDb in TestDB.ALL_MYSQL_LIKE) "one" else null

            // test data at path root '$' exists by providing no path arguments
            val hasAnyData = JsonTestsData.JsonBTable.jsonBColumn.exists(optional = optional)
            assertEquals(2, tester.selectAll().where { hasAnyData }.count())

            val hasFakeKey = JsonTestsData.JsonBTable.jsonBColumn.exists(".fakeKey", optional = optional)
            assertEquals(0, tester.selectAll().where { hasFakeKey }.count())

            val hasLogins = JsonTestsData.JsonBTable.jsonBColumn.exists(".logins", optional = optional)
            assertEquals(2, tester.selectAll().where { hasLogins }.count())

            // test data at path exists with filter condition & optional arguments
            if (currentDialectTest is PostgreSQLDialect) {
                val filterPath = ".logins ? (@ == $maximumLogins)"
                val hasMaxLogins = JsonTestsData.JsonBTable.jsonBColumn.exists(filterPath)
                val usersWithMaxLogin = tester.select(tester.id).where { hasMaxLogins }
                assertEquals(newId, usersWithMaxLogin.single()[tester.id])

                val (jsonPath, optionalArg) = $$".user.team ? (@ == $team)" to "{\"team\":\"$teamA\"}"
                val isOnTeamA = JsonTestsData.JsonBTable.jsonBColumn.exists(jsonPath, optional = optionalArg)
                val usersOnTeamA = tester.select(tester.id).where { isOnTeamA }
                assertEquals(newId, usersOnTeamA.single()[tester.id])
            }
        }
    }

    @Test
    fun testJsonExtractWithArrays() {
        withJsonBArrays(exclude = binaryJsonNotSupportedDB + TestDB.ALL_H2_V2) { tester, singleId, _, testDb ->
            val path1 = if (currentDialectTest is PostgreSQLDialect) {
                arrayOf("users", "0", "team")
            } else {
                arrayOf(".users[0].team")
            }
            val firstIsOnTeamA = JsonTestsData.JsonBArrays.groups.extract<String>(*path1) eq "Team A"
            assertEquals(singleId, tester.selectAll().where { firstIsOnTeamA }.single()[tester.id])

            // older MySQL and MariaDB versions require non-scalar extracted value from JSON Array
            val toScalar = testDb != TestDB.MYSQL_V5
            val path2 = if (currentDialectTest is PostgreSQLDialect) "0" else "[0]"
            val firstNumber = JsonTestsData.JsonBArrays.numbers.extract<Int>(path2, toScalar = toScalar)
            assertEqualCollections(listOf(100, 3), tester.select(firstNumber).map { it[firstNumber] })
        }
    }

    @Test
    fun testJsonContainsWithArrays() {
        withJsonBArrays(exclude = binaryJsonNotSupportedDB + TestDB.ALL_H2_V2 + TestDB.SQLITE) { tester, _, tripleId, testDb ->
            val hasSmallNumbers = JsonTestsData.JsonBArrays.numbers.contains("[3, 5]")
            assertEquals(tripleId, tester.selectAll().where { hasSmallNumbers }.single()[tester.id])

            if (testDb in TestDB.ALL_MYSQL_LIKE) {
                val hasUserNameB = JsonTestsData.JsonBArrays.groups.contains("\"B\"", ".users[0].name")
                assertEquals(tripleId, tester.selectAll().where { hasUserNameB }.single()[tester.id])
            }
        }
    }

    @Test
    fun testJsonExistsWithArrays() {
        withJsonBArrays(exclude = binaryJsonNotSupportedDB + TestDB.ALL_H2_V2) { tester, _, tripleId, testDb ->
            val optional = if (testDb in TestDB.ALL_MYSQL_LIKE) "one" else null

            val hasMultipleUsers = JsonTestsData.JsonBArrays.groups.exists(".users[1]", optional = optional)
            assertEquals(tripleId, tester.selectAll().where { hasMultipleUsers }.single()[tester.id])

            val hasAtLeast3Numbers = JsonTestsData.JsonBArrays.numbers.exists("[2]", optional = optional)
            assertEquals(tripleId, tester.selectAll().where { hasAtLeast3Numbers }.single()[tester.id])
        }
    }

    @Test
    fun testJsonBWithDefaults() {
        val defaultUser = User("UNKNOWN", "UNASSIGNED")
        val defaultTester = object : Table("default_tester") {
            val user1 = jsonb<User>("user_1", Json.Default).default(defaultUser)
            val user2 = jsonb<User>("user_2", Json.Default).clientDefault { defaultUser }
        }

        withDb(excludeSettings = listOf(TestDB.MYSQL_V5)) { testDb ->
            if (testDb in binaryJsonNotSupportedDB) {
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

                if (testDb == TestDB.SQLITE) {
                    // ensure JSON strings (in DDL and DML) are being set properly & inserted as BLOB/JSONB binary format,
                    // which is returned in the same format, in a non-readable manner that throws JsonDecodingException
                    expectException<SerializationException> {
                        defaultTester.select(defaultTester.user1).single().also {
                            assertEquals(defaultUser.name, it[defaultTester.user1].name)
                        }
                    }
                    expectException<SerializationException> {
                        defaultTester.select(defaultTester.user2).single().also {
                            assertEquals(defaultUser.name, it[defaultTester.user2].name)
                        }
                    }

                    // SQLite requires JSON() function to convert JSONB binary format to readable text format
                    val user1AsJson = defaultTester.user1.function("JSON").alias("u1")
                    val user2AsJson = defaultTester.user2.function("JSON").alias("u2")
                    defaultTester.select(user1AsJson, user2AsJson).single().also {
                        assertEquals(defaultUser.name, it[user1AsJson]?.name)
                        assertEquals(defaultUser.team, it[user1AsJson]?.team)

                        assertEquals(defaultUser.name, it[user2AsJson]?.name)
                        assertEquals(defaultUser.team, it[user2AsJson]?.team)
                    }
                } else {
                    defaultTester.select(defaultTester.user1, defaultTester.user2).single().also {
                        assertEquals(defaultUser.name, it[defaultTester.user1].name)
                        assertEquals(defaultUser.team, it[defaultTester.user1].team)

                        assertEquals(defaultUser.name, it[defaultTester.user2].name)
                        assertEquals(defaultUser.team, it[defaultTester.user2].team)
                    }
                }

                SchemaUtils.drop(defaultTester)
            }
        }
    }

    @OptIn(ExperimentalSerializationApi::class)
    @Test
    fun testLoggerWithJsonBCollections() {
        val iterables = object : Table("iterables_tester") {
            val userList = jsonb("user_list", Json.Default, ListSerializer(User.serializer()))
            val intList = jsonb<List<Int>>("int_list", Json.Default)
            val userArray = jsonb("user_array", Json.Default, ArraySerializer(User.serializer()))
            val intArray = jsonb<IntArray>("int_array", Json.Default)
        }

        withTables(excludeSettings = binaryJsonNotSupportedDB, iterables) { testDb ->
            // the logger is left in to test that it does not throw ClassCastException on insertion of iterables
            addLogger(StdOutSqlLogger)

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

            if (testDb == TestDB.SQLITE) {
                val result = iterables.select(
                    iterables.userList.asJson(), iterables.intList.asJson(), iterables.userArray.asJson(), iterables.intArray.asJson()
                ).single()
                assertEqualCollections(listOf(user1, user2), result[iterables.userList.asJson()])
                assertEqualCollections(integerList, result[iterables.intList.asJson()])
                assertContentEquals(arrayOf(user1, user2), result[iterables.userArray.asJson()])
                assertContentEquals(integerArray, result[iterables.intArray.asJson()])
            } else {
                val result = iterables.selectAll().single()
                assertEqualCollections(listOf(user1, user2), result[iterables.userList])
                assertEqualCollections(integerList, result[iterables.intList])
                assertContentEquals(arrayOf(user1, user2), result[iterables.userArray])
                assertContentEquals(integerArray, result[iterables.intArray])
            }
        }
    }

    @Test
    fun testJsonBWithNullableColumn() {
        val tester = object : IntIdTable("nullable_tester") {
            val user = jsonb<User>("user", Json.Default).nullable()
        }

        withTables(excludeSettings = binaryJsonNotSupportedDB, tester) { testDb ->
            val nullId = tester.insertAndGetId {
                it[user] = null
            }
            val nonNullId = tester.insertAndGetId {
                it[user] = User("A", "Team A")
            }

            val column = if (testDb == TestDB.SQLITE) tester.user.asJson() else tester.user
            val result1 = tester.select(column).where { tester.id eq nullId }.single()
            assertNull(result1[column])

            val result2 = tester.select(column).where { tester.id eq nonNullId }.single()
            assertNotNull(result2[column])

            val batchData = listOf(null, User("B", "Team B"))
            val batchSql = mutableListOf<String>()
            tester.batchInsert(batchData) { user ->
                this[tester.user] = user

                batchSql += this.prepareSQL(this@withTables, prepared = true)
            }
            assertEquals(batchData.size, batchSql.size)
            val expectedMarker = when (testDb) {
                in TestDB.ALL_POSTGRES -> "?::jsonb"
                else -> "?"
            }
            assertTrue(batchSql.all { it.contains(expectedMarker, ignoreCase = true) })
        }
    }

    @Test
    fun testJsonBWithUpsert() {
        withJsonBTable(exclude = binaryJsonNotSupportedDB) { tester, _, _, _ ->
            val newData = DataHolder(User("Pro", "Alpha"), 999, true, "A")
            val newId = tester.insertAndGetId {
                it[jsonBColumn] = newData
            }

            val newData2 = newData.copy(active = false)
            tester.upsert {
                it[tester.id] = newId
                it[jsonBColumn] = newData2
            }

            assertJsonBColumnEquals(newData2, tester.selectAll().where { tester.id eq newId })
        }
    }

    private fun <T> JdbcTransaction.assertJsonBColumnEquals(expected: T, query: Query) {
        // SQLite requires JSON() function to convert JSONB binary format to readable text format
        val (adjustedColumn, adjustedQuery) = if (currentDialectTest is SQLiteDialect) {
            val column = JsonTestsData.JsonBTable.jsonBColumn.asJson()
            column to query.adjustSelect { oldSelect ->
                val toKeep = oldSelect.fields - JsonTestsData.JsonBTable.jsonBColumn
                select(toKeep + column)
            }
        } else {
            JsonTestsData.JsonBTable.jsonBColumn to query
        }
        assertEquals(expected, adjustedQuery.singleOrNull()?.get(adjustedColumn))
    }

    private class KeyExistsOp(left: Expression<*>, right: Expression<*>) : ComparisonOp(left, right, "??")

    private infix fun ExpressionWithColumnType<*>.keyExists(other: String) = KeyExistsOp(this, stringParam(other))

    @Test
    fun testEscapedPlaceholderInCustomOp() {
        withJsonBTable(exclude = TestDB.ALL - TestDB.ALL_POSTGRES) { tester, _, data1, _ ->
            // the logger is left in to test StatementContext.expandArgs()
            addLogger(StdOutSqlLogger)

            val topLevelKeyResult = tester.selectAll().where { JsonTestsData.JsonBTable.jsonBColumn keyExists "logins" }.single()
            assertEquals(data1, topLevelKeyResult[JsonTestsData.JsonBTable.jsonBColumn])

            val nestedKeyResult = tester.selectAll().where { JsonTestsData.JsonBTable.jsonBColumn keyExists "name" }.toList()
            kotlin.test.assertTrue { nestedKeyResult.isEmpty() }
        }
    }
}
