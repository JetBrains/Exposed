package org.jetbrains.exposed.sql.tests.shared.dml

import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IdTable
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.tests.DatabaseTestsBase
import org.jetbrains.exposed.sql.tests.shared.assertEquals
import org.junit.Test
import java.util.*

class ColumnWithTransformTest : DatabaseTestsBase() {

    data class TransformDataHolder(val value: Int)

    class DataHolderTransformer : ColumnTransformer<Int, TransformDataHolder> {
        override fun unwrap(value: TransformDataHolder): Int = value.value
        override fun wrap(value: Int): TransformDataHolder = TransformDataHolder(value)
    }

    class DataHolderNullableTransformer : ColumnTransformer<Int?, TransformDataHolder?> {
        override fun unwrap(value: TransformDataHolder?): Int? = value?.value
        override fun wrap(value: Int?): TransformDataHolder? = value?.let { TransformDataHolder(it) }
    }

    class DataHolderNullTransformer : ColumnTransformer<Int, TransformDataHolder?> {
        override fun unwrap(value: TransformDataHolder?): Int = value?.value ?: 0
        override fun wrap(value: Int): TransformDataHolder? = if (value == 0) null else TransformDataHolder(value)
    }

    @Test
    fun testSimpleTransforms() {
        val tester = object : IntIdTable() {
            val v1 = integer("v1")
                .transform(wrap = { TransformDataHolder(it) }, unwrap = { it.value })
            val v2 = integer("v2")
                .nullable()
                .transform(wrap = { it?.let { TransformDataHolder(it) } }, unwrap = { it?.value })
            val v3 = integer("v3")
                .transform(wrap = { TransformDataHolder(it) }, unwrap = { it.value })
                .nullable()
        }

        withTables(tester) {
            val id1 = tester.insertAndGetId {
                it[v1] = TransformDataHolder(1)
                it[v2] = TransformDataHolder(2)
                it[v3] = TransformDataHolder(3)
            }
            val entry1 = tester.selectAll().where { tester.id eq id1 }.first()
            assertEquals(1, entry1[tester.v1].value)
            assertEquals(2, entry1[tester.v2]?.value)
            assertEquals(3, entry1[tester.v3]?.value)

            val id2 = tester.insertAndGetId {
                it[v1] = TransformDataHolder(1)
                it[v2] = null
                it[v3] = null
            }
            val entry2 = tester.selectAll().where { tester.id eq id2 }.first()
            assertEquals(1, entry2[tester.v1].value)
            assertEquals(null, entry2[tester.v2])
            assertEquals(null, entry2[tester.v3])
        }
    }

    @Test
    fun testNestedTransforms() {
        val tester = object : IntIdTable() {
            val v1 = integer("v1")
                .transform(DataHolderTransformer())
                .transform(wrap = { it.value.toString() }, unwrap = { TransformDataHolder(it.toInt()) })

            val v2 = integer("v2")
                .transform(DataHolderTransformer())
                .transform(wrap = { it.value.toString() }, unwrap = { TransformDataHolder(it.toInt()) })
                .nullable()

            val v3 = integer("v3")
                .transform(DataHolderTransformer())
                .nullable()
                .transform(wrap = { it?.value.toString() }, unwrap = { it?.let { it1 -> TransformDataHolder(it1.toInt()) } })

            val v4 = integer("v4")
                .nullable()
                .transform(DataHolderNullableTransformer())
                .transform(wrap = { it?.value.toString() }, unwrap = { it?.let { it1 -> TransformDataHolder(it1.toInt()) } })
        }

        withTables(tester) {
            val id1 = tester.insertAndGetId {
                it[v1] = "1"
                it[v2] = "2"
                it[v3] = "3"
                it[v4] = "4"
            }
            val entry1 = tester.selectAll().where { tester.id eq id1 }.first()
            assertEquals("1", entry1[tester.v1])
            assertEquals("2", entry1[tester.v2])
            assertEquals("3", entry1[tester.v3])
            assertEquals("4", entry1[tester.v4])

            val id2 = tester.insertAndGetId {
                it[v1] = "1"
                it[v2] = null
                it[v3] = null
                it[v4] = null
            }
            val entry2 = tester.selectAll().where { tester.id eq id2 }.first()
            assertEquals("1", entry2[tester.v1])
            assertEquals(null, entry2[tester.v2])
            assertEquals(null, entry2[tester.v3])
            assertEquals(null, entry2[tester.v4])
        }
    }

    @Test
    fun testReadTransformedValuesFromInsertStatement() {
        val tester = object : IntIdTable() {
            val v1 = integer("v1").transform(DataHolderTransformer())
            val v2 = integer("v2").nullTransform(DataHolderNullTransformer())
        }

        withTables(tester) {
            val statement = tester.insert {
                it[v1] = TransformDataHolder(1)
                it[tester.v2] = null
            }

            assertEquals(1, statement[tester.v1].value)
            assertEquals(null, statement[tester.v2])
        }
    }

