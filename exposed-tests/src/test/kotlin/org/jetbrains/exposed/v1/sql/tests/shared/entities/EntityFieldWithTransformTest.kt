package org.jetbrains.exposed.v1.sql.tests.shared.entities

import org.jetbrains.exposed.v1.dao.IntEntity
import org.jetbrains.exposed.v1.dao.IntEntityClass
import org.jetbrains.exposed.v1.dao.id.EntityID
import org.jetbrains.exposed.v1.dao.id.IntIdTable
import org.jetbrains.exposed.v1.sql.Op
import org.jetbrains.exposed.v1.sql.selectAll
import org.jetbrains.exposed.v1.sql.tests.DatabaseTestsBase
import org.jetbrains.exposed.v1.sql.tests.shared.assertEquals
import org.jetbrains.exposed.v1.sql.tests.shared.assertTrue
import org.junit.Test
import java.math.BigDecimal
import kotlin.random.Random

class EntityFieldWithTransformTest : DatabaseTestsBase() {

    object TransformationsTable : IntIdTable() {
        val value = varchar("value", 50)
    }

    object NullableTransformationsTable : IntIdTable() {
        val value = varchar("nullable", 50).nullable()
    }

    class TransformationEntity(id: EntityID<Int>) : IntEntity(id) {
        companion object : IntEntityClass<TransformationEntity>(TransformationsTable)

        var value by TransformationsTable.value.transform(
            unwrap = { "transformed-$it" },
            wrap = { it.replace("transformed-", "") }
        )
    }

    class NullableTransformationEntity(id: EntityID<Int>) : IntEntity(id) {
        companion object : IntEntityClass<NullableTransformationEntity>(NullableTransformationsTable)

        var value by NullableTransformationsTable.value.transform(
            unwrap = { "transformed-$it" },
            wrap = { it?.replace("transformed-", "") }
        )
    }

    @Test
    fun testSetAndGetValue() {
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
    fun testSetAndGetNullableValueWhilePresent() {
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
    fun testSetAndGetNullableValueWhileAbsent() {
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
            .transform(wrap = { it.toBigDecimal() }, unwrap = { it.toString() })
    }

    class TableWithTransform(id: EntityID<Int>) : IntEntity(id) {
        companion object : IntEntityClass<TableWithTransform>(TableWithTransforms)

        var value by TableWithTransforms.value.transform(wrap = { it.toInt() }, unwrap = { it.toBigDecimal() })
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

    class ChainedTransformationEntity(id: EntityID<Int>) : IntEntity(id) {
        companion object : IntEntityClass<ChainedTransformationEntity>(TransformationsTable)

        var value by TransformationsTable.value
            .transform(
                unwrap = { "transformed-$it" },
                wrap = { it.replace("transformed-", "") }
            )
            .transform(
                unwrap = { if (it.length > 5) it.slice(0..4) else it },
                wrap = { it }
            )
    }

    @Test
    fun testChainedTransformation() {
        withTables(TransformationsTable) {
            ChainedTransformationEntity.new {
                value = "qwertyuiop"
            }

            assertEquals("qwert", ChainedTransformationEntity.all().first().value)
        }
    }

    class MemoizedChainedTransformationEntity(id: EntityID<Int>) : IntEntity(id) {
        companion object : IntEntityClass<MemoizedChainedTransformationEntity>(TransformationsTable)

        var value by TransformationsTable.value
            .transform(
                unwrap = { "transformed-$it" },
                wrap = { it.replace("transformed-", "") }
            )
            .memoizedTransform(
                unwrap = { it + Random(10).nextInt(0, 100) },
                wrap = { it }
            )
    }

    @Test
    fun testMemoizedChainedTransformation() {
        withTables(TransformationsTable) {
            MemoizedChainedTransformationEntity.new {
                value = "value#"
            }

            val entity = MemoizedChainedTransformationEntity.all().first()

            val firstRead = entity.value
            assertTrue(firstRead.startsWith("value#"))
            assertEquals(firstRead, entity.value)
        }
    }
}
