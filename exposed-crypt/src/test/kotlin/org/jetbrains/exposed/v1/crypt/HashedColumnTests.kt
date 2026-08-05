package org.jetbrains.exposed.v1.crypt

import org.jetbrains.exposed.v1.core.dao.id.IntIdTable
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.insertAndGetId
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import org.jetbrains.exposed.v1.tests.DatabaseTestsBase
import org.jetbrains.exposed.v1.tests.shared.assertEquals
import org.jetbrains.exposed.v1.tests.shared.assertFalse
import org.jetbrains.exposed.v1.tests.shared.assertTrue
import org.junit.jupiter.api.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HashedColumnTests : DatabaseTestsBase() {
    private companion object {
        const val TEST_BCRYPT_STRENGTH = 4
        const val TEST_ARGON2_MEMORY = 512
        const val TEST_ARGON2_ITERATIONS = 1
        const val TEST_PBKDF2_ITERATIONS = 1000
        const val TEST_SCRYPT_CPU_COST = 1024

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
    fun testEachAlgorithmVerifiesItsOwnHashesAndSaltsThem() {
        val hashers = arrayOf(
            "BCRYPT" to BCryptHasher(strength = TEST_BCRYPT_STRENGTH),
            "ARGON2" to Argon2Hasher(memory = TEST_ARGON2_MEMORY, iterations = TEST_ARGON2_ITERATIONS),
            "PBKDF2" to Pbkdf2Hasher(iterations = TEST_PBKDF2_ITERATIONS),
            "SCRYPT" to SCryptHasher(cpuCost = TEST_SCRYPT_CPU_COST)
        )

        for ((algorithm, hasher) in hashers) {
            val hashed = hasher.hash(PASSWORD)

            assertTrue(hashed.matches(PASSWORD), "$algorithm failed to verify the value it hashed.")
            assertFalse(hashed.matches(WRONG_PASSWORD), "$algorithm verified a value it did not hash.")
            assertFalse(PASSWORD in hashed.encodedValue, "$algorithm leaked the plaintext into its output.")
            assertNotEquals(
                hashed,
                hasher.hash(PASSWORD),
                "$algorithm produced the same hash twice, so it does not salt each value."
            )
        }
    }

    @Test
    fun testStoredHashIsNotHashedAgainWhenWrittenBack() {
        val hasher = BCryptHasher(strength = TEST_BCRYPT_STRENGTH)
        val tester = object : IntIdTable(TESTER_TABLE) {
            val password = text("password").hashed(hasher)
        }

        withTables(tester) {
            val originalId = tester.insertAndGetId { it[password] = hasher.hash(PASSWORD) }
            val original = tester.selectAll().where { tester.id eq originalId }.single()[tester.password]

            val copyId = tester.insertAndGetId { it[password] = original }
            val copy = tester.selectAll().where { tester.id eq copyId }.single()[tester.password]

            assertEquals(original.encodedValue, copy.encodedValue)
            assertTrue(copy.matches(PASSWORD))
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

            val newPassword = "even-more-s3cret"
            tester.update({ tester.id eq id }) { it[password] = hasher.hash(newPassword) }

            val stored = tester.selectAll().where { tester.id eq id }.single()[tester.password]
            assertTrue(stored.matches(newPassword))
            assertFalse(stored.matches(PASSWORD))
        }
    }

    @Test
    fun testHashedWrapsAHashProducedElsewhereWithoutRehashingIt() {
        val foreignHash = BCryptHasher(strength = TEST_BCRYPT_STRENGTH + 1).hash(PASSWORD).encodedValue

        val hasher = BCryptHasher(strength = TEST_BCRYPT_STRENGTH)
        val tester = object : IntIdTable(TESTER_TABLE) {
            val password = text("password").hashed(hasher)
        }

        withTables(tester) {
            tester.insert { it[password] = Hashed(hasher, foreignHash) }

            val stored = tester.selectAll().single()[tester.password]
            assertEquals(foreignHash, stored.encodedValue)
            assertTrue(stored.matches(PASSWORD))
        }
    }

    private class ReversingHasher : Hasher {
        override fun hash(plainText: String): Hashed = Hashed(this, plainText.reversed())

        override fun matches(plainText: String, encodedValue: String): Boolean = plainText.reversed() == encodedValue
    }

    @Test
    fun testCustomHasherIsUsableAsAColumn() {
        val hasher = ReversingHasher()
        val tester = object : IntIdTable(TESTER_TABLE) {
            val password = text("password").hashed(hasher)
        }

        withTables(tester) {
            tester.insert { it[password] = hasher.hash(PASSWORD) }

            val stored = tester.selectAll().single()[tester.password]
            assertEquals(PASSWORD.reversed(), stored.encodedValue)
            assertTrue(stored.matches(PASSWORD))
            assertFalse(stored.matches(WRONG_PASSWORD))
        }
    }
}
