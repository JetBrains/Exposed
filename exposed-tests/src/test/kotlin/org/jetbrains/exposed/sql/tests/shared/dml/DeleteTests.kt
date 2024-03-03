package org.jetbrains.exposed.sql.tests.shared.dml

import junit.framework.TestCase.assertNull
import org.jetbrains.exposed.exceptions.ExposedSQLException
import org.jetbrains.exposed.exceptions.UnsupportedByDialectException
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.like
import org.jetbrains.exposed.sql.tests.DatabaseTestsBase
import org.jetbrains.exposed.sql.tests.TestDB
import org.jetbrains.exposed.sql.tests.currentDialectTest
import org.jetbrains.exposed.sql.tests.shared.assertEquals
import org.jetbrains.exposed.sql.tests.shared.expectException
import org.jetbrains.exposed.sql.vendors.H2Dialect
import org.jetbrains.exposed.sql.vendors.MysqlDialect
import org.jetbrains.exposed.sql.vendors.SQLiteDialect
import org.junit.Test
import kotlin.test.expect

class DeleteTests : DatabaseTestsBase() {
    private val notSupportLimit by lazy {
        val exclude = arrayListOf(
            TestDB.POSTGRESQL,
            TestDB.POSTGRESQLNG,
            TestDB.ORACLE,
            TestDB.H2_PSQL,
            TestDB.H2_ORACLE
        )
        if (!SQLiteDialect.ENABLE_UPDATE_DELETE_LIMIT) {
            exclude.add(TestDB.SQLITE)
        }
        exclude
    }

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
    fun testDeleteWithLimitAndOffset01() {
        withCitiesAndUsers(exclude = notSupportLimit) { _, _, userData ->
            userData.deleteWhere(limit = 1) { userData.value eq 20 }
            userData.select(userData.user_id, userData.value).where { userData.value eq 20 }.let {
                assertEquals(1L, it.count())
                val expected = if (currentDialectTest is H2Dialect) "smth" else "eugene"
                assertEquals(expected, it.single()[userData.user_id])
            }
        }
    }

    @Test
    fun testDeleteWithLimit02() {
        val dialects = TestDB.entries - notSupportLimit
        withCitiesAndUsers(dialects) { _, _, userData ->
            expectException<UnsupportedByDialectException> {
                userData.deleteWhere(limit = 1) {
                    userData.value eq 20
                }
            }
        }
    }
}
