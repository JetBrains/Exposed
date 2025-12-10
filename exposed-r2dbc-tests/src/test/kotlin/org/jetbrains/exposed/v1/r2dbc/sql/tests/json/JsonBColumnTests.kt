package org.jetbrains.exposed.v1.r2dbc.sql.tests.json

import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.single
import kotlinx.coroutines.flow.singleOrNull
import kotlinx.coroutines.flow.toList
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.builtins.ArraySerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.core.dao.id.IntIdTable
import org.jetbrains.exposed.v1.core.vendors.PostgreSQLDialect
import org.jetbrains.exposed.v1.exceptions.UnsupportedByDialectException
import org.jetbrains.exposed.v1.json.JsonBColumnType
import org.jetbrains.exposed.v1.json.castToJson
import org.jetbrains.exposed.v1.json.contains
import org.jetbrains.exposed.v1.json.exists
import org.jetbrains.exposed.v1.json.extract
import org.jetbrains.exposed.v1.json.jsonb
import org.jetbrains.exposed.v1.r2dbc.*
import org.jetbrains.exposed.v1.r2dbc.tests.R2dbcDatabaseTestsBase
import org.jetbrains.exposed.v1.r2dbc.tests.TestDB
import org.jetbrains.exposed.v1.r2dbc.tests.currentDialectTest
import org.jetbrains.exposed.v1.r2dbc.tests.shared.assertEqualCollections
import org.jetbrains.exposed.v1.r2dbc.tests.shared.assertEquals
import org.jetbrains.exposed.v1.r2dbc.tests.shared.assertFalse
import org.jetbrains.exposed.v1.r2dbc.tests.shared.assertTrue
import org.jetbrains.exposed.v1.r2dbc.tests.shared.expectException
import org.junit.jupiter.api.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class JsonBColumnTests : R2dbcDatabaseTestsBase() {
    private val binaryJsonNotSupportedDB = listOf(TestDB.SQLSERVER, TestDB.ORACLE)

    @Test
    fun testInsertAndSelect() {
        withJsonBTable(exclude = binaryJsonNotSupportedDB) { tester, _, _, _ ->
            val newData = DataHolder(User("Pro", "Alpha"), 999, true, "A")
            val newId = tester.insertAndGetId {
                it[jsonBColumn] = newData
            }

            val newResult = tester.selectAll().where { tester.id eq newId }.singleOrNull()
            assertEquals(newData, newResult?.get(tester.jsonBColumn))

            val insertedData = tester.insert {
                it[jsonBColumn] = newData
            } get tester.jsonBColumn
            assertEquals(newData, insertedData)

            val returnedValues = tester.insert {
                it[jsonBColumn] = newData
            }.resultedValues?.singleOrNull()
            assertEquals(newData, returnedValues?.get(tester.jsonBColumn))
        }
    }

    @Test
    fun testUpdate() {
        withJsonBTable(exclude = binaryJsonNotSupportedDB) { tester, _, data1, _ ->
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
        withJsonBTable(exclude = binaryJsonNotSupportedDB + TestDB.ALL_H2_V2) { tester, user1, data1, _ ->
            val pathPrefix = if (currentDialectTest is PostgreSQLDialect) "" else "."
            val isActive = tester.jsonBColumn.extract<Boolean>("${pathPrefix}active", toScalar = false)
            val result1 = tester.select(isActive).singleOrNull()
            assertEquals(data1.active, result1?.get(isActive))

            val storedUser = tester.jsonBColumn.extract<User>("${pathPrefix}user", toScalar = false)
            val result2 = tester.select(storedUser).singleOrNull()
            assertEquals(user1, result2?.get(storedUser))

            val path = if (currentDialectTest is PostgreSQLDialect) arrayOf("user", "name") else arrayOf(".user.name")
            val username = tester.jsonBColumn.extract<String>(*path)
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
                tester.jsonBColumn.extract<Int>("logins").castTo(IntegerColumnType())
            } else {
                tester.jsonBColumn.extract<Int>(".logins")
            }
            val tooManyLogins = logins greaterEq 1000

            val result = tester.select(tester.id).where { tooManyLogins }.singleOrNull()
            assertEquals(newId, result?.get(tester.id))
        }
    }

    @Test
    fun testSelectWithAlias() {
        // MariaDB does not allow casting to JSON: https://mariadb.com/docs/server/reference/sql-functions/string-functions/cast
        withJsonBTable(exclude = binaryJsonNotSupportedDB + TestDB.MARIADB) { tester, _, data1, _ ->
            val originalAlias = tester.jsonBColumn.alias("og")
            val castAlias = tester.jsonBColumn.castToJson().alias("cast_og")
            val result1 = tester.select(originalAlias, castAlias).singleOrNull()
            assertEquals(data1, result1?.get(originalAlias))
            assertEquals(data1, result1?.get(castAlias))

            val newData = DataHolder(User("Pro", "Alpha"), 999, true, "A")
            val newId = tester.insertAndGetId {
                it[jsonBColumn] = newData
            }

            val tableAlias = tester.alias("tt")

            val result2 = tableAlias.selectAll()
                .where { tableAlias[tester.id] eq newId and tableAlias[tester.jsonBColumn].isNotNull() }
                .singleOrNull()
            assertEquals(newData, result2?.get(tableAlias[tester.jsonBColumn]))
        }
    }

    @Test
    fun testJsonContains() {
        withJsonBTable(exclude = binaryJsonNotSupportedDB + TestDB.ALL_H2_V2) { tester, user1, data1, testDb ->
            val alphaTeamUser = user1.copy(team = "Alpha")
            val newId = tester.insertAndGetId {
                it[jsonBColumn] = data1.copy(user = alphaTeamUser)
            }

            val userIsInactive = tester.jsonBColumn.contains("{\"active\":false}")
            assertEquals(0, tester.selectAll().where { userIsInactive }.count())

            val alphaTeamUserAsJson = "{\"user\":${Json.Default.encodeToString(alphaTeamUser)}}"
            var userIsInAlphaTeam = tester.jsonBColumn.contains(stringLiteral(alphaTeamUserAsJson))
            assertEquals(1, tester.selectAll().where { userIsInAlphaTeam }.count())

            // test target contains candidate at specified path
            if (testDb in TestDB.ALL_MYSQL_LIKE) {
                userIsInAlphaTeam = tester.jsonBColumn.contains("\"Alpha\"", ".user.team")
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
            val hasAnyData = tester.jsonBColumn.exists(optional = optional)
            assertEquals(2, tester.selectAll().where { hasAnyData }.count())

            val hasFakeKey = tester.jsonBColumn.exists(".fakeKey", optional = optional)
            assertEquals(0, tester.selectAll().where { hasFakeKey }.count())

            val hasLogins = tester.jsonBColumn.exists(".logins", optional = optional)
            assertEquals(2, tester.selectAll().where { hasLogins }.count())

            // test data at path exists with filter condition & optional arguments
            if (currentDialectTest is PostgreSQLDialect) {
                val filterPath = ".logins ? (@ == $maximumLogins)"
                val hasMaxLogins = tester.jsonBColumn.exists(filterPath)
                val usersWithMaxLogin = tester.select(tester.id).where { hasMaxLogins }
                assertEquals(newId, usersWithMaxLogin.single()[tester.id])

                val (jsonPath, optionalArg) = ".user.team ? (@ == \$team)" to "{\"team\":\"$teamA\"}"
                val isOnTeamA = tester.jsonBColumn.exists(jsonPath, optional = optionalArg)
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
            val firstIsOnTeamA = tester.groups.extract<String>(*path1) eq "Team A"
            assertEquals(singleId, tester.selectAll().where { firstIsOnTeamA }.single()[tester.id])

            // older MySQL and MariaDB versions require non-scalar extracted value from JSON Array
            val toScalar = testDb != TestDB.MYSQL_V5
            val path2 = if (currentDialectTest is PostgreSQLDialect) "0" else "[0]"
            val firstNumber = tester.numbers.extract<Int>(path2, toScalar = toScalar)
            assertEqualCollections(listOf(100, 3), tester.select(firstNumber).map { it[firstNumber] }.toList())
        }
    }

    @Test
    fun testJsonContainsWithArrays() {
        withJsonBArrays(exclude = binaryJsonNotSupportedDB + TestDB.ALL_H2_V2) { tester, _, tripleId, testDb ->
            val hasSmallNumbers = tester.numbers.contains("[3, 5]")
            assertEquals(tripleId, tester.selectAll().where { hasSmallNumbers }.single()[tester.id])

            if (testDb in TestDB.ALL_MYSQL_LIKE) {
                val hasUserNameB = tester.groups.contains("\"B\"", ".users[0].name")
                assertEquals(tripleId, tester.selectAll().where { hasUserNameB }.single()[tester.id])
            }
        }
    }

    @Test
    fun testJsonExistsWithArrays() {
        withJsonBArrays(exclude = binaryJsonNotSupportedDB + TestDB.ALL_H2_V2) { tester, _, tripleId, testDb ->
            val optional = if (testDb in TestDB.ALL_MYSQL_LIKE) "one" else null

            val hasMultipleUsers = tester.groups.exists(".users[1]", optional = optional)
            assertEquals(tripleId, tester.selectAll().where { hasMultipleUsers }.single()[tester.id])

            val hasAtLeast3Numbers = tester.numbers.exists("[2]", optional = optional)
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

                defaultTester.selectAll().single().also {
                    assertEquals(defaultUser.name, it[defaultTester.user1].name)
                    assertEquals(defaultUser.team, it[defaultTester.user1].team)

                    assertEquals(defaultUser.name, it[defaultTester.user2].name)
                    assertEquals(defaultUser.team, it[defaultTester.user2].team)
                }

                org.jetbrains.exposed.v1.r2dbc.SchemaUtils.drop(defaultTester)
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

        withTables(excludeSettings = binaryJsonNotSupportedDB, iterables) {
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

            val result = iterables.selectAll().single()
            assertEqualCollections(listOf(user1, user2), result[iterables.userList])
            assertEqualCollections(integerList, result[iterables.intList])
            assertContentEquals(arrayOf(user1, user2), result[iterables.userArray])
            assertContentEquals(integerArray, result[iterables.intArray])
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

            val result1 = tester.select(tester.user).where { tester.id eq nullId }.single()
            assertNull(result1[tester.user])

            val result2 = tester.select(tester.user).where { tester.id eq nonNullId }.single()
            assertNotNull(result2[tester.user])

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
                it[tester.jsonBColumn] = newData2
            }

            val newResult = tester.selectAll().where { tester.id eq newId }.singleOrNull()
            assertEquals(newData2, newResult?.get(tester.jsonBColumn))
        }
    }

    private class KeyExistsOp(left: Expression<*>, right: Expression<*>) : ComparisonOp(left, right, "??")

    private infix fun ExpressionWithColumnType<*>.keyExists(other: String) = KeyExistsOp(this, stringParam(other))

    @Test
    fun testEscapedPlaceholderInCustomOp() {
        withJsonBTable(exclude = TestDB.ALL - TestDB.ALL_POSTGRES) { tester, _, data1, _ ->
            // the logger is left in to test StatementContext.expandArgs()
            addLogger(StdOutSqlLogger)

            val topLevelKeyResult = tester.selectAll().where { tester.jsonBColumn keyExists "logins" }.single()
            assertEquals(data1, topLevelKeyResult[tester.jsonBColumn])

            val nestedKeyResult = tester.selectAll().where { tester.jsonBColumn keyExists "name" }.toList()
            kotlin.test.assertTrue { nestedKeyResult.isEmpty() }
        }
    }

    @Test
    fun testJsonCast() {
        val tester = object : Table("cast_tester") {
            val user = jsonb<User>("user", Json.Default)
            val admin = jsonb<User>("admin", Json.Default).nullable()
            val numbers = jsonb<IntArray>("numbers", Json.Default)
        }

        // MariaDB does not allow casting to JSON: https://mariadb.com/docs/server/reference/sql-functions/string-functions/cast
        withTables(excludeSettings = binaryJsonNotSupportedDB + TestDB.MARIADB, tester) {
            val newUser = User("Pro", "Alpha")
            val newNumbers = intArrayOf(1, 2, 3, 4, 5)
            tester.insert {
                it[tester.user] = newUser
                it[tester.admin] = null
                it[tester.numbers] = newNumbers
            }

            // These will be automatically cast to the JSON version of their original type, like JsonColumnType<User>
            val userAsJson = tester.user.castToJson()
            val adminAsJson = tester.admin.castToJson()
            val numbersAsJson = tester.numbers.castToJson()
            val result = tester.select(userAsJson, adminAsJson, numbersAsJson).single()

            assertEquals(newUser, result[userAsJson])
            assertNull(result[adminAsJson])
            assertContentEquals(newNumbers, result[numbersAsJson])
        }
    }

    @Test
    fun testJsonbCastToJsonFormatFlag() {
        val tester = object : Table("flag_tester") {
            // true is the default setting
            val withFlag = jsonb<User>("with_flag", Json.Default, castToJsonFormat = true)
            val withoutFlag = jsonb<User>("without_flag", Json.Default, castToJsonFormat = false)
        }

        withTables(excludeSettings = binaryJsonNotSupportedDB, tester) { testDb ->
            // all db should switch both flags off
            val actualWithFlag = (tester.withFlag.columnType as JsonBColumnType).needsBinaryFormatCast
            assertFalse(actualWithFlag)
            val actualWithoutFlag = (tester.withoutFlag.columnType as JsonBColumnType).needsBinaryFormatCast
            assertFalse(actualWithoutFlag)

            val user = User("Pro", "Alpha")
            tester.insert {
                it[tester.withFlag] = user
                it[tester.withoutFlag] = user
            }
            val result = tester.selectAll().single()

            // all db should read successfully as flag always ignored (except with SQLite which has no R2DBC)
            assertEquals(user, result[tester.withFlag])
            assertEquals(user, result[tester.withoutFlag])
        }
    }
}
