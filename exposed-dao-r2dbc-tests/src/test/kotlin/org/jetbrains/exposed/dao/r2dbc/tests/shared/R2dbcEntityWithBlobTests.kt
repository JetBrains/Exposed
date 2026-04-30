package org.jetbrains.exposed.dao.r2dbc.tests.shared

import org.jetbrains.exposed.r2dbc.dao.R2dbcEntity
import org.jetbrains.exposed.r2dbc.dao.R2dbcEntityClass
import org.jetbrains.exposed.r2dbc.dao.flushCache
import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.IdTable
import org.jetbrains.exposed.v1.core.statements.api.ExposedBlob
import org.jetbrains.exposed.v1.r2dbc.tests.R2dbcDatabaseTestsBase
import org.jetbrains.exposed.v1.r2dbc.tests.shared.assertEquals
import org.junit.jupiter.api.assertNull
import java.util.UUID
import kotlin.test.Test

class R2dbcEntityWithBlobTests : R2dbcDatabaseTestsBase() {

    object BlobTable : IdTable<String>("YTable") {
        override val id: Column<EntityID<String>> = varchar("uuid", 36).entityId().clientDefault {
            EntityID(UUID.randomUUID().toString(), EntityTestsData.YTable)
        }

        val blob = blob("content").nullable()

        override val primaryKey = PrimaryKey(id)
    }

    class BlobEntity(id: EntityID<String>) : R2dbcEntity<String>(id) {
        var content by BlobTable.blob

        companion object : R2dbcEntityClass<String, BlobEntity>(BlobTable)
    }

    @Test
    fun testBlobField() {
        withTables(BlobTable) {
            val y1 = BlobEntity.new {
                content = ExposedBlob("foo".toByteArray())
            }

            flushCache()
            var y2 = BlobEntity.reload(y1)!!
            assertEquals(String(y2.content!!.bytes), "foo")

            y2.content = null
            flushCache()
            y2 = BlobEntity.reload(y1)!!
            assertNull(y2.content)

            y2.content = ExposedBlob("foo2".toByteArray())
            flushCache()
        }
    }
}
