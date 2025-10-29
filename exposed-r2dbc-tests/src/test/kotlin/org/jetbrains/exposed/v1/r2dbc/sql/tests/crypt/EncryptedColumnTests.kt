package org.jetbrains.exposed.v1.r2dbc.sql.tests.crypt

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.single
import nl.altindag.log.LogCaptor
import org.jetbrains.exposed.v1.core.dao.id.IntIdTable
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.exposedLogger
import org.jetbrains.exposed.v1.crypt.Algorithms
import org.jetbrains.exposed.v1.crypt.Encryptor
import org.jetbrains.exposed.v1.crypt.encryptedBinary
import org.jetbrains.exposed.v1.crypt.encryptedVarchar
import org.jetbrains.exposed.v1.r2dbc.insertAndGetId
import org.jetbrains.exposed.v1.r2dbc.selectAll
import org.jetbrains.exposed.v1.r2dbc.tests.R2dbcDatabaseTestsBase
import org.jetbrains.exposed.v1.r2dbc.update
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EncryptedColumnTests : R2dbcDatabaseTestsBase() {
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
            val logCaptor = LogCaptor.forName(exposedLogger.name)
            logCaptor.setLogLevelToDebug()

            val insertedStrings = listOf("testName", "testCity", "testAddress", "testAge")
            val (insertedName, insertedCity, insertedAddress, insertedAge) = insertedStrings
            val id1 = stringTable.insertAndGetId {
                it[name] = insertedName
                it[city] = insertedCity.toByteArray()
                it[address] = insertedAddress
                it[age] = insertedAge
            }

            val insertLog = logCaptor.debugLogs.single()
            assertTrue(insertLog.startsWith("INSERT "))
            assertTrue(insertedStrings.none { it in insertLog })

            logCaptor.clearLogs()
            logCaptor.resetLogLevel()
            logCaptor.close()

            assertEquals(1L, stringTable.selectAll().count())

            assertEquals(insertedName, stringTable.selectAll().where { stringTable.id eq id1 }.first()[stringTable.name])
            assertEquals(insertedCity, String(stringTable.selectAll().where { stringTable.id eq id1 }.first()[stringTable.city]))
            assertEquals(insertedAddress, stringTable.selectAll().where { stringTable.id eq id1 }.first()[stringTable.address])
            assertEquals(insertedAge, stringTable.selectAll().where { stringTable.id eq id1 }.first()[stringTable.age])
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
            val logCaptor = LogCaptor.forName(exposedLogger.name)
            logCaptor.setLogLevelToDebug()

            val insertedStrings = listOf("TestName", "TestCity", "TestAddress")
            val (insertedName, insertedCity, insertedAddress) = insertedStrings
            val id = stringTable.insertAndGetId {
                it[name] = insertedName
                it[city] = insertedCity.toByteArray()
                it[address] = insertedAddress
            }

            val insertLog = logCaptor.debugLogs.single()
            assertTrue(insertLog.startsWith("INSERT "))
            assertTrue(insertedStrings.none { it in insertLog })

            logCaptor.clearLogs()

            val updatedStrings = listOf("TestName2", "TestCity2", "TestAddress2")
            val (updatedName, updatedCity, updatedAddress) = updatedStrings
            stringTable.update({ stringTable.id eq id }) {
                it[name] = updatedName
                it[city] = updatedCity.toByteArray()
                it[address] = updatedAddress
            }

            val updateLog = logCaptor.debugLogs.single()
            assertTrue(updateLog.startsWith("UPDATE "))
            assertTrue(updatedStrings.none { it in updateLog })

            logCaptor.clearLogs()
            logCaptor.resetLogLevel()
            logCaptor.close()

            assertEquals(updatedName, stringTable.selectAll().where { stringTable.id eq id }.single()[stringTable.name])
            assertEquals(updatedCity, String(stringTable.selectAll().where { stringTable.id eq id }.single()[stringTable.city]))
            assertEquals(updatedAddress, stringTable.selectAll().where { stringTable.id eq id }.single()[stringTable.address])
        }
    }
}
