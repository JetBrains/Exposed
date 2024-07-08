package org.jetbrains.exposed.sql.tests.shared.entities

import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.Op
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.tests.DatabaseTestsBase
import org.jetbrains.exposed.sql.tests.shared.assertEquals
import org.jetbrains.exposed.sql.tests.shared.entities.EntityTests.Parent.Companion.transform
import org.junit.Test
import java.math.BigDecimal

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

            val row = TransformationsTable.selectAll()
                .where(Op.TRUE)
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

            val row = NullableTransformationsTable.selectAll()
                .where(Op.TRUE)
                .first()

            assertEquals("transformed-stuff", row[NullableTransformationsTable.value])
        }
    }

    @Test
    fun `set and get nullable value - while absent`() {
        withTables(NullableTransformationsTable) {
            val entity = NullableTransformationEntity.new {}

            assertEquals(null, entity.value)

            val row = NullableTransformationsTable.selectAll()
                .where(Op.TRUE)
                .first()

            assertEquals(null, row[NullableTransformationsTable.value])
        }
    }

    object TableWithTransforms : IntIdTable() {
        val value = varchar("value", 50)
            .transform(toReal = { it.toBigDecimal() }, toColumn = { it.toString() })
    }

    class TableWithTransform(id: EntityID<Int>) : IntEntity(id) {
        companion object : IntEntityClass<TableWithTransform>(TableWithTransforms)
        var value by TableWithTransforms.value.transform(toReal = { it.toInt() }, toColumn = { it.toBigDecimal() })
    }

    @Test
    fun testDaoTransformWithDslTransform() {
        withTables(TableWithTransforms) {
            TableWithTransform.new {
                value = 10
            }

            // Correct DAO value
            assertEquals(10, TableWithTransform.all().first().value)

            // Correct DSL value
            assertEquals(BigDecimal(10), TableWithTransforms.selectAll().first()[TableWithTransforms.value])
        }
    }
}
