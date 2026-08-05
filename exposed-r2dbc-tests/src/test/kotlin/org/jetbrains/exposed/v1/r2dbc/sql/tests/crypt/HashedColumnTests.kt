package org.jetbrains.exposed.v1.r2dbc.sql.tests.crypt

import kotlinx.coroutines.flow.single
import org.jetbrains.exposed.v1.core.dao.id.IntIdTable
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.crypt.BCryptHasher
import org.jetbrains.exposed.v1.crypt.hashed
import org.jetbrains.exposed.v1.r2dbc.insertAndGetId
import org.jetbrains.exposed.v1.r2dbc.selectAll
import org.jetbrains.exposed.v1.r2dbc.tests.R2dbcDatabaseTestsBase
import org.jetbrains.exposed.v1.r2dbc.update
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HashedColumnTests : R2dbcDatabaseTestsBase() {
    private companion object {
        const val TEST_BCRYPT_STRENGTH = 4

        const val PASSWORD = "s3cret"
        const val WRONG_PASSWORD = "s3cr3t"

        const val TESTER_TABLE = "HashedTester"
    }

    @Test
    fun testHashedColumnStoresHashAndVerifiesPlainText() {
        val hasher = BCryptHasher(strength = TEST_BCRYPT_STRENGTH)
        val tester = object : IntIdTable(TESTER_TABLE) {
            val password = text("password").hashed(hasher)
            val recoveryCode = varchar("recovery_code", 60).nullable().hashed(hasher)
        }

        val rawTester = object : IntIdTable(TESTER_TABLE) {
            val password = text("password")
            val recoveryCode = varchar("recovery_code", 60).nullable()
        }

        withTables(tester) {
            val id = tester.insertAndGetId {
                it[password] = hasher.hash(PASSWORD)
                it[recoveryCode] = null
            }

            val raw = rawTester.selectAll().where { rawTester.id eq id }.single()
            assertNotEquals(PASSWORD, raw[rawTester.password], "The plaintext reached the database.")
            assertTrue(hasher.matches(PASSWORD, raw[rawTester.password]))
            assertNull(raw[rawTester.recoveryCode])

            val stored = tester.selectAll().where { tester.id eq id }.single()
            assertTrue(stored[tester.password].matches(PASSWORD))
            assertFalse(stored[tester.password].matches(WRONG_PASSWORD))
            assertNull(stored[tester.recoveryCode])
        }
    }

    @Test
    fun testUpdateReplacesStoredHash() {
        val hasher = BCryptHasher(strength = TEST_BCRYPT_STRENGTH)
        val tester = object : IntIdTable(TESTER_TABLE) {
            val password = text("password").hashed(hasher)
        }

        withTables(tester) {
            val id = tester.insertAndGetId { it[password] = hasher.hash(PASSWORD) }
            val original = tester.selectAll().where { tester.id eq id }.single()[tester.password]

            val newPassword = "even-more-s3cret"
            tester.update({ tester.id eq id }) { it[password] = hasher.hash(newPassword) }

            val stored = tester.selectAll().where { tester.id eq id }.single()[tester.password]
            assertTrue(stored.matches(newPassword))
            assertFalse(stored.matches(PASSWORD))
            assertNotEquals(original.encodedValue, stored.encodedValue)

            tester.update({ tester.id eq id }) { it[password] = stored }
            val rewritten = tester.selectAll().where { tester.id eq id }.single()[tester.password]
            assertEquals(stored.encodedValue, rewritten.encodedValue)
            assertTrue(rewritten.matches(newPassword))
        }
    }
}
