package org.jetbrains.exposed.sql.tests.shared.ddl

import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.tests.DatabaseTestsBase
import org.jetbrains.exposed.sql.tests.shared.assertEqualLists
import org.jetbrains.exposed.sql.tests.shared.assertEquals
import org.jetbrains.exposed.sql.tests.shared.ddl.ColumnWithTransformTest.ColumnWithTransformTable.simple
import org.junit.Test
import kotlin.test.assertNull

class ColumnWithTransformTest : DatabaseTestsBase() {
    @Test
    fun testSimpleTransforms() {
        val tester = object : IntIdTable("SimpleTransforms") {
            val stringToInteger = integer("stringToInteger")
                .transform(unwrap = { it.toInt() }, wrap = { it.toString() })
            val nullableStringToInteger = integer("nullableStringToInteger")
                .nullable()
                .transform(unwrap = { it.toInt() }, wrap = { it.toString() })
            val stringToIntegerNullable = integer("stringToIntegerNullable")
                .transform(unwrap = { it.toInt() }, wrap = { it.toString() })
                .nullable()
        }

        withTables(tester) {
            val id1 = tester.insertAndGetId {
                it[stringToInteger] = "1"
                it[nullableStringToInteger] = "2"
                it[stringToIntegerNullable] = "3"
            }
            val entry1 = tester.selectAll().where { tester.id eq id1 }.first()
            assertEquals("1", entry1[tester.stringToInteger])
            assertEquals("2", entry1[tester.nullableStringToInteger])
            assertEquals("3", entry1[tester.stringToIntegerNullable])

            val id2 = tester.insertAndGetId {
                it[stringToInteger] = "1"
                it[nullableStringToInteger] = null
                it[stringToIntegerNullable] = null
            }
            val entry2 = tester.selectAll().where { tester.id eq id2 }.first()
            assertEquals(null, entry2[tester.nullableStringToInteger])
            assertEquals(null, entry2[tester.stringToIntegerNullable])
        }
    }

    @Test
    fun testNestedTransforms() {
        val tester = object : IntIdTable("NestedTransforms") {
            val booleanToInteger = integer("booleanToInteger")
                .transform(wrap = { if (it != 0) "TRUE" else "FALSE" }, unwrap = { if (it == "TRUE") 1 else 0 })
                .transform(wrap = { it == "TRUE" }, unwrap = { if (it) "TRUE" else "FALSE" })

            val booleanToIntegerNullable = integer("booleanToIntegerNullable")
                .transform(wrap = { if (it != 0) "TRUE" else "FALSE" }, unwrap = { if (it == "TRUE") 1 else 0 })
                .nullable()
                .transform(wrap = { it == "TRUE" }, unwrap = { if (it) "TRUE" else "FALSE" })
        }

        withTables(tester) {
            val id1 = tester.insertAndGetId {
                it[booleanToInteger] = true
                it[booleanToIntegerNullable] = true
            }
            val entry1 = tester.selectAll().where { tester.id eq id1 }.first()
            assertEquals(true, entry1[tester.booleanToInteger])
            assertEquals(true, entry1[tester.booleanToIntegerNullable])

            val id2 = tester.insertAndGetId {
                it[booleanToInteger] = false
                it[booleanToIntegerNullable] = null
            }
            val entry2 = tester.selectAll().where { tester.id eq id2 }.first()
            assertEquals(false, entry2[tester.booleanToInteger])
            assertEquals(null, entry2[tester.booleanToIntegerNullable])
        }
    }

    object IntListColumnType : ColumnTransformer<String, List<Int>> {
        override fun wrap(value: String): List<Int> {
            val result = value.split(",").map { it.toInt() }
            return result
        }

        override fun unwrap(value: List<Int>): String = value.joinToString(",")
    }

