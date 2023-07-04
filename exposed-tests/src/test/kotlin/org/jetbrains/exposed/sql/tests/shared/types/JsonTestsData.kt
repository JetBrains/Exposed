package org.jetbrains.exposed.sql.tests.shared.types

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.tests.DatabaseTestsBase
import org.jetbrains.exposed.sql.tests.TestDB

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

inline fun <reified T : Any> Table.json(
    name: String,
    jsonConfig: Json,
    kSerializer: KSerializer<T> = serializer<T>()
): Column<T> =
    json(name, { jsonConfig.encodeToString(kSerializer, it) }, { jsonConfig.decodeFromString(kSerializer, it) })

inline fun <reified T : Any> Table.jsonb(
    name: String,
    jsonConfig: Json,
    kSerializer: KSerializer<T> = serializer<T>()
): Column<T> =
    jsonb(name, { jsonConfig.encodeToString(kSerializer, it) }, { jsonConfig.decodeFromString(kSerializer, it) })

inline fun <reified T : Any> ExpressionWithColumnType<*>.jsonExtractImpl(
    vararg path: String,
    toScalar: Boolean = true
): JsonExtract<T> = jsonExtract<T>(
    path = path,
    toScalar = toScalar,
    serialize = { Json.Default.encodeToString(serializer<T>(), it) },
    deserialize = { Json.Default.decodeFromString(serializer<T>(), it) }
)

fun DatabaseTestsBase.withJsonTable(
    exclude: List<TestDB> = emptyList(),
    statement: Transaction.(tester: JsonTestsData.JsonTable, user1: User, data1: DataHolder) -> Unit
) {
    val tester = JsonTestsData.JsonTable

    withDb(excludeSettings = exclude) { testDb ->
        excludingH2Version1(testDb) {
            SchemaUtils.create(tester)

            val user1 = User("Admin", null)
            val data1 = DataHolder(user1, 10, true, null)

            tester.insert { it[jsonColumn] = data1 }

            statement(tester, user1, data1)

            SchemaUtils.drop(tester)
        }
    }
}

fun DatabaseTestsBase.withJsonBTable(
    exclude: List<TestDB> = emptyList(),
    statement: Transaction.(tester: JsonTestsData.JsonBTable, user1: User, data1: DataHolder) -> Unit
) {
    val tester = JsonTestsData.JsonBTable

    withDb(excludeSettings = exclude) { testDb ->
        excludingH2Version1(testDb) {
            SchemaUtils.create(tester)

            val user1 = User("Admin", null)
            val data1 = DataHolder(user1, 10, true, null)

            tester.insert { it[jsonBColumn] = data1 }

            statement(tester, user1, data1)

            SchemaUtils.drop(tester)
        }
    }
}

fun DatabaseTestsBase.withJsonArrays(
    exclude: List<TestDB> = emptyList(),
    statement: Transaction.(tester: JsonTestsData.JsonArrays, singleId: EntityID<Int>, tripleId: EntityID<Int>) -> Unit
) {
    val tester = JsonTestsData.JsonArrays

    withDb(excludeSettings = exclude) { testDb ->
        excludingH2Version1(testDb) {
            SchemaUtils.create(tester)

            val singleId = tester.insertAndGetId {
                it[tester.groups] = UserGroup(listOf(User("A", "Team A")))
                it[tester.numbers] = intArrayOf(100)
            }
            val tripleId = tester.insertAndGetId {
                it[tester.groups] = UserGroup(List(3) { i -> User("${'B' + i}", "Team ${'B' + i}") })
                it[tester.numbers] = intArrayOf(3, 4, 5)
            }

            statement(tester, singleId, tripleId)

            SchemaUtils.drop(tester)
        }
    }
}

fun DatabaseTestsBase.withJsonBArrays(
    exclude: List<TestDB> = emptyList(),
    statement: Transaction.(tester: JsonTestsData.JsonBArrays, singleId: EntityID<Int>, tripleId: EntityID<Int>) -> Unit
) {
    val tester = JsonTestsData.JsonBArrays

    withDb(excludeSettings = exclude) { testDb ->
        excludingH2Version1(testDb) {
            SchemaUtils.create(tester)

            val singleId = tester.insertAndGetId {
                it[tester.groups] = UserGroup(listOf(User("A", "Team A")))
                it[tester.numbers] = intArrayOf(100)
            }
            val tripleId = tester.insertAndGetId {
                it[tester.groups] = UserGroup(List(3) { i -> User("${'B' + i}", "Team ${'B' + i}") })
                it[tester.numbers] = intArrayOf(3, 4, 5)
            }

            statement(tester, singleId, tripleId)

            SchemaUtils.drop(tester)
        }
    }
}

@Serializable
data class DataHolder(val user: User, val logins: Int, val active: Boolean, val team: String?)

@Serializable
data class User(val name: String, val team: String?)

@Serializable
data class UserGroup(val users: List<User>)
