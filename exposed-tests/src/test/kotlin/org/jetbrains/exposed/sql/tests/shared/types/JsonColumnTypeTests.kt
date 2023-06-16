package org.jetbrains.exposed.sql.tests.shared.types

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.greaterEq
import org.jetbrains.exposed.sql.tests.DatabaseTestsBase
import org.jetbrains.exposed.sql.tests.TestDB
import org.jetbrains.exposed.sql.tests.currentDialectTest
import org.jetbrains.exposed.sql.tests.shared.assertEquals
import org.jetbrains.exposed.sql.tests.shared.expectException
import org.jetbrains.exposed.sql.vendors.PostgreSQLDialect
import org.jetbrains.exposed.sql.vendors.SQLServerDialect
import org.junit.Test

class JsonColumnTypeTests : DatabaseTestsBase() {
    private val notYetSupportedDB = TestDB.allH2TestDB + TestDB.ORACLE

    @Test
    fun testInsertAndSelect() {
        withJsonTable(exclude = notYetSupportedDB) { tester, _, _ ->
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
        withJsonTable(exclude = notYetSupportedDB) { tester, _, data1 ->
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
        withJsonTable(exclude = notYetSupportedDB) { tester, user1, data1 ->
            // SQLServer returns null if extracted JSON is not scalar
            val requiresScalar = currentDialectTest is SQLServerDialect
            val isActive = tester.jsonColumn.jsonExtract<Boolean>("active", toScalar = requiresScalar)
            val result1 = tester.slice(isActive).selectAll().singleOrNull()
            assertEquals(data1.active, result1?.get(isActive))

            val storedUser = tester.jsonColumn.jsonExtract<User>("user", toScalar = false)
            val result2 = tester.slice(storedUser).selectAll().singleOrNull()
            assertEquals(user1, result2?.get(storedUser))

            val path = if (currentDialectTest is PostgreSQLDialect) arrayOf("user", "name") else arrayOf("user.name")
            val username = tester.jsonColumn.jsonExtract<String>(*path)
            val result3 = tester.slice(username).selectAll().singleOrNull()
            assertEquals(user1.name, result3?.get(username))
        }
    }

    @Test
    fun testSelectWhereWithExtract() {
        withJsonTable(exclude = notYetSupportedDB) { tester, _, data1 ->
            val newId = tester.insertAndGetId {
                it[jsonColumn] = data1.copy(logins = 1000)
            }

            // Postgres requires type casting to compare json field as integer value in DB
            val logins = if (currentDialectTest is PostgreSQLDialect) {
                tester.jsonColumn.jsonExtract<Int>("logins").castTo(IntegerColumnType())
            } else {
                tester.jsonColumn.jsonExtract<Int>("logins")
            }
            val tooManyLogins = logins greaterEq 1000

            val result = tester.slice(tester.id).select { tooManyLogins }.singleOrNull()
            assertEquals(newId, result?.get(tester.id))
        }
    }

    @Test
    fun testWithNonSerializableClass() {
        data class Fake(val number: Int)

        withDb(excludeSettings = notYetSupportedDB) { testDb ->
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
}
