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

object TransformTables {
    object Transformations : IntIdTable() {
        val value = varchar("value", 50)
    }
    object NullableTransformations: IntIdTable() {
        val value = varchar("nullable", 50).nullable()
    }
    class Transformation(id: EntityID<Int>) : IntEntity(id) {
        companion object : IntEntityClass<Transformation>(Transformations)
        var value by Transformations.value.transform(
            toColumn = { "transformed-$it" },
            toReal = { it.replace("transformed-", "") }
        )
    }
    class NullableTransformation(id: EntityID<Int>) : IntEntity(id) {
        companion object : IntEntityClass<NullableTransformation>(NullableTransformations)
        var value by NullableTransformations.value.transform(
            toColumn = { "transformed-$it" },
            toReal = { it?.replace("transformed-", "") }
        )
    }
}

class ColumnWithTransformTest: DatabaseTestsBase() {

    @Test fun `set and get value`() {
        withTables(TransformTables.Transformations) {
            val entity = TransformTables.Transformation.new {
                value = "stuff"
            }

            assertEquals("stuff", entity.value)

            val row = TransformTables.Transformations.select(Op.TRUE)
                .first()

            assertEquals("transformed-stuff", row[TransformTables.Transformations.value])
        }
    }

    @Test fun `set and get nullable value - while present`() {
        withTables(TransformTables.NullableTransformations) {
            val entity = TransformTables.NullableTransformation.new {
                value = "stuff"
            }

            assertEquals("stuff", entity.value)

            val row = TransformTables.NullableTransformations.select(Op.TRUE)
                .first()

            assertEquals("transformed-stuff", row[TransformTables.NullableTransformations.value])
        }
    }

    @Test fun `set and get nullable value - while absent`() {
        withTables(TransformTables.NullableTransformations) {
            val entity = TransformTables.NullableTransformation.new {}

            assertEquals(null, entity.value)

            val row = TransformTables.NullableTransformations.select(Op.TRUE)
                .first()

            assertEquals(null, row[TransformTables.NullableTransformations.value])
        }
    }
}
