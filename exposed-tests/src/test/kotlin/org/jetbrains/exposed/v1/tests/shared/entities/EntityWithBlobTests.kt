package org.jetbrains.exposed.v1.tests.shared.entities

import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.IdTable
import org.jetbrains.exposed.v1.core.statements.api.ExposedBlob
import org.jetbrains.exposed.v1.dao.Entity
import org.jetbrains.exposed.v1.dao.EntityClass
import org.jetbrains.exposed.v1.dao.flushCache
import org.jetbrains.exposed.v1.tests.DatabaseTestsBase
import org.jetbrains.exposed.v1.tests.shared.assertEquals
import org.jetbrains.exposed.v1.tests.shared.entities.EntityTestsData.YTable
import org.junit.jupiter.api.Test
import java.util.*
import kotlin.test.assertNull

class EntityWithBlobTests : DatabaseTestsBase() {

    object BlobTable : IdTable<String>("YTable") {
        override val id: Column<EntityID<String>> = varchar("uuid", 36).entityId().clientDefault {
            EntityID(UUID.randomUUID().toString(), YTable)
        }

        val blob = blob("content").nullable()

        override val primaryKey = PrimaryKey(id)
    }

    class BlobEntity(id: EntityID<String>) : Entity<String>(id) {
        var content by BlobTable.blob

        companion object : EntityClass<String, BlobEntity>(BlobTable)
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
