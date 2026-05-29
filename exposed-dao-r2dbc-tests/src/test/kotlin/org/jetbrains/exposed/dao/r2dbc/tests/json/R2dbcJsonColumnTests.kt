package org.jetbrains.exposed.dao.r2dbc.tests.json

import kotlinx.coroutines.flow.single
import org.jetbrains.exposed.v1.core.like
import org.jetbrains.exposed.v1.core.vendors.PostgreSQLDialect
import org.jetbrains.exposed.v1.json.extract
import org.jetbrains.exposed.v1.r2dbc.tests.R2dbcDatabaseTestsBase
import org.jetbrains.exposed.v1.r2dbc.tests.TestDB
import org.jetbrains.exposed.v1.r2dbc.tests.currentDialectTest
import org.jetbrains.exposed.v1.r2dbc.tests.shared.assertEquals
import org.jetbrains.exposed.v1.r2dbc.update
import kotlin.test.Test

class R2dbcJsonColumnTests : R2dbcDatabaseTestsBase() {
    @Test
    fun testDAOFunctionsWithJsonColumn() {
        val dataTable = JsonTestsData.JsonTable
        val dataEntity = JsonTestsData.JsonEntity

        withTables(dataTable) { testDb ->
            val dataA = DataHolder(User("Admin", "Alpha"), 10, true, null)
            val newUser = dataEntity.new {
                jsonColumn = dataA
            }

            assertEquals(dataA, dataEntity.findById(newUser.id)?.jsonColumn)

            val updatedUser = dataA.copy(user = User("Lead", "Beta"))
            dataTable.update {
                it[jsonColumn] = updatedUser
            }

            assertEquals(updatedUser, dataEntity.all().single().jsonColumn)

            if (testDb !in TestDB.ALL_H2_V2) {
                dataEntity.new { jsonColumn = dataA }
                val path = if (currentDialectTest is PostgreSQLDialect) {
                    arrayOf("user", "team")
                } else {
                    arrayOf(".user.team")
                }
                val userTeam = JsonTestsData.JsonTable.jsonColumn.extract<String>(*path)
                val userInTeamB = dataEntity.find { userTeam like "B%" }.single()

                assertEquals(updatedUser, userInTeamB.jsonColumn)
            }
        }
    }
}
