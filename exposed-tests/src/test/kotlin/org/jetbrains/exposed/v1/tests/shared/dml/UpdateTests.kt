package org.jetbrains.exposed.v1.tests.shared.dml

import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.core.dao.id.IntIdTable
import org.jetbrains.exposed.v1.core.dao.id.LongIdTable
import org.jetbrains.exposed.v1.exceptions.UnsupportedByDialectException
import org.jetbrains.exposed.v1.jdbc.*
import org.jetbrains.exposed.v1.tests.DatabaseTestsBase
import org.jetbrains.exposed.v1.tests.TestDB
import org.jetbrains.exposed.v1.tests.currentDialectMetadataTest
import org.jetbrains.exposed.v1.tests.currentTestDB
import org.jetbrains.exposed.v1.tests.shared.assertEquals
import org.jetbrains.exposed.v1.tests.shared.expectException
import org.junit.Test
import kotlin.test.assertTrue

class UpdateTests : DatabaseTestsBase() {
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
    fun testUpdateWithLimit() {
        withCitiesAndUsers { _, users, _ ->
            if (!currentDialectMetadataTest.supportsLimitWithUpdateOrDelete()) {
                expectException<UnsupportedByDialectException> {
                    users.update({ users.id like "a%" }, limit = 1) {
                        it[users.id] = "NewName"
                    }
                }
            } else {
                val aNames = users.select(users.name).where { users.id like "a%" }.map { it[users.name] }
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
    fun testUpdateWithJoinAndLimit() {
        val supportsUpdateWithJoinAndLimit = TestDB.ALL_MARIADB + TestDB.ORACLE + TestDB.SQLSERVER
        withCitiesAndUsers(exclude = TestDB.ALL - supportsUpdateWithJoinAndLimit) { _, users, userData ->
            val join = users.innerJoin(userData)

            val maxToUpdate = 2
            assertTrue { join.selectAll().count() > maxToUpdate }

            val updatedValue = 123
            val valueQuery = join.selectAll().where { userData.value eq updatedValue }
            assertEquals(0, valueQuery.count())

            join.update(limit = maxToUpdate) {
                it[userData.value] = updatedValue
            }

            assertEquals(maxToUpdate, valueQuery.count().toInt())
        }
    }

    @Test
    fun testUpdateWithMultipleJoins() {
        withCitiesAndUsers(exclude = TestDB.ALL_H2 + TestDB.SQLITE) { cities, users, userData ->
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

        val supportWhere = TestDB.entries - TestDB.ALL_H2.toSet() - TestDB.SQLITE + TestDB.H2_V2_ORACLE

        withTables(tableA, tableB) { testingDb ->
            val aId = tableA.insertAndGetId { it[foo] = "foo" }
            tableB.insert {
                it[bar] = "zip"
                it[tableAId] = aId
            }

            val join = tableA.innerJoin(tableB)

            if (testingDb in supportWhere) {
                join.update({ tableA.foo eq "foo" }) {
                    it[tableB.bar] = "baz"
                }
                join.selectAll().single().also {
                    assertEquals("baz", it[tableB.bar])
                }

                val joinWithConstraint = tableA.innerJoin(tableB, { tableA.id }, { tableB.tableAId }) { tableB.bar eq "foo" }
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
    fun testUpdateWithJoinQuery() {
        withCitiesAndUsers(exclude = TestDB.ALL_H2_V1 + TestDB.SQLITE) { _, users, userData ->
            // single join query using join()
            val userAlias = users.selectAll().where { users.cityId neq 1 }.alias("u2")
            val joinWithSubQuery = userData.innerJoin(userAlias, { userData.user_id }, { userAlias[users.id] })
            joinWithSubQuery.update {
                it[userData.value] = 123
            }

            joinWithSubQuery.selectAll().forEach {
                assertEquals(123, it[userData.value])
            }

            if (currentTestDB !in TestDB.ALL_H2) { // does not support either multi-table joins or update(where)
                // single join query using join() with update(where)
                joinWithSubQuery.update({ userData.comment like "Comment%" }) {
                    it[userData.value] = 0
                }

                joinWithSubQuery.selectAll().forEach {
                    assertEquals(0, it[userData.value])
                }

                // multiple join queries using joinQuery()
                val singleJoinQuery = userData.joinQuery(
                    on = { userData.user_id eq it[users.id] },
                    joinPart = { users.selectAll().where { users.cityId neq 1 } }
                )
                val doubleJoinQuery = singleJoinQuery.joinQuery(
                    on = { userData.user_id eq it[users.id] },
                    joinPart = { users.selectAll().where { users.name like "%ey" } }
                )
                doubleJoinQuery.update {
                    it[userData.value] = 99
                }

                doubleJoinQuery.selectAll().forEach {
                    assertEquals(99, it[userData.value])
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
