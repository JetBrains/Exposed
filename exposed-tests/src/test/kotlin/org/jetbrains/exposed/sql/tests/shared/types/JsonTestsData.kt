package org.jetbrains.exposed.sql.tests.shared.types

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.insert
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
}

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

@Serializable
data class DataHolder(val user: User, val logins: Int, val active: Boolean, val team: String?)

@Serializable
data class User(val name: String, val team: String?)
