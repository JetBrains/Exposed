package org.jetbrains.exposed.sql.tests.shared.ddl

import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.ColumnTransformer
import org.jetbrains.exposed.sql.StdOutSqlLogger
import org.jetbrains.exposed.sql.addLogger
import org.jetbrains.exposed.sql.insertAndGetId
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.tests.DatabaseTestsBase
import org.jetbrains.exposed.sql.tests.shared.assertEqualLists
import org.jetbrains.exposed.sql.tests.shared.assertEquals
import org.junit.Test
import kotlin.test.assertNull

class ColumnTransformTest : DatabaseTestsBase() {
    @Test
    fun testSimpleTransforms() {
        val tester = object : IntIdTable("SimpleTransforms") {
            val stringToInteger = integer("stringToInteger")
                .transform(toColumn = { it.toInt() }, toReal = { it.toString() })
            val nullableStringToInteger = integer("nullableStringToInteger")
                .nullable()
                .transform(toColumn = { it.toInt() }, toReal = { it.toString() })
            val stringToIntegerNullable = integer("stringToIntegerNullable")
                .transform(toColumn = { it.toInt() }, toReal = { it.toString() })
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
            val booleanToInteger = integer("stringToInteger")
                .transform(toReal = { if (it != 0) "TRUE" else "FALSE" }, toColumn = { if (it == "TRUE") 1 else 0 })
                .transform(toReal = { it == "TRUE" }, toColumn = { if (it) "TRUE" else "FALSE" })

            val booleanToIntegerNullable = integer("booleanToIntegerNullable")
                .transform(toReal = { if (it != 0) "TRUE" else "FALSE" }, toColumn = { if (it == "TRUE") 1 else 0 })
                .nullable()
                .transform(toReal = { it == "TRUE" }, toColumn = { if (it) "TRUE" else "FALSE" })
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

    object IntListColumnType : ColumnTransformer<List<Int>, String> {
        override fun toReal(value: String): List<Int> {
            val result = value.split(",").map { it.toInt() }
            return result
        }

        override fun toColumn(value: List<Int>): String = value.joinToString(",")
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
            addLogger(StdOutSqlLogger)
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
}
