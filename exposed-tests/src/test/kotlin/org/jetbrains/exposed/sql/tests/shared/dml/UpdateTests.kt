package org.jetbrains.exposed.sql.tests.shared.dml

import org.jetbrains.exposed.exceptions.UnsupportedByDialectException
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.tests.DatabaseTestsBase
import org.jetbrains.exposed.sql.tests.TestDB
import org.jetbrains.exposed.sql.tests.shared.assertEquals
import org.jetbrains.exposed.sql.tests.shared.expectException
import org.junit.Test

class UpdateTests : DatabaseTestsBase() {

    @Test
    fun testUpdate01() {
        withCitiesAndUsers { _, users, _ ->
            val alexId = "alex"
            val alexName = users.slice(users.name).select { users.id.eq(alexId) }.first()[users.name]
            assertEquals("Alex", alexName)

            val newName = "Alexey"
            users.update({ users.id.eq(alexId) }) {
                it[users.name] = newName
            }

            val alexNewName = users.slice(users.name).select { users.id.eq(alexId) }.first()[users.name]
            assertEquals(newName, alexNewName)
        }
    }

    @Test
    fun testUpdateWithLimit01() {
        withCitiesAndUsers(listOf(TestDB.SQLITE, TestDB.POSTGRESQL)) { _, users, _ ->
            val aNames = users.slice(users.name).select { users.id like "a%" }.map { it[users.name] }
            assertEquals(2, aNames.size)

            users.update({ users.id like "a%" }, 1) {
                it[users.id] = "NewName"
            }

            val unchanged = users.slice(users.name).select { users.id like "a%" }.count()
            val changed = users.slice(users.name).select { users.id eq "NewName" }.count()
            assertEquals(1, unchanged)
            assertEquals(1, changed)
        }
    }

    @Test
    fun testUpdateWithLimit02() {
        val dialects = TestDB.values().toList() - listOf(TestDB.SQLITE, TestDB.POSTGRESQL)
        withCitiesAndUsers(dialects) { _, users, _ ->
            expectException<UnsupportedByDialectException> {
                users.update({ users.id like "a%" }, 1) {
                    it[users.id] = "NewName"
                }
            }
        }
    }
    
    @Test
    fun testUpdateWithJoin() {
        val dialects = listOf(TestDB.H2, TestDB.SQLITE, TestDB.H2_MYSQL)
        withCitiesAndUsers(dialects) { cities, users, userData ->
            val join = users.innerJoin(userData)
            join.update {
                it[userData.comment] = users.name
            }

            join.selectAll().forEach {
                assertEquals(it[users.name], it[userData.comment])
            }
        }
    }
}