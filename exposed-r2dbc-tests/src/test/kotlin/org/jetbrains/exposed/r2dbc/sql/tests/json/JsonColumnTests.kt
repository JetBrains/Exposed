package org.jetbrains.exposed.r2dbc.sql.tests.json

import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.single
import kotlinx.coroutines.flow.singleOrNull
import kotlinx.coroutines.flow.toList
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ArraySerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.exceptions.UnsupportedByDialectException
import org.jetbrains.exposed.r2dbc.sql.*
import org.jetbrains.exposed.r2dbc.sql.tests.R2dbcDatabaseTestsBase
import org.jetbrains.exposed.r2dbc.sql.tests.TestDB
import org.jetbrains.exposed.r2dbc.sql.tests.currentDialectTest
import org.jetbrains.exposed.r2dbc.sql.tests.shared.assertEqualCollections
import org.jetbrains.exposed.r2dbc.sql.tests.shared.assertEqualLists
import org.jetbrains.exposed.r2dbc.sql.tests.shared.assertEquals
import org.jetbrains.exposed.r2dbc.sql.tests.shared.assertTrue
import org.jetbrains.exposed.r2dbc.sql.tests.shared.expectException
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.greaterEq
import org.jetbrains.exposed.sql.json.contains
import org.jetbrains.exposed.sql.json.exists
import org.jetbrains.exposed.sql.json.extract
import org.jetbrains.exposed.sql.json.json
import org.jetbrains.exposed.sql.vendors.OracleDialect
import org.jetbrains.exposed.sql.vendors.PostgreSQLDialect
import org.jetbrains.exposed.sql.vendors.SQLServerDialect
import org.junit.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class JsonColumnTests : R2dbcDatabaseTestsBase() {
    @Test
    fun testInsertAndSelect() {
        withJsonTable { tester, _, _, _ ->
            val newData = DataHolder(User("Pro", "Alpha"), 999, true, "A")
            val newId = tester.insertAndGetId {
                it[jsonColumn] = newData
            }

            val newResult = tester.selectAll().where { tester.id eq newId }.singleOrNull()
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
        withJsonTable(exclude = TestDB.ALL_H2_V2) { tester, user1, data1, _ ->
            val pathPrefix = if (currentDialectTest is PostgreSQLDialect) "" else "."
            // SQLServer & Oracle return null if extracted JSON is not scalar
            val requiresScalar = currentDialectTest is SQLServerDialect || currentDialectTest is OracleDialect
            val isActive = tester.jsonColumn.extract<Boolean>("${pathPrefix}active", toScalar = requiresScalar)
            val result1 = tester.select(isActive).singleOrNull()
            assertEquals(data1.active, result1?.get(isActive))

            val storedUser = tester.jsonColumn.extract<User>("${pathPrefix}user", toScalar = false)
            val result2 = tester.select(storedUser).singleOrNull()
            assertEquals(user1, result2?.get(storedUser))

            val path = if (currentDialectTest is PostgreSQLDialect) arrayOf("user", "name") else arrayOf(".user.name")
            val username = tester.jsonColumn.extract<String>(*path)
            val result3 = tester.select(username).singleOrNull()
            assertEquals(user1.name, result3?.get(username))
        }
    }

    @Test
    fun testSelectWhereWithExtract() {
        withJsonTable(exclude = TestDB.ALL_H2_V2) { tester, _, data1, _ ->
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

            val result = tester.select(tester.id).where { tooManyLogins }.singleOrNull()
            assertEquals(newId, result?.get(tester.id))
        }
    }

    @Test
    fun testWithNonSerializableClass() {
        data class Fake(val number: Int)

        withDb {
            expectException<SerializationException> {
                // Throws with message: Serializer for class 'Fake' is not found.
                // Please ensure that class is marked as '@Serializable' and that the serialization compiler plugin is applied.
                val tester = object : Table("tester") {
                    val jCol = json<Fake>("j_col", Json)
                }
            }
        }
    }

    private val jsonContainsNotSupported = TestDB.ALL -
        (TestDB.ALL_POSTGRES + TestDB.ALL_MYSQL_MARIADB)

    @Test
    fun testJsonContains() {
        withJsonTable(exclude = jsonContainsNotSupported) { tester, user1, data1, testDb ->
            val alphaTeamUser = user1.copy(team = "Alpha")
            val newId = tester.insertAndGetId {
                it[jsonColumn] = data1.copy(user = alphaTeamUser)
            }

            val userIsInactive = tester.jsonColumn.contains("{\"active\":false}")
            val result = tester.selectAll().where { userIsInactive }.toList()
            assertEquals(0, result.size)

            val alphaTeamUserAsJson = "{\"user\":${Json.Default.encodeToString(alphaTeamUser)}}"
            var userIsInAlphaTeam = tester.jsonColumn.contains(stringLiteral(alphaTeamUserAsJson))
            assertEquals(1, tester.selectAll().where { userIsInAlphaTeam }.count())

            // test target contains candidate at specified path
            if (testDb in TestDB.ALL_MYSQL_LIKE) {
                userIsInAlphaTeam = tester.jsonColumn.contains("\"Alpha\"", ".user.team")
                val alphaTeamUsers = tester.select(tester.id).where { userIsInAlphaTeam }
                assertEquals(newId, alphaTeamUsers.single()[tester.id])
            }
        }
    }

    @Test
    fun testJsonExists() {
        withJsonTable(exclude = TestDB.ALL_H2_V2 + TestDB.SQLSERVER) { tester, _, data1, testDb ->
            val maximumLogins = 1000
            val teamA = "A"
            val newId = tester.insertAndGetId {
                it[jsonColumn] = data1.copy(user = data1.user.copy(team = teamA), logins = maximumLogins)
            }

            val optional = if (testDb in TestDB.ALL_MYSQL_LIKE) "one" else null

            // test data at path root '$' exists by providing no path arguments
            val hasAnyData = tester.jsonColumn.exists(optional = optional)
            assertEquals(2, tester.selectAll().where { hasAnyData }.count())

            val hasFakeKey = tester.jsonColumn.exists(".fakeKey", optional = optional)
            assertEquals(0, tester.selectAll().where { hasFakeKey }.count())

            val hasLogins = tester.jsonColumn.exists(".logins", optional = optional)
            assertEquals(2, tester.selectAll().where { hasLogins }.count())

            // test data at path exists with filter condition & optional arguments
            val testDialect = currentDialectTest
            if (testDialect is OracleDialect || testDialect is PostgreSQLDialect) {
                val filterPath = if (testDialect is OracleDialect) {
                    "?(@.logins == $maximumLogins)"
                } else {
                    ".logins ? (@ == $maximumLogins)"
                }
                val hasMaxLogins = tester.jsonColumn.exists(filterPath)
                val usersWithMaxLogin = tester.select(tester.id).where { hasMaxLogins }
                assertEquals(newId, usersWithMaxLogin.single()[tester.id])

                val (jsonPath, optionalArg) = if (testDialect is OracleDialect) {
                    "?(@.user.team == \$team)" to "PASSING '$teamA' AS \"team\""
                } else {
                    ".user.team ? (@ == \$team)" to "{\"team\":\"$teamA\"}"
                }
                val isOnTeamA = tester.jsonColumn.exists(jsonPath, optional = optionalArg)
                val usersOnTeamA = tester.select(tester.id).where { isOnTeamA }
                assertEquals(newId, usersOnTeamA.single()[tester.id])
            }
        }
    }

    @Test
    fun testJsonExtractWithArrays() {
        withJsonArrays(exclude = TestDB.ALL_H2_V2) { tester, singleId, _, testDb ->
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
        withJsonArrays(exclude = jsonContainsNotSupported) { tester, _, tripleId, testDb ->
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
        withJsonArrays(exclude = TestDB.ALL_H2_V2 + TestDB.SQLSERVER) { tester, _, tripleId, testDb ->
            val optional = if (testDb in TestDB.ALL_MYSQL_LIKE) "one" else null

            val hasMultipleUsers = tester.groups.exists(".users[1]", optional = optional)
            assertEquals(tripleId, tester.selectAll().where { hasMultipleUsers }.single()[tester.id])

            val hasAtLeast3Numbers = tester.numbers.exists("[2]", optional = optional)
            assertEquals(tripleId, tester.selectAll().where { hasAtLeast3Numbers }.single()[tester.id])
        }
    }

    @Test
    fun testJsonContainsWithIterables() {
        val iterables = object : IntIdTable("iterables") {
            val userList = json<List<User>>("user_list", Json.Default)
            val userSet = json<Set<User>>("user_set", Json.Default)
            val userArray = json<Array<User>>("user_array", Json.Default)
        }

        suspend fun selectIdWhere(condition: SqlExpressionBuilder.() -> Op<Boolean>): List<EntityID<Int>> {
            val query = iterables.select(iterables.id).where(SqlExpressionBuilder.condition())
            return query.map { it[iterables.id] }.toList()
        }

        withTables(excludeSettings = jsonContainsNotSupported, iterables) {
            val user1 = User("A", "Team A")
            val user2 = User("B", "Team B")
            val id1 = iterables.insertAndGetId {
                it[userList] = listOf(user1, user2)
                it[userSet] = setOf(user1)
                it[userArray] = arrayOf(user1, user2)
            }
            val id2 = iterables.insertAndGetId {
                it[userList] = listOf(user2)
                it[userSet] = setOf(user2)
                it[userArray] = arrayOf(user1, user2)
            }

            assertEqualLists(listOf(id1), selectIdWhere { iterables.userList.contains(listOf(user1)) })
            assertEqualLists(listOf(id2), selectIdWhere { iterables.userSet.contains(setOf(user2)) })
            assertEqualLists(listOf(id1, id2), selectIdWhere { iterables.userArray.contains(arrayOf(user1, user2)) })
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
            if (testDb == TestDB.MYSQL_V5) {
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

    @OptIn(ExperimentalSerializationApi::class)
    @Test
    fun testLoggerWithJsonCollections() {
        val iterables = object : Table("iterables_tester") {
            val userList = json("user_list", Json.Default, ListSerializer(User.serializer()))
            val intList = json<List<Int>>("int_list", Json.Default)
            val userArray = json("user_array", Json.Default, ArraySerializer(User.serializer()))
            val intArray = json<IntArray>("int_array", Json.Default)
        }

        withTables(iterables) {
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
    fun testJsonWithNullableColumn() {
        val tester = object : IntIdTable("nullable_tester") {
            val user = json<User>("user", Json.Default).nullable()
        }

        withTables(tester) {
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
        }
    }

    @Test
    fun testJsonWithUpsert() {
        withJsonTable { tester, _, _, _ ->
            val newData = DataHolder(User("Pro", "Alpha"), 999, true, "A")
            val newId = tester.insertAndGetId {
                it[jsonColumn] = newData
            }

            val newData2 = newData.copy(active = false)
            tester.upsert {
                it[tester.id] = newId
                it[tester.jsonColumn] = newData2
            }

            val newResult = tester.selectAll().where { tester.id eq newId }.singleOrNull()
            assertEquals(newData2, newResult?.get(tester.jsonColumn))
        }
    }

    @Test
    fun testJsonWithTransformer() {
        val tester = object : Table("tester") {
            val numbers: Column<DoubleArray> = json<IntArray>("numbers", Json.Default).transform(
                wrap = { DoubleArray(it.size) { i -> 1.0 * it[i] } },
                unwrap = { IntArray(it.size) { i -> it[i].toInt() } }
            )
        }

        withTables(tester) {
            val data = doubleArrayOf(1.0, 2.0, 3.0)
            tester.insert {
                it[numbers] = data
            }

            assertContentEquals(data, tester.selectAll().single()[tester.numbers])
        }
    }

    @Test
    fun testJsonAsDefault() {
        val defaultUser = User("name", "team")
        val tester = object : IntIdTable("testJsonAsDefault") {
            val value = json<User>("value", Json.Default)
                .default(defaultUser)
        }

        val testerDatabaseGenerated = object : IntIdTable("testJsonAsDefault") {
            val value = json<User>("value", Json.Default)
                .databaseGenerated()
        }

        // MySQL versions prior to 8.0.13 do not accept default values on JSON columns
        withTables(excludeSettings = listOf(TestDB.MYSQL_V5), tester) {
            testerDatabaseGenerated.insert { }

            val value = testerDatabaseGenerated.selectAll().single()[tester.value]
            assertEquals(defaultUser, value)
        }
    }
}
