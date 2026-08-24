package org.jetbrains.exposed.v1.crypt

import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.IntIdTable
import org.jetbrains.exposed.v1.dao.IntEntity
import org.jetbrains.exposed.v1.dao.IntEntityClass
import org.jetbrains.exposed.v1.dao.entityCache
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.tests.DatabaseTestsBase
import org.jetbrains.exposed.v1.tests.MISSING_R2DBC_TEST
import org.jetbrains.exposed.v1.tests.shared.assertFalse
import org.jetbrains.exposed.v1.tests.shared.assertTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull

@Tag(MISSING_R2DBC_TEST)
class HashedColumnDaoTests : DatabaseTestsBase() {
    private companion object {
        const val TEST_BCRYPT_STRENGTH = 4

        const val PASSWORD = "s3cret"
        const val WRONG_PASSWORD = "s3cr3t"
        const val NEW_PASSWORD = "even-more-s3cret"
        const val RECOVERY_CODE = "r3covery"

        val hasher = BCryptHasher(strength = TEST_BCRYPT_STRENGTH)
    }

    object TestTable : IntIdTable("HashedDaoTester") {
        val password = text("password").hashed(hasher)
        val recoveryCode = varchar("recovery_code", 60).nullable().hashed(hasher)
    }

    class TestEntity(id: EntityID<Int>) : IntEntity(id) {
        companion object : IntEntityClass<TestEntity>(TestTable)

        var password by TestTable.password
        var recoveryCode by TestTable.recoveryCode
    }

    @Test
    fun testHashedColumnsWithDao() {
        withTables(TestTable) {
            val created = TestEntity.new {
                password = hasher.hash(PASSWORD)
                recoveryCode = hasher.hash(RECOVERY_CODE)
            }
            val id = created.id

            val cached = assertNotNull(entityCache.find(TestEntity, id))
            assertTrue(cached.password.matches(PASSWORD))
            assertFalse(cached.password.matches(WRONG_PASSWORD))
            assertTrue(assertNotNull(cached.recoveryCode).matches(RECOVERY_CODE))

            entityCache.clear()
            val reloaded = assertNotNull(TestEntity.findById(id))
            assertTrue(reloaded.password.matches(PASSWORD))
            assertFalse(reloaded.password.matches(WRONG_PASSWORD))
            assertTrue(assertNotNull(reloaded.recoveryCode).matches(RECOVERY_CODE))

            val stored = TestTable.selectAll().single()[TestTable.password].encodedValue
            assertNotEquals(PASSWORD, stored, "The plaintext reached the database.")

            reloaded.password = hasher.hash(NEW_PASSWORD)

            entityCache.clear()
            val updated = assertNotNull(TestEntity.findById(id))
            assertTrue(updated.password.matches(NEW_PASSWORD))
            assertFalse(updated.password.matches(PASSWORD))
            assertNotEquals(stored, updated.password.encodedValue)
        }
    }

    @Test
    fun testHashedColumnsWithDaoUsingTheColumnsOwnHasher() {
        withTables(TestTable) {
            val created = TestEntity.new {
                password = TestTable.password.hash(PASSWORD)
                recoveryCode = TestTable.recoveryCode.hash(RECOVERY_CODE)
            }

            entityCache.clear()
            val reloaded = assertNotNull(TestEntity.findById(created.id))
            assertTrue(reloaded.password.matches(PASSWORD))
            assertFalse(reloaded.password.matches(WRONG_PASSWORD))
            assertTrue(assertNotNull(reloaded.recoveryCode).matches(RECOVERY_CODE))
        }
    }
}
