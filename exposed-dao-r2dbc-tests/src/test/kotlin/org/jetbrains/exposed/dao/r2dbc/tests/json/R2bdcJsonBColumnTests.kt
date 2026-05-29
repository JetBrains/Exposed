package org.jetbrains.exposed.dao.r2dbc.tests.json

import kotlinx.coroutines.flow.single
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.r2dbc.dao.IntR2dbcEntity
import org.jetbrains.exposed.r2dbc.dao.IntR2dbcEntityClass
import org.jetbrains.exposed.v1.core.IntegerColumnType
import org.jetbrains.exposed.v1.core.castTo
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.IntIdTable
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.core.vendors.PostgreSQLDialect
import org.jetbrains.exposed.v1.json.extract
import org.jetbrains.exposed.v1.json.json
import org.jetbrains.exposed.v1.json.jsonb
import org.jetbrains.exposed.v1.r2dbc.tests.R2dbcDatabaseTestsBase
import org.jetbrains.exposed.v1.r2dbc.tests.TestDB
import org.jetbrains.exposed.v1.r2dbc.tests.currentDialectTest
import org.jetbrains.exposed.v1.r2dbc.tests.shared.assertEquals
import org.jetbrains.exposed.v1.r2dbc.update
import org.junit.jupiter.api.Test

class R2bdcJsonBColumnTests : R2dbcDatabaseTestsBase() {
    private val binaryJsonNotSupportedDB = listOf(TestDB.SQLSERVER, TestDB.ORACLE)

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

    object MyTable : IntIdTable("my_table") {
        val name = text("name")
        val user = jsonb<User>("json_column", Json.Default)
    }

    class MyEntity(id: EntityID<Int>) : IntR2dbcEntity(id) {
        companion object : IntR2dbcEntityClass<MyEntity>(MyTable)

        var name by MyTable.name
        var user by MyTable.user
    }

    @Test
    fun testFieldsOutsideTransaction() {
        lateinit var entity: MyEntity
        withTables(excludeSettings = binaryJsonNotSupportedDB, MyTable) {
            entity = MyEntity.new {
                name = "Test"
                user = User("Pro", "Alpha")
            }
        }

        // Should be able to read fields despite having no transaction
        kotlin.test.assertEquals("Test", entity.name)
        kotlin.test.assertEquals(User("Pro", "Alpha"), entity.user)
    }
}
