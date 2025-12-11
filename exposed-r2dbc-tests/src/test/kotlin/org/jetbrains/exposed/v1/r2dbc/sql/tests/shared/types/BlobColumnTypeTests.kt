package org.jetbrains.exposed.v1.r2dbc.sql.tests.shared.types

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.single
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.alias
import org.jetbrains.exposed.v1.core.blobParam
import org.jetbrains.exposed.v1.core.dao.id.IntIdTable
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.statements.api.ExposedBlob
import org.jetbrains.exposed.v1.r2dbc.*
import org.jetbrains.exposed.v1.r2dbc.tests.R2dbcDatabaseTestsBase
import org.jetbrains.exposed.v1.r2dbc.tests.TestDB
import org.jetbrains.exposed.v1.r2dbc.tests.shared.assertEquals
import org.jetbrains.exposed.v1.r2dbc.tests.shared.assertTrue
import org.jetbrains.exposed.v1.r2dbc.tests.shared.expectException
import org.junit.jupiter.api.Test
import java.nio.charset.Charset
import kotlin.random.Random

class BlobColumnTypeTests : R2dbcDatabaseTestsBase() {
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
                    expectException<ExposedR2dbcException> {
                        SchemaUtils.create(testTable)
                    }
                }

                else -> {
                    org.jetbrains.exposed.v1.r2dbc.SchemaUtils.create(testTable)

                    testTable.insert {
                        it[number] = 1
                    }
                    assertEquals(defaultBlobStr, String(testTable.selectAll().first()[testTable.blobWithDefault].bytes))

                    org.jetbrains.exposed.v1.r2dbc.SchemaUtils.drop(testTable)
                }
            }
        }
    }
}
