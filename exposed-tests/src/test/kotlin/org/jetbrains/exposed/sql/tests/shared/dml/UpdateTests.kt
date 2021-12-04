package org.jetbrains.exposed.sql.tests.shared.dml

import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.dao.id.LongIdTable
import org.jetbrains.exposed.exceptions.UnsupportedByDialectException
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.tests.DatabaseTestsBase
import org.jetbrains.exposed.sql.tests.TestDB
import org.jetbrains.exposed.sql.tests.shared.assertEqualLists
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
    fun testBatchUpdate() {
        val tbl = object  : LongIdTable("batch_updates") {
            val name = varchar("name", 50)
        }
        val scopedTbl = object  : LongIdTable("scoped_batch_updates") {
            val name = varchar("name", 50)
            override val defaultScope = { Op.build { name like "%ergey" } }
        }
        val initialNames = listOf("Alex", "Andrey", "Eugene", "Sergey", "Something")
        withTables(tbl, scopedTbl) {
            initialNames.forEach { name ->
                tbl.insert { it[tbl.name] = name }
                scopedTbl.insert { it[scopedTbl.name] = name }
            }
            val records = {
                tbl.selectAll()
                    .map { it[tbl.id] to it[tbl.name] }
            }

            assertEqualLists(initialNames.sorted(),
                             records().map { it.second }.sorted())

            // Updates all records
            tbl.batchUpdate(records(),
                            id = { it.first },
                            body =  { this[tbl.name] = it.second.lowercase() })

            assertEqualLists(initialNames.map { it.lowercase() },
                             records().map { it.second })

            val unscopedScopedRecords = {
                scopedTbl.stripDefaultScope()
                    .selectAll()
                    .map { it[scopedTbl.id] to it[scopedTbl.name] }
            }
            assertEqualLists(initialNames, unscopedScopedRecords().map { it.second })

            // A batch update respects the default scope
            scopedTbl.batchUpdate(unscopedScopedRecords(),
                                  id = { it.first },
                                  body = { this[scopedTbl.name] = it.second.lowercase() })
            assertEqualLists(initialNames.map { if (it == "Sergey") it.lowercase() else it }.sorted(),
                             unscopedScopedRecords().map { it.second }.sorted())

            // Removing the default scope leads to all records being updated
            scopedTbl.stripDefaultScope()
                .batchUpdate(unscopedScopedRecords(),
                             id = { it.first },
                             body = { this[scopedTbl.name] = it.second.lowercase() })
            assertEqualLists(initialNames.map { it.lowercase() }.sorted(),
                             unscopedScopedRecords().map { it.second }.sorted())
        }
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
            val loadNames = {
                scopedUsers.stripDefaultScope()
                    .slice(scopedUsers.name)
                    .select { scopedUsers.id inList listOf(alexId, sergeyId) }
                    .orderBy(scopedUsers.name, SortOrder.ASC)
                    .map { it[scopedUsers.name] }
            }
            assertEqualLists(loadNames(), "Alex", "Sergey")

            // Doesn't allow updating records that don't match the default scope
            scopedUsers.update({ scopedUsers.id.eq(alexId) }) {
                it[scopedUsers.name] = newAlexName
            }
            assertEqualLists(loadNames(), "Alex", "Sergey")

            // it updates records that match the default scope
            val newSergeyName = "Aye! I'm Sergey!"
            scopedUsers.update({ scopedUsers.id.eq(sergeyId) }) {
                it[scopedUsers.name] = newSergeyName
            }
            assertEqualLists(loadNames(), "Alex", newSergeyName)

            // it updates records that don't match the default scope when the default scope is striped
            scopedUsers.stripDefaultScope()
                .update({ scopedUsers.id.eq(alexId) }) {
                    it[scopedUsers.name] = newAlexName
                }
            assertEqualLists(loadNames(), newAlexName,  newSergeyName)
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

                    // When the default scope is in place, only records that are in-scope are updated
                    scopedUsers.update({ scopedUsers.cityId.isNotNull() }, 1) {
                        it[users.name] = "NewName"
                    }

                    val unchanged = { slice: FieldSet ->
                        slice.select { scopedUsers.name neq "NewName" }
                            .count()
                    }

                    val changed = { slice: FieldSet ->
                        slice.select { scopedUsers.name eq "NewName" }
                            .count()
                    }
                    val x = unchanged(scopedUsers.stripDefaultScope().slice(scopedUsers.name))
                    assertEquals(1, unchanged(scopedUsers.slice(scopedUsers.name)))
                    assertEquals(4, unchanged(scopedUsers.stripDefaultScope().slice(scopedUsers.name)))
                    assertEquals(1, changed(scopedUsers.slice(scopedUsers.name)))
                    assertEquals(1, changed(scopedUsers.stripDefaultScope().slice(scopedUsers.name)))

                    // Even when we increase the limit
                    scopedUsers.update({ scopedUsers.cityId.isNotNull() }, 10) {
                        it[users.name] = "NewName"
                    }
                    assertEquals(0, unchanged(scopedUsers.slice(scopedUsers.name)))
                    assertEquals(3, unchanged(scopedUsers.stripDefaultScope().slice(scopedUsers.name)))
                    assertEquals(2, changed(scopedUsers.slice(scopedUsers.name)))
                    assertEquals(2, changed(scopedUsers.stripDefaultScope().slice(scopedUsers.name)))

                    // When the default scope is striped, all records can be updated
                    scopedUsers.stripDefaultScope()
                        .update({
                                    (scopedUsers.cityId eq munichId())
                                        .or(scopedUsers.cityId neq munichId())
                                        .or(scopedUsers.cityId.isNull()) },
                                10) { it[users.name] = "NewName"
                        }
                    assertEquals(0, unchanged(scopedUsers.slice(scopedUsers.name)))
                    assertEquals(0, unchanged(scopedUsers.stripDefaultScope().slice(scopedUsers.name)))
                    assertEquals(2, changed(scopedUsers.slice(scopedUsers.name)))
                    assertEquals(5, changed(scopedUsers.stripDefaultScope().slice(scopedUsers.name)))
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
                scopedUsers.innerJoin(scopedUserData).let { join ->
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

                        scopedUsers.stripDefaultScope()
                            .innerJoin(scopedUserData.stripDefaultScope())
                            .select { scopedUsers.id neq "sergey" }
                            .forEach { row ->
                                assertNotEquals(row[scopedUsers.name], row[scopedUserData.comment])
                                assertNotEquals(123, row[scopedUserData.value])
                            }
                    }

                // only stripping the right table's scope
                scopedUsers.innerJoin(scopedUserData.stripDefaultScope()).let { join ->
                    // Sergey and Eugene should both be affected by stripping
                    // the right table's scope
                    join.update {
                        it[scopedUserData.comment] = scopedUsers.name
                        it[scopedUserData.value] = 123
                    }.let { assertEquals(2, it) }

                    // Sergey and Eugene should both be affected by stripping
                    // the right table's scope
                    join.selectAll().toList().let { rows ->
                        assertEquals(2, rows.size)
                        rows.first().let { row ->
                            assertEquals(row[scopedUsers.name], row[scopedUserData.comment])
                            assertEquals(123, row[scopedUserData.value])
                        }
                    }

                    scopedUsers.stripDefaultScope()
                        .innerJoin(scopedUserData.stripDefaultScope())
                        .select { scopedUsers.id notInList listOf("sergey", "eugene") }
                        .forEach { row ->
                            assertNotEquals(row[scopedUsers.name], row[scopedUserData.comment])
                            assertNotEquals(123, row[scopedUserData.value])
                        }
                }

                // String both table scopes
                scopedUsers.stripDefaultScope()
                    .innerJoin(scopedUserData.stripDefaultScope())
                    .let { join ->
                    // Sergey and Eugene should both be affected by stripping
                    // the right table's scope
                    join.update {
                        it[scopedUserData.comment] = scopedUsers.name
                        it[scopedUserData.value] = 123
                    }.let { assertEquals(4, it) }

                    // Sergey and Eugene should both be affected by stripping
                    // the right table's scope
                    join.selectAll().toList().let { rows ->
                        assertEquals(4, rows.size)
                        rows.first().let { row ->
                            assertEquals(row[scopedUsers.name], row[scopedUserData.comment])
                            assertEquals(123, row[scopedUserData.value])
                        }
                    }

                    scopedUsers.stripDefaultScope()
                        .innerJoin(scopedUserData.stripDefaultScope())
                        .select { scopedUsers.id eq "andrey" }
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
