package org.jetbrains.exposed.crypt

import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.insertAndGetId
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.tests.DatabaseTestsBase
import org.jetbrains.exposed.sql.tests.shared.assertEquals
import org.jetbrains.exposed.sql.update
import org.junit.Test
import kotlin.test.assertEquals

class EncryptedColumnTests : DatabaseTestsBase() {
    @Test
    fun testOutputLengthOfEncryption() {
        fun testSize(algorithm: String, encryptor: Encryptor, str: String) =
            assertEquals(
                encryptor.maxColLength(str.toByteArray().size),
                encryptor.encrypt(str).toByteArray().size,
                "Failed to calculate length of $algorithm's output."
            )

        val encryptors = arrayOf(
            "AES_256_PBE_GCM" to Algorithms.AES_256_PBE_GCM("passwd", "12345678"),
            "AES_256_PBE_CBC" to Algorithms.AES_256_PBE_CBC("passwd", "12345678"),
            "BLOW_FISH" to Algorithms.BLOW_FISH("sadsad"),
            "TRIPLE_DES" to Algorithms.TRIPLE_DES("1".repeat(24))
        )
        val testStrings = arrayOf("1", "2".repeat(10), "3".repeat(31), "4".repeat(1001), "5".repeat(5391))

        for ((algorithm, encryptor) in encryptors) {
            for (testStr in testStrings) {
                testSize(algorithm, encryptor, testStr)
            }
        }
    }

    @Test
    fun testEncryptedColumnTypeWithAString() {
        val stringTable = object : IntIdTable("StringTable") {
            val name = encryptedVarchar("name", 80, Algorithms.AES_256_PBE_CBC("passwd", "5c0744940b5c369b"))
            val city = encryptedBinary("city", 80, Algorithms.AES_256_PBE_GCM("passwd", "5c0744940b5c369b"))
            val address = encryptedVarchar("address", 100, Algorithms.BLOW_FISH("key"))
            val age = encryptedVarchar("age", 100, Algorithms.TRIPLE_DES("1".repeat(24)))
        }

        withTables(stringTable) {
            val id1 = stringTable.insertAndGetId {
                it[name] = "testName"
                it[city] = "testCity".toByteArray()
                it[address] = "testAddress"
                it[age] = "testAge"
            }

            assertEquals(1L, stringTable.selectAll().count())

            assertEquals("testName", stringTable.selectAll().where { stringTable.id eq id1 }.first()[stringTable.name])
            assertEquals("testCity", String(stringTable.selectAll().where { stringTable.id eq id1 }.first()[stringTable.city]))
            assertEquals("testAddress", stringTable.selectAll().where { stringTable.id eq id1 }.first()[stringTable.address])
            assertEquals("testAge", stringTable.selectAll().where { stringTable.id eq id1 }.first()[stringTable.age])
        }
    }

    @Test
    fun testUpdateEncryptedColumnType() {
        val stringTable = object : IntIdTable("StringTable") {
            val name = encryptedVarchar("name", 100, Algorithms.AES_256_PBE_GCM("passwd", "12345678"))
            val city = encryptedBinary("city", 100, Algorithms.AES_256_PBE_CBC("passwd", "12345678"))
            val address = encryptedVarchar("address", 100, Algorithms.BLOW_FISH("key"))
        }

        withTables(stringTable) {
            val id = stringTable.insertAndGetId {
                it[name] = "TestName"
                it[city] = "TestCity".toByteArray()
                it[address] = "TestAddress"
            }

            val updatedName = "TestName2"
            val updatedCity = "TestCity2"
            val updatedAddress = "TestAddress2"
            stringTable.update({ stringTable.id eq id }) {
                it[name] = updatedName
                it[city] = updatedCity.toByteArray()
                it[address] = updatedAddress
            }

            assertEquals(updatedName, stringTable.selectAll().where { stringTable.id eq id }.single()[stringTable.name])
            assertEquals(updatedCity, String(stringTable.selectAll().where { stringTable.id eq id }.single()[stringTable.city]))
            assertEquals(updatedAddress, stringTable.selectAll().where { stringTable.id eq id }.single()[stringTable.address])
        }
    }
}
