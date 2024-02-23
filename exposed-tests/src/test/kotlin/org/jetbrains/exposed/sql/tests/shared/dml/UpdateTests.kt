package org.jetbrains.exposed.sql.tests.shared.dml

import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.dao.id.LongIdTable
import org.jetbrains.exposed.exceptions.UnsupportedByDialectException
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.tests.DatabaseTestsBase
import org.jetbrains.exposed.sql.tests.TestDB
import org.jetbrains.exposed.sql.tests.shared.assertEquals
import org.jetbrains.exposed.sql.tests.shared.expectException
import org.jetbrains.exposed.sql.vendors.SQLiteDialect
import org.junit.Test
import java.lang.IllegalArgumentException

class UpdateTests : DatabaseTestsBase() {
    private val notSupportLimit by lazy {
        val exclude = arrayListOf(TestDB.POSTGRESQL, TestDB.POSTGRESQLNG, TestDB.H2_PSQL)
        if (!SQLiteDialect.ENABLE_UPDATE_DELETE_LIMIT) {
            exclude.add(TestDB.SQLITE)
        }
        exclude
    }

    @Test
    fun testUpdate01() {
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
    fun testUpdateWithLimit01() {
        withCitiesAndUsers(exclude = notSupportLimit) { _, users, _ ->
            val aNames = users.select(users.name).where { users.id like "a%" }.map { it[users.name] }
            assertEquals(2, aNames.size)

            users.update({ users.id like "a%" }, 1) {
                it[users.id] = "NewName"
            }

            val unchanged = users.select(users.name).where { users.id like "a%" }.count()
            val changed = users.select(users.name).where { users.id eq "NewName" }.count()
            assertEquals(1, unchanged)
            assertEquals(1, changed)
        }
    }

    @Test
    fun testUpdateWithLimit02() {
        val dialects = TestDB.entries - notSupportLimit
        withCitiesAndUsers(dialects) { _, users, _ ->
            expectException<UnsupportedByDialectException> {
                users.update({ users.id like "a%" }, 1) {
                    it[users.id] = "NewName"
                }
            }
        }
    }

    @Test
    fun testUpdateWithSingleJoin() {
        withCitiesAndUsers(exclude = listOf(TestDB.SQLITE)) { _, users, userData ->
            val join = users.innerJoin(userData)
            join.update {
                it[userData.comment] = users.name
                it[userData.value] = 123
            }

            join.selectAll().forEach {
                assertEquals(it[users.name], it[userData.comment])
                assertEquals(123, it[userData.value])
            }

            val joinWithConstraint = users.innerJoin(userData, { users.id }, { userData.user_id }) { users.id eq "smth" }
            joinWithConstraint.update {
                it[userData.comment] = users.name
                it[userData.value] = 0
            }

            joinWithConstraint.selectAll().forEach {
                assertEquals(it[users.name], it[userData.comment])
                assertEquals(0, it[userData.value])
            }
        }
    }

    @Test
    fun testUpdateWithMultipleJoins() {
        withCitiesAndUsers(exclude = TestDB.allH2TestDB + TestDB.SQLITE) { cities, users, userData ->
            val join = cities.innerJoin(users).innerJoin(userData)
            join.update {
                it[userData.comment] = users.name
                it[userData.value] = 123
            }

            join.selectAll().forEach {
                assertEquals(it[users.name], it[userData.comment])
                assertEquals(123, it[userData.value])
            }
        }
    }

    @Test
    fun testUpdateWithJoinAndWhere() {
        val tableA = object : LongIdTable("test_table_a") {
            val foo = varchar("foo", 255)
        }
        val tableB = object : LongIdTable("test_table_b") {
            val bar = varchar("bar", 255)
            val tableAId = reference("table_a_id", tableA)
        }

        val supportWhere = TestDB.entries - TestDB.allH2TestDB.toSet() - TestDB.SQLITE + TestDB.H2_ORACLE

        withTables(tableA, tableB) { testingDb ->
            val aId = tableA.insertAndGetId { it[foo] = "foo" }
            tableB.insert {
                it[bar] = "zip"
                it[tableAId] = aId
            }

            val join = tableA.innerJoin(tableB)
            val joinWithConstraint = tableA.innerJoin(tableB, { tableA.id }, { tableB.tableAId }) { tableB.bar eq "foo" }

            if (testingDb in supportWhere) {
                join.update({ tableA.foo eq "foo" }) {
                    it[tableB.bar] = "baz"
                }
                join.selectAll().single().also {
                    assertEquals("baz", it[tableB.bar])
                }

                joinWithConstraint.update({ tableA.foo eq "foo" }) {
                    it[tableB.bar] = "baz"
                }
                assertEquals(0, joinWithConstraint.selectAll().count())
            } else {
                expectException<UnsupportedByDialectException> {
                    join.update({ tableA.foo eq "foo" }) {
                        it[tableB.bar] = "baz"
                    }
                }
            }
        }
    }

    @Test
    fun `test that column length checked in update `() {
        val stringTable = object : IntIdTable("StringTable") {
            val name = varchar("name", 10)
        }

        withTables(stringTable) {
            stringTable.insert {
                it[name] = "TestName"
            }

            val veryLongString = "1".repeat(255)
            expectException<IllegalArgumentException> {
                stringTable.update({ stringTable.name eq "TestName" }) {
                    it[name] = veryLongString
                }
            }
        }
    }

    @Test
    fun `test update fails with empty body`() {
        withCitiesAndUsers { cities, _, _ ->
            expectException<IllegalArgumentException> {
                cities.update(where = { cities.id.isNull() }) {
                    // empty
                }
            }
        }
    }
}