    object TransformTable : IntIdTable() {
        val simple = integer("simple").transform(DataHolderTransformer())
        val chained = text("chained")
            .transform(wrap = { it.toInt() }, unwrap = { it.toString() })
            .transform(DataHolderTransformer())
    }

    class TransformEntity(id: EntityID<Int>) : IntEntity(id) {
        var simple by TransformTable.simple
        var chained by TransformTable.chained

        companion object : IntEntityClass<TransformEntity>(TransformTable)
    }

    @Test
    fun testTransformedValuesWithDAO() {
        withTables(TransformTable) {
            val entity = TransformEntity.new {
                this.simple = TransformDataHolder(120)
                this.chained = TransformDataHolder(240)
            }

            val row = TransformTable.selectAll().first()
            assertEquals(TransformDataHolder(120), row[TransformTable.simple])
            assertEquals(TransformDataHolder(240), row[TransformTable.chained])

            assertEquals(TransformDataHolder(120), entity.simple)
            assertEquals(TransformDataHolder(240), entity.chained)
        }
    }

    data class CustomId(val id: UUID) : Comparable<CustomId> {
        override fun compareTo(other: CustomId): Int = id.compareTo(other.id)
    }

    @Test
    fun testTransformIdColumn() {
        val tester = object : IdTable<CustomId>() {
            override val id: Column<EntityID<CustomId>> = uuid("id")
                .transform(wrap = { CustomId(it) }, unwrap = { it.id })
                .entityId()

            override val primaryKey: PrimaryKey = PrimaryKey(id)
        }

        val referenceTester = object : IntIdTable() {
            val reference = reference("reference", tester)
        }

        withTables(tester, referenceTester) {
            tester.insert {
                it[id] = CustomId(UUID.fromString("00000000-0000-0000-0000-000000000000"))
            }
            val transformedId = tester.selectAll().single()[tester.id]

            referenceTester.insert {
                it[reference] = transformedId
            }
            val referenceId = referenceTester.selectAll().single()[referenceTester.reference]
            assertEquals(referenceId, transformedId)
        }
    }

    @Test
    fun testNullToNonNullTransform() {
        val tester = object : IntIdTable("tester") {
            val value = integer("value")
                .nullable()
                .transform(wrap = { if (it == -1) null else it }, unwrap = { it ?: -1 })
        }

        val rawTester = object : IntIdTable("tester") {
            val value = integer("value").nullable()
        }

        withTables(tester) {
            tester.insert {
                it[value] = null
            }
            assertEquals(null, tester.selectAll().single()[tester.value])
            assertEquals(-1, rawTester.selectAll().single()[rawTester.value])
        }
    }

    @Test
    fun testNullToNonNullRecursiveTransform() {
        val tester = object : IntIdTable("tester") {
            val value = integer("value")
                .nullable()
                .transform(wrap = { if (it == -1) null else it }, unwrap = { it ?: -1 })
                .transform(DataHolderNullableTransformer())
        }

        val rawTester = object : IntIdTable("tester") {
            val value = long("value").nullable()
        }

        withTables(tester) {
            val id1 = tester.insertAndGetId {
                it[value] = TransformDataHolder(100)
            }
            assertEquals(100, tester.selectAll().where { tester.id eq id1 }.single()[tester.value]?.value)
            assertEquals(100, rawTester.selectAll().where { rawTester.id eq id1 }.single()[rawTester.value])

            val id2 = tester.insertAndGetId {
                it[value] = null
            }
            assertEquals(null, tester.selectAll().where { tester.id eq id2 }.single()[tester.value]?.value)
            assertEquals(-1, rawTester.selectAll().where { rawTester.id eq id2 }.single()[rawTester.value])
        }
    }

    @Test
    fun testNullTransform() {
        val tester = object : IntIdTable("tester") {
            val value = integer("value")
                .nullTransform(DataHolderNullTransformer())
        }

        val rawTester = object : IntIdTable("tester") {
            val value = integer("value")
        }

        withTables(tester) {
            val result = tester.insert {
                it[value] = null
            }
            assertEquals(null, result[tester.value])
            assertEquals(null, tester.selectAll().single()[tester.value])
            assertEquals(0, rawTester.selectAll().single()[rawTester.value])
        }
    }

    @Test
    fun testDefaults() {
        val tester = object : IntIdTable() {
            val value = integer("value")
                .transform(DataHolderTransformer())
                .default(TransformDataHolder(1))
        }

        withTables(tester) {
            val entry = tester.insert {}
            assertEquals(1, entry[tester.value].value)
            assertEquals(1, tester.selectAll().first()[tester.value].value)
        }
    }
}
