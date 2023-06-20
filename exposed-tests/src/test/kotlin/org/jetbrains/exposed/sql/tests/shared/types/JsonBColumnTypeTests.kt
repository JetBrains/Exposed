package org.jetbrains.exposed.sql.tests.shared.types

import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.tests.DatabaseTestsBase
import org.jetbrains.exposed.sql.tests.TestDB
import org.jetbrains.exposed.sql.tests.shared.assertEquals
import org.junit.Test

class JsonBColumnTypeTests : DatabaseTestsBase() {
    private val binaryJsonNotSupportedDB = listOf(TestDB.SQLITE, TestDB.SQLSERVER) + TestDB.ORACLE

    @Test
    fun testInsertAndSelect() {
        withJsonBTable(exclude = binaryJsonNotSupportedDB) { tester, _, _ ->
            val newData = DataHolder(User("Pro", "Alpha"), 999, true, "A")
            val newId = tester.insertAndGetId {
                it[jsonBColumn] = newData
            }

            val newResult = tester.select { tester.id eq newId }.singleOrNull()
            assertEquals(newData, newResult?.get(tester.jsonBColumn))
        }
    }

    @Test
    fun testUpdate() {
        withJsonBTable(exclude = binaryJsonNotSupportedDB) { tester, _, data1 ->
            assertEquals(data1, tester.selectAll().single()[tester.jsonBColumn])

            val updatedData = data1.copy(active = false)
            tester.update {
                it[jsonBColumn] = updatedData
            }

            assertEquals(updatedData, tester.selectAll().single()[tester.jsonBColumn])
        }
    }

    @Test
    fun testDAOFunctionsWithJsonBColumn() {
        val dataTable = JsonTestsData.JsonBTable
        val dataEntity = JsonTestsData.JsonBEntity

        withDb(excludeSettings = binaryJsonNotSupportedDB) { testDb ->
            excludingH2Version1(testDb) {
                SchemaUtils.create(dataTable)

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

                SchemaUtils.drop(dataTable)
            }
        }
    }
}
