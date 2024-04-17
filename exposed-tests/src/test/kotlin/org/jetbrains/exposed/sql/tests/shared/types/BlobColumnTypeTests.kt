package org.jetbrains.exposed.sql.tests.shared.types

import junit.framework.TestCase.assertEquals
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.alias
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.statements.api.ExposedBlob
import org.jetbrains.exposed.sql.tests.DatabaseTestsBase
import org.junit.Test
import java.nio.charset.Charset

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
}
