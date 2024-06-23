package org.jetbrains.exposed.sql.tests.shared.types

import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.exceptions.ExposedSQLException
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.alias
import org.jetbrains.exposed.sql.blobParam
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.statements.api.ExposedBlob
import org.jetbrains.exposed.sql.tests.DatabaseTestsBase
import org.jetbrains.exposed.sql.tests.TestDB
import org.jetbrains.exposed.sql.tests.currentDialectTest
import org.jetbrains.exposed.sql.tests.shared.assertEquals
import org.jetbrains.exposed.sql.tests.shared.assertTrue
import org.jetbrains.exposed.sql.tests.shared.expectException
import org.jetbrains.exposed.sql.vendors.PostgreSQLDialect
import org.junit.Test
import java.nio.charset.Charset
import kotlin.random.Random
import kotlin.test.assertContentEquals

class BlobColumnTypeTests : DatabaseTestsBase() {
    object BlobTable : IntIdTable("test-blob") {
        val content = blob("content")
    }

    @Test
    fun testWriteAndReadBlobValueViaAlias() {
        withTables(BlobTable) {
            val sampleData = "test-sample-data"
            BlobTable.insert {
                it[content] = ExposedBlob(sampleData.toByteArray())
            }

            val alias = BlobTable.content.alias("content_column")
            val content = BlobTable.select(alias).single()[alias].bytes.toString(Charset.defaultCharset())
            assertEquals(content, sampleData)
        }
    }

    @Test
    fun testBlob() {
        withTables(BlobTable) {
            val shortBytes = "Hello there!".toByteArray()
            val longBytes = Random.nextBytes(1024)
            val shortBlob = ExposedBlob(shortBytes)
            val longBlob = ExposedBlob(longBytes)

            val id1 = BlobTable.insert {
                it[content] = shortBlob
            } get (BlobTable.id)

            val id2 = BlobTable.insert {
                it[content] = longBlob
            } get (BlobTable.id)

            val id3 = BlobTable.insert {
                it[content] = blobParam(ExposedBlob(shortBytes))
            } get (BlobTable.id)

            val readOn1 = BlobTable.selectAll().where { BlobTable.id eq id1 }.first()[BlobTable.content]
            val text1 = String(readOn1.bytes)
            val text2 = readOn1.inputStream.bufferedReader().readText()

            assertEquals("Hello there!", text1)
            assertEquals("Hello there!", text2)

            val readOn2 = BlobTable.selectAll().where { BlobTable.id eq id2 }.first()[BlobTable.content]
            val bytes1 = readOn2.bytes
            val bytes2 = readOn2.inputStream.readBytes()

            assertTrue(longBytes.contentEquals(bytes1))
            assertTrue(longBytes.contentEquals(bytes2))

            val bytes3 = BlobTable.selectAll().where { BlobTable.id eq id3 }.first()[BlobTable.content].inputStream.readBytes()
            assertTrue(shortBytes.contentEquals(bytes3))
        }
    }

    @Test
    fun testBlobDefault() {
        val defaultBlobStr = "test"
        val defaultBlob = ExposedBlob(defaultBlobStr.encodeToByteArray())

        val testTable = object : Table("TestTable") {
            val number = integer("number")
            val blobWithDefault = blob("blobWithDefault").default(defaultBlob)
        }

        withDb { testDb ->
            when (testDb) {
                TestDB.MYSQL_V5, TestDB.MYSQL_V8 -> {
                    expectException<ExposedSQLException> {
                        SchemaUtils.create(testTable)
                    }
                }

                else -> {
                    SchemaUtils.create(testTable)

                    testTable.insert {
                        it[number] = 1
                    }
                    assertEquals(defaultBlobStr, String(testTable.selectAll().first()[testTable.blobWithDefault].bytes))

                    SchemaUtils.drop(testTable)
                }
            }
        }
    }

    @Test
    fun testBlobAsOid() {
        val defaultBytes = "test".toByteArray()
        val defaultBlob = ExposedBlob(defaultBytes)
        val tester = object : Table("blob_tester") {
            val blobCol = blob("blob_col", useObjectIdentifier = true).default(defaultBlob)
        }

        withDb {
            if (currentDialectTest !is PostgreSQLDialect) {
                expectException<IllegalStateException> {
                    SchemaUtils.create(tester)
                }
            } else {
                assertEquals("oid", tester.blobCol.descriptionDdl().split(" ")[1])
                SchemaUtils.create(tester)

                tester.insert {}

                val result1 = tester.selectAll().single()[tester.blobCol]
                assertContentEquals(defaultBytes, result1.bytes)

                tester.insert {
                    defaultBlob.inputStream.reset()
                    it[blobCol] = defaultBlob
                }
                tester.insert {
                    defaultBlob.inputStream.reset()
                    it[blobCol] = blobParam(defaultBlob, useObjectIdentifier = true)
                }

                val result2 = tester.selectAll()
                assertEquals(3, result2.count())
                assertTrue(result2.all { it[tester.blobCol].bytes.contentEquals(defaultBytes) })

                SchemaUtils.drop(tester)
            }
        }
    }
}
