package org.jetbrains.exposed.r2dbc.sql.tests.json

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.r2dbc.sql.R2dbcTransaction
import org.jetbrains.exposed.r2dbc.sql.insert
import org.jetbrains.exposed.r2dbc.sql.insertAndGetId
import org.jetbrains.exposed.sql.json.json
import org.jetbrains.exposed.sql.json.jsonb
import org.jetbrains.exposed.sql.tests.R2dbcDatabaseTestsBase
import org.jetbrains.exposed.sql.tests.TestDB

object JsonTestsData {
    object JsonTable : IntIdTable("j_table") {
        val jsonColumn = json<DataHolder>("j_column", Json.Default)
    }

    object JsonBTable : IntIdTable("j_b_table") {
        val jsonBColumn = jsonb<DataHolder>("j_b_column", Json.Default)
    }

    object JsonArrays : IntIdTable("j_arrays") {
        val groups = json<UserGroup>("groups", Json.Default)
        val numbers = json<IntArray>("numbers", Json.Default)
    }

    object JsonBArrays : IntIdTable("j_b_arrays") {
        val groups = jsonb<UserGroup>("groups", Json.Default)
        val numbers = jsonb<IntArray>("numbers", Json.Default)
    }
}

fun R2dbcDatabaseTestsBase.withJsonTable(
    exclude: Collection<TestDB> = emptyList(),
    statement: suspend R2dbcTransaction.(
        tester: JsonTestsData.JsonTable,
        user1: User,
        data1: DataHolder,
        testDb: TestDB
    ) -> Unit
) {
    val tester = JsonTestsData.JsonTable

    withTables(excludeSettings = exclude, tester) { testDb ->
        val user1 = User("Admin", null)
        val data1 = DataHolder(user1, 10, true, null)

        tester.insert { it[jsonColumn] = data1 }

        statement(tester, user1, data1, testDb)
    }
}

fun R2dbcDatabaseTestsBase.withJsonBTable(
    exclude: Collection<TestDB> = emptyList(),
    statement: suspend R2dbcTransaction.(
        tester: JsonTestsData.JsonBTable,
        user1: User,
        data1: DataHolder,
        testDb: TestDB
    ) -> Unit
) {
    val tester = JsonTestsData.JsonBTable

    withTables(excludeSettings = exclude, tester) { testDb ->
        val user1 = User("Admin", null)
        val data1 = DataHolder(user1, 10, true, null)

        tester.insert { it[jsonBColumn] = data1 }

        statement(tester, user1, data1, testDb)
    }
}

fun R2dbcDatabaseTestsBase.withJsonArrays(
    exclude: Collection<TestDB> = emptyList(),
    statement: suspend R2dbcTransaction.(
        tester: JsonTestsData.JsonArrays,
        singleId: EntityID<Int>,
        tripleId: EntityID<Int>,
        testDb: TestDB
    ) -> Unit
) {
    val tester = JsonTestsData.JsonArrays

    withTables(excludeSettings = exclude, tester) { testDb ->
        val singleId = tester.insertAndGetId {
            it[tester.groups] = UserGroup(listOf(User("A", "Team A")))
            it[tester.numbers] = intArrayOf(100)
        }
        val tripleId = tester.insertAndGetId {
            it[tester.groups] = UserGroup(List(3) { i -> User("${'B' + i}", "Team ${'B' + i}") })
            it[tester.numbers] = intArrayOf(3, 4, 5)
        }

        statement(tester, singleId, tripleId, testDb)
    }
}

fun R2dbcDatabaseTestsBase.withJsonBArrays(
    exclude: Collection<TestDB> = emptyList(),
    statement: suspend R2dbcTransaction.(
        tester: JsonTestsData.JsonBArrays,
        singleId: EntityID<Int>,
        tripleId: EntityID<Int>,
        testDb: TestDB
    ) -> Unit
) {
    val tester = JsonTestsData.JsonBArrays

    withTables(excludeSettings = exclude, tester) { testDb ->
        val singleId = tester.insertAndGetId {
            it[tester.groups] = UserGroup(listOf(User("A", "Team A")))
            it[tester.numbers] = intArrayOf(100)
        }
        val tripleId = tester.insertAndGetId {
            it[tester.groups] = UserGroup(List(3) { i -> User("${'B' + i}", "Team ${'B' + i}") })
            it[tester.numbers] = intArrayOf(3, 4, 5)
        }

        statement(tester, singleId, tripleId, testDb)
    }
}

@Serializable
data class DataHolder(val user: User, val logins: Int, val active: Boolean, val team: String?)

@Serializable
data class User(val name: String, val team: String?)

@Serializable
data class UserGroup(val users: List<User>)
