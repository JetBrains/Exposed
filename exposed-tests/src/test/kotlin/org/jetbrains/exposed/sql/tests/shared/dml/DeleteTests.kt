package org.jetbrains.exposed.sql.tests.shared.dml

import org.jetbrains.exposed.exceptions.UnsupportedByDialectException
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.tests.DatabaseTestsBase
import org.jetbrains.exposed.sql.tests.TestDB
import org.jetbrains.exposed.sql.tests.currentDialectTest
import org.jetbrains.exposed.sql.tests.shared.assertEquals
import org.jetbrains.exposed.sql.tests.shared.assertTrue
import org.jetbrains.exposed.sql.tests.shared.dml.DMLTestsData.ScopedUsers.default
import org.jetbrains.exposed.sql.tests.shared.dml.DMLTestsData.ScopedUsers.nullable
import org.jetbrains.exposed.sql.tests.shared.expectException
import org.jetbrains.exposed.sql.vendors.H2Dialect
import org.jetbrains.exposed.sql.vendors.SQLiteDialect
import org.junit.Test

class DeleteTests : DatabaseTestsBase() {
    private val notSupportLimit by lazy {
        val exclude = arrayListOf(TestDB.POSTGRESQL, TestDB.POSTGRESQLNG, TestDB.ORACLE)
        if (!SQLiteDialect.ENABLE_UPDATE_DELETE_LIMIT) {
            exclude.add(TestDB.SQLITE)
        }
        exclude
    }

    object unscopedScopedUsers : Table(DMLTestsData.ScopedUsers.tableName) {
        val id: Column<String> = varchar("id", 10)
        val name: Column<String> = varchar("name", length = 50)
        val cityId: Column<Int?> = reference("city_id", DMLTestsData.Cities.id).nullable()
        val flags: Column<Int> = integer("flags").default(0)
        override val primaryKey = PrimaryKey(id)
    }

    val unscopedScopedUserData = object : Table(DMLTestsData.ScopedUserData.tableName) {
        val userId: Column<String> = reference("user_id", DMLTestsData.ScopedUsers.id)
        val comment: Column<String> = varchar("comment", 30)
        val value: Column<Int> = integer("value")
    }

    @Test
    fun testDelete01() {
        withCitiesAndUsers { _, users, userData, scopedUsers, scopedUserData ->
            //deletes all data
            userData.deleteAll()
            val userDataExists = userData.selectAll().any()
            assertEquals(false, userDataExists)

            // Only deletes data within scope
            scopedUserData.deleteAll()
            val remainingScopedUserData = unscopedScopedUserData.selectAll().toList().size
            assertEquals(3, remainingScopedUserData)

            // Deleting using a where clause
            val smthId = users.slice(users.id).select { users.name.like("%thing") }.single()[users.id]
            assertEquals("smth", smthId)

            users.deleteWhere { users.name like "%thing" }
            val hasSmth = users.slice(users.id).select { users.name.like("%thing") }.any()
            assertEquals(false, hasSmth)

            // Deleting using a where clause ensures the default scope is applied.
            scopedUsers
                .slice(scopedUsers.id)
                .select { scopedUsers.name.like("%Sergey") }
                .single()[scopedUsers.id]
                .let { sergeyId ->
                    assertEquals("sergey", sergeyId)

                    scopedUsers.deleteWhere { scopedUsers.name like "%er%" }
                    scopedUsers.slice(scopedUsers.id)
                        .select { scopedUsers.name.like("%Sergey") }
                        .any().let { sergeyExists -> assertEquals(false, sergeyExists) }

                    assertEquals(4, unscopedScopedUsers.selectAll().count())
                }
        }
    }

    @Test
    fun testDeleteWithLimitAndOffset01() {
        withCitiesAndUsers(exclude = notSupportLimit) { _, _, userData, scopedUsers, scopedUserData ->
            userData.deleteWhere(limit = 1) { userData.value eq 20 }
            userData.slice(userData.user_id, userData.value)
                .select { userData.value eq 20 }.let {
                    assertEquals(1L, it.count())
                    val expected = if (currentDialectTest is H2Dialect) "smth" else "eugene"
                    assertEquals(expected, it.single()[userData.user_id])
                }
            scopedUserData.insert {
                it[scopedUserData.userId] = "sergey"
                it[scopedUserData.comment] =  "This is Sergey"
                it[scopedUserData.value] = 60
            }
            assertEquals(5, unscopedScopedUserData.selectAll().count())
            scopedUserData.deleteWhere(limit = 1) { scopedUserData.value neq 60 }

            assertEquals(1, scopedUserData.selectAll().count())
            assertEquals(4, unscopedScopedUserData.selectAll().count())
        }
    }

    @Test
    fun testDeleteWithLimit02() {
        val dialects = TestDB.values().toList() - notSupportLimit
        withCitiesAndUsers(dialects) { _, _, userData, _, _ ->
            expectException<UnsupportedByDialectException> {
                userData.deleteWhere(limit = 1) {
                    userData.value eq 20
                }
            }
        }
    }
}
