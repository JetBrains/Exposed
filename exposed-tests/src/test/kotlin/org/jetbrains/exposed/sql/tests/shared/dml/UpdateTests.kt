package org.jetbrains.exposed.sql.tests.shared.dml

import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.exceptions.UnsupportedByDialectException
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.tests.DatabaseTestsBase
import org.jetbrains.exposed.sql.tests.TestDB
import org.jetbrains.exposed.sql.tests.shared.assertEqualLists
import org.jetbrains.exposed.sql.tests.shared.assertEquals
import org.jetbrains.exposed.sql.tests.shared.expectException
import org.jetbrains.exposed.sql.vendors.*
import org.junit.Test
import java.lang.IllegalArgumentException
import kotlin.test.assertNotEquals


class UpdateTests : DatabaseTestsBase() {
    private val notSupportLimit by lazy {
        val exclude = arrayListOf(TestDB.POSTGRESQL, TestDB.POSTGRESQLNG)
        if (!SQLiteDialect.ENABLE_UPDATE_DELETE_LIMIT) {
            exclude.add(TestDB.SQLITE)
        }
        exclude
    }


    @Test
    fun testUpdate01() {
        withCitiesAndUsers {
            val alexId = "alex"
            users.slice(users.name)
                .select { users.id.eq(alexId) }
                .first()[users.name]
                .let { alexName -> assertEquals("Alex", alexName) }

            val newAlexName = "Alexey"
            users.update({ users.id.eq(alexId) }) {
                it[users.name] = newAlexName
            }

            users.slice(users.name)
                .select { users.id.eq(alexId) }
                .first()[users.name]
                .let { actualNewAlexName -> assertEquals(newAlexName, actualNewAlexName) }

            val sergeyId = "sergey"
            unscopedScopedUsers.slice(unscopedScopedUsers.name)
                .select { unscopedScopedUsers.id inList listOf(alexId, sergeyId) }
                .orderBy(unscopedScopedUsers.name, SortOrder.ASC)
                .map { it[unscopedScopedUsers.name] }
                .let { names -> assertEqualLists(names, "Alex", "Sergey") }

            scopedUsers.update({ scopedUsers.id.eq(alexId) }) {
                it[scopedUsers.name] = newAlexName
            }
            unscopedScopedUsers.slice(unscopedScopedUsers.name)
                .select { unscopedScopedUsers.id inList listOf(alexId, sergeyId) }
                .orderBy(unscopedScopedUsers.name, SortOrder.ASC)
                .map { it[unscopedScopedUsers.name] }
                .let { names -> assertEqualLists(names, "Alex", "Sergey") }

            val newSergeyName = "Aye! I'm Sergey!"
            scopedUsers.update({ scopedUsers.id.eq(sergeyId) }) {
                it[scopedUsers.name] = newSergeyName
            }
            unscopedScopedUsers.slice(unscopedScopedUsers.name)
                .select { unscopedScopedUsers.id inList listOf(alexId, sergeyId) }
                .orderBy(unscopedScopedUsers.name, SortOrder.ASC)
                .map { it[unscopedScopedUsers.name] }
                .let { names -> assertEqualLists(names, "Alex", newSergeyName) }

        }
    }

    @Test
    fun testUpdateWithLimit01() {
        withCitiesAndUsers(exclude = notSupportLimit) {
            val aNames = users.slice(users.name).select { users.id like "a%" }.map { it[users.name] }
            assertEquals(2, aNames.size)

            users.update({ users.id like "a%" }, 1) {
                it[users.id] = "NewName"
            }

            val unchanged = users.slice(users.name).select { users.id like "a%" }.count()
            val changed = users.slice(users.name).select { users.id eq "NewName" }.count()
            assertEquals(1, unchanged)
            assertEquals(1, changed)

            scopedUsers.slice(scopedUsers.name)
                .select { scopedUsers.cityId eq munichId() }
                .map { it[scopedUsers.name] }
                .let { munichUsers ->
                    assertEquals(2, munichUsers.size)

                    scopedUsers.update(
                        { scopedUsers.cityId eq munichId() },
                        1
                    ) { it[users.name] = "NewName" }

                    val unchanged = scopedUsers.slice(scopedUsers.name)
                        .select {
                            (scopedUsers.cityId eq munichId())
                            .and(scopedUsers.name neq "NewName")
                        }.count()

                    val changed = scopedUsers.slice(scopedUsers.name)
                        .select { scopedUsers.name eq "NewName" }
                        .count()
                    assertEquals(1, unchanged)
                    assertEquals(1, changed)
                }
        }
    }

    @Test
    fun testUpdateWithLimit02() {
        val dialects = TestDB.values().toList() - notSupportLimit
        withCitiesAndUsers(dialects) {
            expectException<UnsupportedByDialectException> {
                users.update({ users.id like "a%" }, 1) {
                    it[users.id] = "NewName"
                }
            }
        }
    }

    @Test
    fun testUpdateWithJoin() {
        val dialects = listOf(TestDB.SQLITE)

        withCitiesAndUsers(dialects) {
            users.innerJoin(userData)
                .let { join ->
                    join.update {
                        it[userData.comment] = users.name
                        it[userData.value] = 123
                    }

                    join.selectAll().forEach {
                        assertEquals(it[users.name], it[userData.comment])
                        assertEquals(123, it[userData.value])
                    }
                }

            if (currentDialect is PostgreSQLDialect ||
                currentDialect is PostgreSQLNGDialect ||
                currentDialect is OracleDialect ||
                currentDialect is SQLServerDialect ||
                currentDialect is MysqlDialect ||
                currentDialect is SQLiteDialect) {
                scopedUsers.innerJoin(scopedUserData)
                    .let { join ->
                        // Only Sergey should be affected by this update.
                        join.update {
                            it[scopedUserData.comment] = scopedUsers.name
                            it[scopedUserData.value] = 123
                        }.let { assertEquals(1, it) }

                        join.selectAll().toList().let { rows ->
                            assertEquals(1, rows.size)
                            rows.first().let { row ->
                                assertEquals(row[scopedUsers.name], row[scopedUserData.comment])
                                assertEquals(123, row[scopedUserData.value])
                            }
                        }

                        unscopedScopedUsers.innerJoin(unscopedScopedUserData)
                            .select { unscopedScopedUsers.id neq "sergey" }
                            .forEach { row ->
                                assertNotEquals(row[scopedUsers.name], row[scopedUserData.comment])
                                assertNotEquals(123, row[scopedUserData.value])
                            }
                    }
            }
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun `test that column length checked in update `() {
        val stringTable = object : IntIdTable("StringTable") {
            val name = varchar("name", 10)
        }

        withTables(stringTable) {
            stringTable.insert {
                it[name] = "TestName"
            }

            val veryLongString = "1".repeat(255)
            stringTable.update({ stringTable.name eq "TestName" }) {
                it[name] = veryLongString
            }
        }
    }

    @Test
    fun `test update fails with empty body`() {
        withCitiesAndUsers {
            expectException<IllegalArgumentException> {
                cities.update(where = { cities.id.isNull() }) {
                    // empty
                }
            }
        }
    }
}
