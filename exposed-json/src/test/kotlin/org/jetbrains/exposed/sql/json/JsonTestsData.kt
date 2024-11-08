package org.jetbrains.exposed.sql.json

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.insertAndGetId
import org.jetbrains.exposed.sql.tests.DatabaseTestsBase
import org.jetbrains.exposed.sql.tests.TestDB
import org.jetbrains.exposed.sql.transactions.JdbcTransaction

object JsonTestsData {
    object JsonTable : IntIdTable("j_table") {
        val jsonColumn = json<DataHolder>("j_column", Json.Default)
    }

    object JsonBTable : IntIdTable("j_b_table") {
        val jsonBColumn = jsonb<DataHolder>("j_b_column", Json.Default)
    }

    class JsonEntity(id: EntityID<Int>) : IntEntity(id) {
        companion object : IntEntityClass<JsonEntity>(JsonTable)

        var jsonColumn by JsonTable.jsonColumn
    }

    class JsonBEntity(id: EntityID<Int>) : IntEntity(id) {
        companion object : IntEntityClass<JsonBEntity>(JsonBTable)

        var jsonBColumn by JsonBTable.jsonBColumn
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

fun DatabaseTestsBase.withJsonTable(
    exclude: Collection<TestDB> = emptyList(),
    statement: JdbcTransaction.(tester: JsonTestsData.JsonTable, user1: User, data1: DataHolder, testDb: TestDB) -> Unit
) {
    val tester = JsonTestsData.JsonTable

    withTables(excludeSettings = exclude, tester) { testDb ->
        val user1 = User("Admin", null)
        val data1 = DataHolder(user1, 10, true, null)

        tester.insert { it[jsonColumn] = data1 }

        statement(tester, user1, data1, testDb)
    }
}

fun DatabaseTestsBase.withJsonBTable(
    exclude: Collection<TestDB> = emptyList(),
    statement: JdbcTransaction.(
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

fun DatabaseTestsBase.withJsonArrays(
    exclude: Collection<TestDB> = emptyList(),
    statement: JdbcTransaction.(
        tester: JsonTestsData.JsonArrays,
        singleId: EntityID<Int>,
        tripleId: EntityID<Int>,
        testDb: TestDB
    ) -> Unit
) {
    val tester = JsonTestsData.JsonArrays

    withTables(excludeSettings = withH2V1(exclude), tester) { testDb ->
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

fun DatabaseTestsBase.withJsonBArrays(
    exclude: Collection<TestDB> = emptyList(),
    statement: JdbcTransaction.(
        tester: JsonTestsData.JsonBArrays,
        singleId: EntityID<Int>,
        tripleId: EntityID<Int>,
        testDb: TestDB
    ) -> Unit
) {
    val tester = JsonTestsData.JsonBArrays

    withTables(excludeSettings = withH2V1(exclude), tester) { testDb ->
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
