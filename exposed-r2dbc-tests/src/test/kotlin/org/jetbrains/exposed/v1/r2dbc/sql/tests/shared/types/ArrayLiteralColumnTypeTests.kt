package org.jetbrains.exposed.v1.r2dbc.sql.tests.shared.types

import kotlinx.coroutines.flow.single
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.core.dao.id.IntIdTable
import org.jetbrains.exposed.v1.exceptions.UnsupportedByDialectException
import org.jetbrains.exposed.v1.r2dbc.*
import org.jetbrains.exposed.v1.r2dbc.tests.R2dbcDatabaseTestsBase
import org.jetbrains.exposed.v1.r2dbc.tests.TestDB
import org.jetbrains.exposed.v1.r2dbc.tests.shared.assertEqualLists
import org.jetbrains.exposed.v1.r2dbc.tests.shared.assertEquals
import org.jetbrains.exposed.v1.r2dbc.tests.shared.assertTrue
import org.jetbrains.exposed.v1.r2dbc.tests.shared.expectException
import org.junit.jupiter.api.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class ArrayLiteralColumnTypeTests : R2dbcDatabaseTestsBase() {
    private val arrayLiteralUnsupportedDb = TestDB.ALL - TestDB.ALL_POSTGRES.toSet()

    enum class Mood(val label: String) { HAPPY("happy"), SAD("sad"), WEIRD("weird, \"quoted\" {label}") }

    data class Member(val id: Long, val role: String?)

    /** A user-defined composite `(id, role)`, which no driver has a codec for. */
    class MemberColumnType : ColumnType<Member>() {
        override fun sqlType(): String = "array_literal_member"

        override fun notNullValueToDB(value: Member): Any =
            "(${value.id},${value.role?.let { "\"" + it.replace("\\", "\\\\").replace("\"", "\\\"") + "\"" } ?: ""})"

        override fun valueFromDB(value: Any): Member {
            val fields = parseComposite(value.toString())
            return Member(fields[0]!!.toLong(), fields[1])
        }
    }

    object ArrayLiteralTable : IntIdTable("array_literal_table") {
        val moods = arrayLiteral<Mood?>(
            "moods",
            CustomEnumerationColumnType("moods", "array_literal_mood", { value -> Mood.entries.single { it.label == value.toString() } }, { it.label })
        )
        val members = arrayLiteral("members", MemberColumnType()).nullable()
        val strings = arrayLiteral<String?>("strings", TextColumnType()).default(emptyList())
        val longs = arrayLiteral("longs", LongColumnType()).nullable()
    }

    private suspend fun R2dbcTransaction.withArrayLiteralTable(statement: suspend () -> Unit) {
        try {
            exec("DROP TABLE IF EXISTS ${ArrayLiteralTable.nameInDatabaseCase()}")
            exec("DROP TYPE IF EXISTS array_literal_mood")
            exec("DROP TYPE IF EXISTS array_literal_member")
            exec("CREATE TYPE array_literal_mood AS ENUM ('happy', 'sad', 'weird, \"quoted\" {label}')")
            exec("CREATE TYPE array_literal_member AS (id BIGINT, role TEXT)")
            SchemaUtils.create(ArrayLiteralTable)
            statement()
        } finally {
            SchemaUtils.drop(ArrayLiteralTable)
            exec("DROP TYPE IF EXISTS array_literal_mood")
            exec("DROP TYPE IF EXISTS array_literal_member")
        }
    }

    private val trickyStrings = listOf("plain", "", "NULL", "null", null, "with,comma", "with \"quotes\"", "back\\slash", " padded ", "{braces}", "it's")

    private val sampleMembers = listOf(
        Member(1, "outer"),
        Member(2, null),
        Member(3, ""),
        Member(4, "with,comma (and) \"quotes\" back\\slash"),
    )

    @Test
    fun testInsertAndSelect() {
        withDb(excludeSettings = arrayLiteralUnsupportedDb) {
            withArrayLiteralTable {
                val id = ArrayLiteralTable.insertAndGetId {
                    it[moods] = listOf(Mood.HAPPY, null, Mood.WEIRD)
                    it[strings] = trickyStrings
                    it[longs] = listOf(Long.MIN_VALUE, 0L, Long.MAX_VALUE)
                }

                val row = ArrayLiteralTable.selectAll().where { ArrayLiteralTable.id eq id }.single()
                assertEqualLists(listOf(Mood.HAPPY, null, Mood.WEIRD), row[ArrayLiteralTable.moods])
                assertEqualLists(trickyStrings, row[ArrayLiteralTable.strings])
                assertEqualLists(listOf(Long.MIN_VALUE, 0L, Long.MAX_VALUE), row[ArrayLiteralTable.longs]!!)

                // check what the database itself stored, independently of the column type's own decoding
                val stored = exec(
                    "SELECT (moods[2] IS NULL)::text, moods[3]::text, (strings[5] IS NULL)::text, strings[3], cardinality(strings)::text " +
                        "FROM ${ArrayLiteralTable.nameInDatabaseCase()}"
                ) { rs -> (0..4).map { rs.get(it, String::class.java) } }?.single()
                assertEqualLists(listOf("true", Mood.WEIRD.label, "true", "NULL", trickyStrings.size.toString()), stored!!)
            }
        }
    }

    @Test
    fun testEmptyAndNullArrays() {
        withDb(excludeSettings = arrayLiteralUnsupportedDb) {
            withArrayLiteralTable {
                ArrayLiteralTable.insert {
                    it[moods] = emptyList()
                    it[members] = null
                    it[longs] = null
                }

                val row = ArrayLiteralTable.selectAll().single()
                assertTrue(row[ArrayLiteralTable.moods].isEmpty())
                assertNull(row[ArrayLiteralTable.members])
                assertTrue(row[ArrayLiteralTable.strings].isEmpty())
                assertNull(row[ArrayLiteralTable.longs])
            }
        }
    }

    @Test
    fun testUpdateAndWhere() {
        withDb(excludeSettings = arrayLiteralUnsupportedDb) {
            withArrayLiteralTable {
                ArrayLiteralTable.insert {
                    it[moods] = listOf(Mood.SAD)
                }

                ArrayLiteralTable.update({ ArrayLiteralTable.moods eq listOf(Mood.SAD) }) {
                    it[strings] = trickyStrings
                }

                val row = ArrayLiteralTable.selectAll().where { ArrayLiteralTable.strings eq trickyStrings }.single()
                assertEqualLists(trickyStrings, row[ArrayLiteralTable.strings])
            }
        }
    }

    @Test
    fun testCompositeElements() {
        withDb(excludeSettings = arrayLiteralUnsupportedDb) {
            withArrayLiteralTable {
                ArrayLiteralTable.insert {
                    it[moods] = emptyList()
                    it[ArrayLiteralTable.members] = listOf(Member(0, "initial"))
                }

                ArrayLiteralTable.update({ ArrayLiteralTable.members eq listOf(Member(0, "initial")) }) {
                    it[ArrayLiteralTable.members] = sampleMembers
                }

                val row = ArrayLiteralTable.selectAll().where { ArrayLiteralTable.members eq sampleMembers }.single()
                assertEqualLists(sampleMembers, row[ArrayLiteralTable.members]!!)

                val stored = exec(
                    "SELECT ((members[2]).role IS NULL)::text, (members[3]).role, (members[4]).role FROM ${ArrayLiteralTable.nameInDatabaseCase()}"
                ) { rs -> (0..2).map { rs.get(it, String::class.java) } }?.single()
                assertEqualLists(listOf("true", sampleMembers[2].role, sampleMembers[3].role), stored!!)
            }
        }
    }

    @Test
    fun testLiteralExpression() {
        withDb(excludeSettings = arrayLiteralUnsupportedDb) {
            withArrayLiteralTable {
                ArrayLiteralTable.insert {
                    it[moods] = listOf(Mood.HAPPY)
                    it[strings] = trickyStrings
                }

                val literal = LiteralOp(ArrayLiteralTable.strings.columnType, trickyStrings)
                assertEquals(1L, ArrayLiteralTable.selectAll().where { ArrayLiteralTable.strings eq literal }.count())
            }
        }
    }

    data class Box(val x1: Double, val y1: Double, val x2: Double, val y2: Double)

    /**
     * The built-in `box` type, whose array delimiter is a semicolon. Its corner coordinates are extracted from any
     * representation, as some drivers decode `box[]` arrays themselves.
     */
    class BoxColumnType : ColumnType<Box>() {
        override fun sqlType(): String = "BOX"

        override fun notNullValueToDB(value: Box): Any = "(${value.x1},${value.y1}),(${value.x2},${value.y2})"

        override fun valueFromDB(value: Any): Box {
            val (x1, y1, x2, y2) = when (value) {
                is DoubleArray -> value.toList() // pgjdbc-ng
                else -> Regex("-?\\d+(\\.\\d+)?").findAll(value.toString()).map { it.value.toDouble() }.toList()
            }
            return Box(x1, y1, x2, y2)
        }
    }

    object BoxArrayTable : Table("array_literal_box_table") {
        val boxes = arrayLiteral<Box?>("boxes", BoxColumnType(), delimiter = ';')
    }

    // PostgreSQL stores a box with its upper right corner first, so these are already in its canonical form
    private val sampleBoxes = listOf(Box(1.0, 1.0, 0.0, 0.0), null, Box(3.5, 3.0, -2.0, 2.0))

    @Test
    fun testNonCommaDelimiter() {
        withTables(excludeSettings = arrayLiteralUnsupportedDb, BoxArrayTable) {
            BoxArrayTable.insert {
                it[boxes] = sampleBoxes
            }

            assertEqualLists(sampleBoxes, BoxArrayTable.selectAll().single()[BoxArrayTable.boxes])

            val stored = exec("SELECT boxes::text FROM ${BoxArrayTable.nameInDatabaseCase()}") { rs -> rs.get(0, String::class.java) }?.single()
            assertEquals("{(1,1),(0,0);NULL;(3.5,3),(-2,2)}", stored)
        }
    }

    @Test
    fun testInvalidDelimiter() {
        listOf('"', '\\', '{', '}', ' ').forEach { delimiter ->
            assertFailsWith<IllegalArgumentException> {
                ArrayLiteralColumnType(LongColumnType(), delimiter)
            }
        }
    }

    @Test
    fun testUnsupportedDialect() {
        withDb(db = arrayLiteralUnsupportedDb) {
            expectException<UnsupportedByDialectException> {
                ArrayLiteralTable.longs.columnType.sqlType()
            }
        }
    }
}

/** Splits a composite literal `(a,"b",)` into its fields, where an unquoted empty field is a SQL `NULL`. */
private fun parseComposite(literal: String): List<String?> {
    val body = literal.trim().removePrefix("(").removeSuffix(")")
    val fields = ArrayList<String?>()
    val current = StringBuilder()
    var inQuotes = false
    var wasQuoted = false
    var i = 0
    while (i < body.length) {
        when (val c = body[i]) {
            '\\' -> current.append(body[++i])
            '"' if inQuotes && i + 1 < body.length && body[i + 1] == '"' -> current.append(body[++i])
            '"' -> {
                inQuotes = !inQuotes
                wasQuoted = true
            }
            ',' if !inQuotes -> {
                fields.add(if (wasQuoted || current.isNotEmpty()) current.toString() else null)
                current.setLength(0)
                wasQuoted = false
            }
            else -> current.append(c)
        }
        i++
    }
    fields.add(if (wasQuoted || current.isNotEmpty()) current.toString() else null)
    return fields
}
