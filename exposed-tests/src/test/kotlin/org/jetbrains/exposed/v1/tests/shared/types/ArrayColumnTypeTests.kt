package org.jetbrains.exposed.v1.tests.shared.types

import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.IntIdTable
import org.jetbrains.exposed.v1.core.vendors.H2Dialect
import org.jetbrains.exposed.v1.core.vendors.PostgreSQLDialect
import org.jetbrains.exposed.v1.core.vendors.currentDialect
import org.jetbrains.exposed.v1.dao.IntEntity
import org.jetbrains.exposed.v1.dao.IntEntityClass
import org.jetbrains.exposed.v1.exceptions.ExposedSQLException
import org.jetbrains.exposed.v1.jdbc.*
import org.jetbrains.exposed.v1.tests.DatabaseTestsBase
import org.jetbrains.exposed.v1.tests.TestDB
import org.jetbrains.exposed.v1.tests.currentDialectTest
import org.jetbrains.exposed.v1.tests.shared.assertEqualLists
import org.jetbrains.exposed.v1.tests.shared.assertEquals
import org.jetbrains.exposed.v1.tests.shared.assertTrue
import org.jetbrains.exposed.v1.tests.shared.expectException
import org.junit.jupiter.api.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ArrayColumnTypeTests : DatabaseTestsBase() {
    private val arrayTypeUnsupportedDb = TestDB.ALL - (TestDB.ALL_POSTGRES + TestDB.H2_V2 + TestDB.H2_V2_PSQL).toSet()

    object ArrayTestTable : IntIdTable("array_test_table") {
        val numbers = array<Int>("numbers").default(listOf(5))
        val strings = array<String?>("strings", TextColumnType()).default(emptyList())
        val doubles = array<Double>("doubles").nullable()
        val byteArray = array("byte_array", BinaryColumnType(32)).nullable()
    }

    @Test
    fun testCreateAndDropArrayColumns() {
        withDb(excludeSettings = arrayTypeUnsupportedDb) {
            try {
                org.jetbrains.exposed.v1.jdbc.SchemaUtils.create(ArrayTestTable)
                assertTrue(ArrayTestTable.exists())
            } finally {
                org.jetbrains.exposed.v1.jdbc.SchemaUtils.drop(ArrayTestTable)
            }
        }
    }

    @Test
    fun testCreateMissingColumnsWithArrayDefaults() {
        withTestTableAndExcludeSettings {
            try {
                org.jetbrains.exposed.v1.jdbc.SchemaUtils.createMissingTablesAndColumns(ArrayTestTable)
                assertTrue(org.jetbrains.exposed.v1.jdbc.SchemaUtils.statementsRequiredToActualizeScheme(ArrayTestTable).isEmpty())
            } finally {
                org.jetbrains.exposed.v1.jdbc.SchemaUtils.drop(ArrayTestTable)
            }
        }
    }

    @Test
    fun testArrayColumnInsertAndSelect() {
        withTestTableAndExcludeSettings {
            val numInput = listOf(1, 2, 3)
            val stringInput = listOf<String?>("hi", "hey", "hello")
            val doubleInput = listOf(1.0, 2.0, 3.0)
            val id1 = ArrayTestTable.insertAndGetId {
                it[numbers] = numInput
                it[strings] = stringInput
                it[doubles] = doubleInput
            }

            val result1 = ArrayTestTable.selectAll().where { ArrayTestTable.id eq id1 }.single()
            assertContentEquals(numInput, result1[ArrayTestTable.numbers])
            assertContentEquals(stringInput, result1[ArrayTestTable.strings])
            assertContentEquals(doubleInput, result1[ArrayTestTable.doubles])

            val id2 = ArrayTestTable.insertAndGetId {
                it[numbers] = emptyList()
                it[strings] = emptyList()
                it[doubles] = emptyList()
            }

            val result2: ResultRow = ArrayTestTable.selectAll().where { ArrayTestTable.id eq id2 }.single()
            assertTrue(result2[ArrayTestTable.numbers].isEmpty())
            assertTrue(result2[ArrayTestTable.strings].isEmpty())
            assertEquals(true, result2[ArrayTestTable.doubles]?.isEmpty())

            val id3 = ArrayTestTable.insertAndGetId {
                it[strings] = listOf(null, null, null, "null")
                it[doubles] = null
            }

            val result3 = ArrayTestTable.selectAll().where { ArrayTestTable.id eq id3 }.single()
            assertEquals(5, result3[ArrayTestTable.numbers].single())
            assertTrue(result3[ArrayTestTable.strings].take(3).all { it == null })
            assertEquals("null", result3[ArrayTestTable.strings].last())
            assertNull(result3[ArrayTestTable.doubles])
        }
    }

    @Test
    fun testArrayMaxSize() {
        val maxArraySize = 5
        val sizedTester = object : Table("sized_tester") {
            val numbers = array("numbers", IntegerColumnType(), maxArraySize).default(emptyList())
        }

        withTestTableAndExcludeSettings(sizedTester) {
            val tooLongList = List(maxArraySize + 1) { i -> i + 1 }
            if (currentDialectTest is PostgreSQLDialect) {
                // PostgreSQL ignores any max cardinality value
                sizedTester.insert {
                    it[numbers] = tooLongList
                }
                assertContentEquals(tooLongList, sizedTester.selectAll().single()[sizedTester.numbers])
            } else {
                // H2 throws 'value too long for column' exception
                expectException<ExposedSQLException> {
                    sizedTester.insert {
                        it[numbers] = tooLongList
                    }
                }
            }
        }
    }

    @Test
    fun testSelectUsingArrayGet() {
        withTestTableAndExcludeSettings {
            val numInput = listOf(1, 2, 3)
            ArrayTestTable.insert {
                it[numbers] = numInput
                it[strings] = listOf<String?>("hi", "hello")
                it[doubles] = null
            }

            // SQL array indexes are one-based
            val secondNumber = ArrayTestTable.numbers[2]
            val result1 = ArrayTestTable.select(secondNumber).single()[secondNumber]
            assertEquals(numInput[1], result1)

            val result2 = ArrayTestTable.selectAll().where { ArrayTestTable.strings[2] eq "hello" }
            assertNull(result2.single()[ArrayTestTable.doubles])

            val result3 = ArrayTestTable.selectAll().where {
                ArrayTestTable.numbers[1] greaterEq ArrayTestTable.numbers[3]
            }
            assertTrue(result3.toList().isEmpty())

            val nullArray = ArrayTestTable.doubles[2]
            val result4 = ArrayTestTable.select(nullArray).single()[nullArray]
            assertNull(result4)
        }
    }

    @Test
    fun testSelectUsingArraySlice() {
        withTestTableAndExcludeSettings {
            val numInput = listOf(1, 2, 3)
            ArrayTestTable.insert {
                it[numbers] = numInput
                it[strings] = listOf(null, null, null, "hello")
                it[doubles] = null
            }

            val lastTwoNumbers = ArrayTestTable.numbers.slice(2, 3) // numbers[2:3]
            val result1 = ArrayTestTable.select(lastTwoNumbers).single()[lastTwoNumbers]
            assertContentEquals(numInput.takeLast(2), result1)

            val firstThreeStrings = ArrayTestTable.strings.slice(upper = 3) // strings[:3]
            val result2 = ArrayTestTable.select(firstThreeStrings).single()[firstThreeStrings]
            if (currentDialect is H2Dialect) { // H2 returns SQL NULL if any parameter in ARRAY_SLICE is null
                assertNull(result2)
            } else {
                assertTrue(result2.filterNotNull().isEmpty())
            }

            val allNumbers = ArrayTestTable.numbers.slice() // numbers[:]
            val result3 = ArrayTestTable.select(allNumbers).single()[allNumbers]
            if (currentDialect is H2Dialect) {
                assertNull(result3)
            } else {
                assertContentEquals(numInput, result3)
            }

            val nullArray = ArrayTestTable.doubles.slice(1, 3)
            val result4 = ArrayTestTable.select(nullArray).single()[nullArray]
            assertNull(result4)
        }
    }

    @Test
    fun testArrayLiteralAndArrayParam() {
        withTestTableAndExcludeSettings {
            val numInput = listOf(1, 2, 3)
            val doublesInput = List(5) { i -> (i + 1).toDouble() }
            val id1 = ArrayTestTable.insertAndGetId {
                it[numbers] = numInput
                it[strings] = listOf(null, null, null, "hello")
                it[doubles] = doublesInput
            }

            val result1 = ArrayTestTable.select(ArrayTestTable.id).where {
                (ArrayTestTable.numbers eq numInput) and (ArrayTestTable.strings neq emptyList())
            }
            assertEquals(id1, result1.single()[ArrayTestTable.id])

            val result2 = ArrayTestTable.select(ArrayTestTable.id).where {
                ArrayTestTable.doubles eq arrayParam(doublesInput)
            }
            assertEquals(id1, result2.single()[ArrayTestTable.id])

            if (currentDialectTest is PostgreSQLDialect) {
                val lastStrings = ArrayTestTable.strings.slice(lower = 4) // strings[4:]
                val result3 = ArrayTestTable.select(ArrayTestTable.id).where {
                    lastStrings eq arrayLiteral(listOf("hello"))
                }
                assertEquals(id1, result3.single()[ArrayTestTable.id])
            }
        }
    }

    @Test
    fun testArrayColumnUpdate() {
        withTestTableAndExcludeSettings {
            val id1 = ArrayTestTable.insertAndGetId {
                it[doubles] = null
            }

            assertNull(ArrayTestTable.selectAll().single()[ArrayTestTable.doubles])

            val updatedDoubles = listOf(9.0)
            ArrayTestTable.update({ ArrayTestTable.id eq id1 }) {
                it[doubles] = updatedDoubles
            }

            assertContentEquals(updatedDoubles, ArrayTestTable.selectAll().single()[ArrayTestTable.doubles])
        }
    }

    @Test
    fun testArrayColumnUpsert() {
        withTestTableAndExcludeSettings {
            val numbers = listOf(1, 2, 3)
            val strings = listOf("A", "B")
            val id1 = ArrayTestTable.insertAndGetId {
                it[ArrayTestTable.numbers] = numbers
                it[ArrayTestTable.strings] = strings
            }

            assertContentEquals(numbers, ArrayTestTable.selectAll().single()[ArrayTestTable.numbers])
            assertContentEquals(strings, ArrayTestTable.selectAll().single()[ArrayTestTable.strings])

            val updatedStrings = listOf("C", "D", "E")
            ArrayTestTable.upsert(
                onUpdate = { it[ArrayTestTable.strings] = updatedStrings }
            ) {
                it[id] = id1
                it[ArrayTestTable.numbers] = numbers
                it[ArrayTestTable.strings] = strings
            }

            assertContentEquals(numbers, ArrayTestTable.selectAll().single()[ArrayTestTable.numbers])
            assertContentEquals(updatedStrings, ArrayTestTable.selectAll().single()[ArrayTestTable.strings])
        }
    }

    class ArrayTestDao(id: EntityID<Int>) : IntEntity(id) {
        companion object : IntEntityClass<ArrayTestDao>(ArrayTestTable)

        var numbers by ArrayTestTable.numbers
        var strings by ArrayTestTable.strings
        var doubles by ArrayTestTable.doubles
    }

    // TODO
    @Test
    fun testArrayColumnWithDAOFunctions() {
        withTestTableAndExcludeSettings {
            val numInput = listOf(1, 2, 3)
            val entity1 = ArrayTestDao.new {
                numbers = numInput
                doubles = null
            }
            assertContentEquals(numInput, entity1.numbers)
            assertTrue(entity1.strings.isEmpty())

            val doublesInput = listOf(9.0)
            entity1.doubles = doublesInput

            assertContentEquals(doublesInput, ArrayTestDao.all().single().doubles)
        }
    }

    @Test
    fun testArrayColumnWithAllAnyOps() {
        withTestTableAndExcludeSettings {
            val numInput = listOf(1, 2, 3)
            val id1 = ArrayTestTable.insertAndGetId {
                it[numbers] = numInput
                it[doubles] = null
            }

            val result1 = ArrayTestTable.select(ArrayTestTable.id).where {
                ArrayTestTable.id eq anyFrom(ArrayTestTable.numbers)
            }
            assertEquals(id1, result1.single()[ArrayTestTable.id])

            val result2 = ArrayTestTable.select(ArrayTestTable.id).where {
                ArrayTestTable.id eq anyFrom(ArrayTestTable.numbers.slice(2, 3))
            }
            assertTrue(result2.toList().isEmpty())

            val result3 = ArrayTestTable.select(ArrayTestTable.id).where {
                ArrayTestTable.id lessEq allFrom(ArrayTestTable.numbers)
            }
            assertEquals(id1, result3.single()[ArrayTestTable.id])

            val result4 = ArrayTestTable.select(ArrayTestTable.id).where {
                ArrayTestTable.id greater allFrom(arrayParam(numInput))
            }
            assertTrue(result4.toList().isEmpty())
        }
    }

    @Test
    fun testInsertArrayOfByteArrays() {
        // POSTGRESQLNG is excluded because the problem may be on their side.
        // Related issue: https://github.com/impossibl/pgjdbc-ng/issues/600
        // Recheck on our side when the issue is resolved.
        withTestTableAndExcludeSettings(excludeSettings = arrayTypeUnsupportedDb + TestDB.POSTGRESQLNG) {
            val testByteArraysList = listOf(
                byteArrayOf(0), byteArrayOf(1, 2, 3)
            )
            ArrayTestTable.insert {
                it[byteArray] = testByteArraysList
            }
            val result = ArrayTestTable.selectAll().first()[ArrayTestTable.byteArray]

            assertNotNull(result)
            assertEquals(testByteArraysList[0][0], result[0][0])
            assertEquals(testByteArraysList[1].toUByteString(), result[1].toUByteString())
        }
    }

    @Test
    fun testAliasedArray() {
        val tester = object : IntIdTable("test_aliased_array") {
            val value = array<Int>("value")
        }

        val value = listOf(1, 2, 3)

        withTables(excludeSettings = arrayTypeUnsupportedDb, tester) {
            tester.insert {
                it[tester.value] = value
            }

            val alias = tester.value.alias("testTable_indexes")

            assertEqualLists(value, tester.select(alias).first()[tester.value])
        }
    }

    private fun withTestTableAndExcludeSettings(
        vararg tables: Table = arrayOf(ArrayTestTable),
        excludeSettings: Collection<TestDB> = arrayTypeUnsupportedDb,
        statement: JdbcTransaction.(TestDB) -> Unit
    ) {
        withTables(excludeSettings = excludeSettings, *tables) { db ->
            statement(db)
        }
    }
}

private fun ByteArray.toUByteString() = joinToString { it.toUByte().toString() }
