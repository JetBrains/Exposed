package org.jetbrains.exposed.v1.tests.shared.dml

import junit.framework.TestCase.assertNull
import org.jetbrains.exposed.v1.core.Join
import org.jetbrains.exposed.v1.core.SqlExpressionBuilder.eq
import org.jetbrains.exposed.v1.core.SqlExpressionBuilder.like
import org.jetbrains.exposed.v1.core.alias
import org.jetbrains.exposed.v1.core.innerJoin
import org.jetbrains.exposed.v1.core.joinQuery
import org.jetbrains.exposed.v1.core.lastQueryAlias
import org.jetbrains.exposed.v1.core.vendors.H2Dialect
import org.jetbrains.exposed.v1.core.vendors.MysqlDialect
import org.jetbrains.exposed.v1.exceptions.ExposedSQLException
import org.jetbrains.exposed.v1.exceptions.UnsupportedByDialectException
import org.jetbrains.exposed.v1.jdbc.*
import org.jetbrains.exposed.v1.tests.DatabaseTestsBase
import org.jetbrains.exposed.v1.tests.TestDB
import org.jetbrains.exposed.v1.tests.currentDialectMetadataTest
import org.jetbrains.exposed.v1.tests.currentDialectTest
import org.jetbrains.exposed.v1.tests.shared.assertEquals
import org.jetbrains.exposed.v1.tests.shared.expectException
import org.junit.Test
import kotlin.test.assertTrue
import kotlin.test.expect

class DeleteTests : DatabaseTestsBase() {
    @Test
    fun testDelete01() {
        withCitiesAndUsers { cities, users, userData ->
            userData.deleteAll()
            val userDataExists = userData.selectAll().any()
            assertEquals(false, userDataExists)

            val smthId = users.select(users.id).where { users.name.like("%thing") }.single()[users.id]
            assertEquals("smth", smthId)

            users.deleteWhere { users.name like "%thing" }
            val hasSmth = users.select(users.id).where { users.name.like("%thing") }.any()
            assertEquals(false, hasSmth)

            if (currentDialectTest is MysqlDialect) {
                assertEquals(1, cities.selectAll().where { cities.id eq 1 }.count())
                expectException<ExposedSQLException> {
                    // a regular delete throws SQLIntegrityConstraintViolationException because Users reference Cities
                    // Cannot delete or update a parent row: a foreign key constraint fails
                    cities.deleteWhere { cities.id eq 1 }
                }
                expect(0) {
                    // the error is now ignored and the record is skipped
                    cities.deleteIgnoreWhere { cities.id eq 1 }
                }
                assertEquals(1, cities.selectAll().where { cities.id eq 1 }.count())
            }
        }
    }

    @Test
    fun testDeleteTableInContext() {
        withCitiesAndUsers { _, users, userData ->
            userData.deleteAll()
            val userDataExists = userData.selectAll().any()
            assertEquals(false, userDataExists)

            val smthId = users.select(users.id).where { users.name.like("%thing") }.single()[users.id]
            assertEquals("smth", smthId)

            // Now deleteWhere and deleteIgnoreWhere should bring the table it operates on into context
            users.deleteWhere { name like "%thing" }

            val hasSmth = users.selectAll().where { users.name.like("%thing") }.firstOrNull()
            assertNull(hasSmth)
        }
    }

    @Test
    fun testDeleteWithLimit() {
        withCitiesAndUsers { _, _, userData ->
            if (!currentDialectMetadataTest.supportsLimitWithUpdateOrDelete()) {
                expectException<UnsupportedByDialectException> {
                    userData.deleteWhere(limit = 1) { userData.value eq 20 }
                }
            } else {
                userData.deleteWhere(limit = 1) { userData.value eq 20 }
                userData.select(userData.user_id, userData.value).where { userData.value eq 20 }.let {
                    assertEquals(1L, it.count())
                    val expected = if (currentDialectTest is H2Dialect) "smth" else "eugene"
                    assertEquals(expected, it.single()[userData.user_id])
                }
            }
        }
    }

    @Test
    fun testDeleteWithSingleJoin() {
        withCitiesAndUsers(exclude = listOf(TestDB.SQLITE)) { _, users, userData ->
            val join = users innerJoin userData
            val query1 = join.selectAll().where { userData.user_id like "%ey" }
            assertTrue { query1.count() > 0L }

            join.delete(userData) { userData.user_id like "%ey" }
            assertEquals(0, query1.count())

            val query2 = join.selectAll()
            assertTrue { query2.count() > 0L }

            join.delete(userData)
            assertEquals(0, query2.count())
        }
    }

    @Test
    fun testDeleteWithMultipleAliasJoins() {
        withCitiesAndUsers(exclude = TestDB.ALL_H2_V2 + TestDB.SQLITE) { cities, users, userData ->
            val towns = cities.alias("towns")
            val people = users.alias("people")
            val stats = userData.alias("stats")
            val aliasedJoin = Join(towns)
                .innerJoin(people, { towns[cities.id] }, { people[users.cityId] })
                .innerJoin(stats, { people[users.id] }, { stats[userData.user_id] })
            val query = aliasedJoin.selectAll().where { towns[cities.name] eq "Munich" }
            assertTrue { query.count() > 0L }

            aliasedJoin.delete(stats) { towns[cities.name] eq "Munich" }
            assertEquals(0, query.count())
        }
    }

    @Test
    fun testDeleteWithJoinQuery() {
        withCitiesAndUsers(exclude = listOf(TestDB.SQLITE)) { _, users, userData ->
            val singleJoinQuery = userData.joinQuery(
                on = { userData.user_id eq it[users.id] },
                joinPart = { users.selectAll().where { users.cityId eq 2 } }
            )
            val joinCount = singleJoinQuery.selectAll().count()
            assertTrue { joinCount > 0L }
            val joinCountWithCondition = singleJoinQuery.selectAll().where {
                singleJoinQuery.lastQueryAlias!![users.name] like "%ey"
            }.count()
            assertTrue { joinCountWithCondition > 0L }

            singleJoinQuery.delete(userData) { singleJoinQuery.lastQueryAlias!![users.name] like "%ey" }
            assertEquals(joinCount - joinCountWithCondition, singleJoinQuery.selectAll().count())
        }
    }

    @Test
    fun testDeleteWithJoinAndLimit() {
        withCitiesAndUsers(exclude = TestDB.ALL - TestDB.SQLSERVER - TestDB.ORACLE) { _, users, userData ->
            val join = users innerJoin userData
            val query = join.selectAll().where { userData.user_id eq "smth" }
            val originalCount = query.count()
            assertTrue { originalCount > 1 }

            join.delete(userData, limit = 1) { userData.user_id eq "smth" }
            assertEquals(originalCount - 1, query.count())
        }
    }

    @Test
    fun testDeleteIgnoreWithJoin() {
        withCitiesAndUsers(exclude = TestDB.ALL - TestDB.ALL_MYSQL_MARIADB) { _, users, userData ->
            val join = users innerJoin userData
            val query = join.selectAll().where { users.id eq "smth" }
            assertTrue { query.count() > 0 }

            expectException<ExposedSQLException> {
                // a regular delete throws SQLIntegrityConstraintViolationException because UserData reference Users
                // Cannot delete or update a parent row: a foreign key constraint fails
                join.delete(users, userData) { users.id eq "smth" }
            }

            expect(2) {
                // the error is now ignored so parent rows are skipped but child rows are deleted
                join.delete(users, userData, ignore = true) { users.id eq "smth" }
            }
            assertEquals(0, query.count())
        }
    }
}
