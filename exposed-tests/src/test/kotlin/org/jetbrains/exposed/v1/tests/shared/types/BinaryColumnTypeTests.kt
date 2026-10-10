package org.jetbrains.exposed.v1.tests.shared.types

import org.jetbrains.exposed.v1.core.StdOutSqlLogger
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.exceptions.UnexpectedValueTypeException
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.tests.DatabaseTestsBase
import org.jetbrains.exposed.v1.tests.TestDB
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class BinaryColumnTypeTests : DatabaseTestsBase() {

    /** Stands in for a decrypted value whose `toString()` would expose plaintext (EXPOSED-1012). */
    private data class Secret(val plain: String)

    private object SecretTable : Table("binary_secret") {
        val id = integer("id")
        val secret = binary("secret").transform(
            wrap = { Secret(it.toString(Charsets.UTF_8)) },
            unwrap = { it.plain.toByteArray(Charsets.UTF_8) },
        )

        override val primaryKey = PrimaryKey(id)
    }

    @Test
    fun testValueFromDBErrorDoesNotExposeTheValue() {
        withTables(excludeSettings = TestDB.ALL - TestDB.ALL_H2_V2, SecretTable) {
            val plaintext = "123-45-6789"
            SecretTable.insert {
                it[id] = 1
                it[secret] = Secret(plaintext)
            }

            val row = SecretTable.selectAll().single()
            // Setting the wrapped (Kotlin-side) value on the row makes the next read pass a `Secret`
            // instance, not a `ByteArray`, into `BasicBinaryColumnType.valueFromDB`, which then fails.
            row[SecretTable.secret] = Secret(plaintext)

            val thrown = assertFailsWith<UnexpectedValueTypeException> { row[SecretTable.secret] }
            val message = assertNotNull(thrown.message)

            assertFalse(
                message.contains(plaintext),
                "the column value must not appear in the message: $message"
            )
            assertTrue(message.contains(Secret::class.qualifiedName!!))
        }
    }

    private object BinaryTestTable : Table("binary_cast_simple") {
        val id = integer("id")
        val data = binary("data", 4)

        override val primaryKey = PrimaryKey(id)
    }

    @Test
    fun testLongerValueDoesNotMatchStoredValue() {
        withTables(excludeSettings = TestDB.ALL - TestDB.ALL_H2_V2, BinaryTestTable) {
            addLogger(StdOutSqlLogger)
            BinaryTestTable.insert {
                it[id] = 1
                it[data] = byteArrayOf(1, 2, 3, 4)
            }

            // A different value that merely starts with the same four bytes.
            val longerValue = byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8)

            val matches = BinaryTestTable.selectAll().where { BinaryTestTable.data eq longerValue }.count()

            assertEquals(
                0L,
                matches,
                "an 8-byte value matched the stored 4-byte row - the comparison argument was " +
                    "truncated to its first 4 bytes by cast(? as VARBINARY(4))"
            )
        }
    }
}
