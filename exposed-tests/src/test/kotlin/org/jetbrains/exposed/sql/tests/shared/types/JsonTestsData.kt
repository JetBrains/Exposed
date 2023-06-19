package org.jetbrains.exposed.sql.tests.shared.types

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.dao.id.IntIdTable
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
}

fun DatabaseTestsBase.withJsonTable(
    exclude: List<TestDB> = emptyList(),
    statement: Transaction.(tester: JsonTestsData.JsonTable, user1: User, data1: DataHolder) -> Unit
) {
    val tester = JsonTestsData.JsonTable

    withTables(exclude, tester) { testDb ->
        excludingH2Version1(testDb) {
            val user1 = User("Admin", null)
            val data1 = DataHolder(user1, 10, true, null)

            tester.insert { it[jsonColumn] = data1 }

            statement(tester, user1, data1)
        }
    }
}

fun DatabaseTestsBase.withJsonBTable(
    exclude: List<TestDB> = emptyList(),
    statement: Transaction.(tester: JsonTestsData.JsonBTable, user1: User, data1: DataHolder) -> Unit
) {
    val tester = JsonTestsData.JsonBTable

    withTables(exclude, tester) { testDb ->
        excludingH2Version1(testDb) {
            val user1 = User("Admin", null)
            val data1 = DataHolder(user1, 10, true, null)

            tester.insert { it[jsonBColumn] = data1 }

            statement(tester, user1, data1)
        }
    }
}

@Serializable
data class DataHolder(val user: User, val logins: Int, val active: Boolean, val team: String?)

@Serializable
data class User(val name: String, val team: String?)