    @Test
    fun testReadTransformedValuesFromInsertStatement() {
        val tester = object : IntIdTable("SimpleTransforms") {
            val stringToInteger = integer("stringToInteger")
                .transform(unwrap = { it.toInt() }, wrap = { it.toString() })
            val booleanToInteger = integer("booleanToInteger")
                .transform(wrap = { if (it != 0) "TRUE" else "FALSE" }, unwrap = { if (it == "TRUE") 1 else 0 })
                .transform(wrap = { it == "TRUE" }, unwrap = { if (it) "TRUE" else "FALSE" })
        }

        withTables(tester) {
            val statement = tester.insert {
                it[stringToInteger] = "1"
                it[booleanToInteger] = true
            }

            assertEquals("1", statement[tester.stringToInteger])
            assertEquals(true, statement[tester.booleanToInteger])
        }
    }

    @Test
    fun testTransformViaColumnTransformer() {
        val tester = object : IntIdTable("TransformViaColumnTransformer") {
            val numbers = text("numbers")
                .transform(IntListColumnType)
            val numbersNullable = text("numbersNullable")
                .transform(IntListColumnType)
                .nullable()

            val nullableNumbers = text("nullableNumbers")
                .nullable()
                .transform(IntListColumnType)
        }

        withTables(tester) {
            val id1 = tester.insertAndGetId {
                it[tester.numbers] = listOf(1, 2, 3)
                it[tester.numbersNullable] = listOf(4, 5, 6)
                it[tester.nullableNumbers] = listOf(7, 8, 9)
            }

            val entry1 = tester.selectAll().where { tester.id eq id1 }.single()
            assertEqualLists(listOf(1, 2, 3), entry1[tester.numbers])
            assertEqualLists(listOf(4, 5, 6), entry1[tester.numbersNullable] ?: emptyList())
            assertEqualLists(listOf(7, 8, 9), entry1[tester.nullableNumbers] ?: emptyList())

            val id2 = tester.insertAndGetId {
                it[tester.numbers] = listOf(1, 2, 3)
                it[tester.numbersNullable] = null
                it[tester.nullableNumbers] = null
            }

            val entry2 = tester.selectAll().where { tester.id eq id2 }.single()
            assertEqualLists(listOf(1, 2, 3), entry2[tester.numbers])
            assertNull(entry2[tester.numbersNullable])
            assertNull(entry2[tester.nullableNumbers])
        }
    }

    object ColumnWithTransformTable : IntIdTable() {
        val simple = long("simple")
            .transform(LongToDataHolderTransformer())
        val chained = long("chained")
            .transform(LongToDataHolderTransformer())
            .transform(DataHolderToStringTransformer())
    }

    class FileSizeDao(id: EntityID<Int>) : IntEntity(id) {
        var simple by ColumnWithTransformTable.simple
        var chained by ColumnWithTransformTable.chained

        companion object : IntEntityClass<FileSizeDao>(ColumnWithTransformTable)
    }

    @Test
    fun testTransformedValuesWithDAO() {
        withTables(ColumnWithTransformTable) {
            val entity = FileSizeDao.new {
                this.simple = ColumnWithTransformDataHolder(120)
                this.chained = "240"
            }

            val row = ColumnWithTransformTable.selectAll().first()
            assertEquals(ColumnWithTransformDataHolder(120), row[simple])
            assertEquals("240", row[ColumnWithTransformTable.chained])

            assertEquals(ColumnWithTransformDataHolder(120), entity.simple)
            assertEquals("240", entity.chained)
        }
    }
}

data class ColumnWithTransformDataHolder(val value: Long)

class LongToDataHolderTransformer : ColumnTransformer<Long, ColumnWithTransformDataHolder> {
    override fun wrap(value: Long): ColumnWithTransformDataHolder = ColumnWithTransformDataHolder(value)
    override fun unwrap(value: ColumnWithTransformDataHolder): Long = value.value
}

class DataHolderToStringTransformer : ColumnTransformer<ColumnWithTransformDataHolder, String> {
    override fun wrap(value: ColumnWithTransformDataHolder): String = value.value.toString()
    override fun unwrap(value: String): ColumnWithTransformDataHolder = ColumnWithTransformDataHolder(value.toLong())
}
