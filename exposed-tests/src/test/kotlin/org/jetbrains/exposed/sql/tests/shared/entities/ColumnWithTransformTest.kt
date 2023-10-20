package org.jetbrains.exposed.sql.tests.shared.entities

import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.Op
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.tests.DatabaseTestsBase
import org.jetbrains.exposed.sql.tests.shared.assertEquals
import org.junit.Test

object TransformationsTable : IntIdTable() {
    val value = varchar("value", 50)
}

object NullableTransformationsTable : IntIdTable() {
    val value = varchar("nullable", 50).nullable()
}

class TransformationEntity(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<TransformationEntity>(TransformationsTable)
    var value by TransformationsTable.value.transform(
        toColumn = { "transformed-$it" },
        toReal = { it.replace("transformed-", "") }
    )
}

class NullableTransformationEntity(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<NullableTransformationEntity>(NullableTransformationsTable)
    var value by NullableTransformationsTable.value.transform(
        toColumn = { "transformed-$it" },
        toReal = { it?.replace("transformed-", "") }
    )
}

class ColumnWithTransformTest : DatabaseTestsBase() {

    @Test
    fun `set and get value`() {
        withTables(TransformationsTable) {
            val entity = TransformationEntity.new {
                value = "stuff"
            }

            assertEquals("stuff", entity.value)

            val row = TransformationsTable.select(Op.TRUE)
                .first()

            assertEquals("transformed-stuff", row[TransformationsTable.value])
        }
    }

    @Test
    fun `set and get nullable value - while present`() {
        withTables(NullableTransformationsTable) {
            val entity = NullableTransformationEntity.new {
                value = "stuff"
            }

            assertEquals("stuff", entity.value)

            val row = NullableTransformationsTable.select(Op.TRUE)
                .first()

            assertEquals("transformed-stuff", row[NullableTransformationsTable.value])
        }
    }

    @Test
    fun `set and get nullable value - while absent`() {
        withTables(NullableTransformationsTable) {
            val entity = NullableTransformationEntity.new {}

            assertEquals(null, entity.value)

            val row = NullableTransformationsTable.select(Op.TRUE)
                .first()

            assertEquals(null, row[NullableTransformationsTable.value])
        }
    }
}
