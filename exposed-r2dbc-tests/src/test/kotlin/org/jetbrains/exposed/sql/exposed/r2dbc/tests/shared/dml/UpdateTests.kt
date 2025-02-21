package org.jetbrains.exposed.sql.exposed.r2dbc.tests.shared.dml

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.jetbrains.exposed.exceptions.UnsupportedByDialectException
import org.jetbrains.exposed.r2dbc.sql.select
import org.jetbrains.exposed.r2dbc.sql.update
import org.jetbrains.exposed.sql.tests.R2dbcDatabaseTestsBase
import org.jetbrains.exposed.sql.tests.currentDialectMetadataTest
import org.jetbrains.exposed.sql.tests.shared.assertEquals
import org.jetbrains.exposed.sql.tests.shared.expectException
import org.junit.Test

class UpdateTests : R2dbcDatabaseTestsBase() {
    @Test
    fun testUpdate01() = runTest {
        withCitiesAndUsers { _, users, _ ->
            val alexId = "alex"
            val alexName = users.select(users.name).where { users.id.eq(alexId) }.first()[users.name]
            assertEquals("Alex", alexName)

            val newName = "Alexey"
            users.update({ users.id.eq(alexId) }) {
                it[users.name] = newName
            }

            val alexNewName = users.select(users.name).where { users.id.eq(alexId) }.first()[users.name]
            assertEquals(newName, alexNewName)
        }
    }

    @Test
    fun testUpdateWithLimit() = runTest {
        withCitiesAndUsers { _, users, _ ->
            if (!currentDialectMetadataTest.supportsLimitWithUpdateOrDelete()) {
                expectException<UnsupportedByDialectException> {
                    users.update({ users.id like "a%" }, limit = 1) {
                        it[users.id] = "NewName"
                    }
                }
            } else {
                val aNames = users.select(users.name).where { users.id like "a%" }.map { it[users.name] }.toList()
                assertEquals(2, aNames.size)

                users.update({ users.id like "a%" }, limit = 1) {
                    it[users.id] = "NewName"
                }

                val unchanged = users.select(users.name).where { users.id like "a%" }.count()
                val changed = users.select(users.name).where { users.id eq "NewName" }.count()
                assertEquals(1, unchanged)
                assertEquals(1, changed)
            }
        }
    }
}
