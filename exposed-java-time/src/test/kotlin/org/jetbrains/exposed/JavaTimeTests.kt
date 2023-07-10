package org.jetbrains.exposed

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.between
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.like
import org.jetbrains.exposed.sql.javatime.*
import org.jetbrains.exposed.sql.tests.DatabaseTestsBase
import org.jetbrains.exposed.sql.tests.TestDB
import org.jetbrains.exposed.sql.tests.currentDialectTest
import org.jetbrains.exposed.sql.tests.shared.assertEquals
import org.jetbrains.exposed.sql.tests.shared.assertTrue
import org.jetbrains.exposed.sql.vendors.*
import org.junit.Assert.fail
import org.junit.Test
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.temporal.Temporal
import kotlin.test.assertEquals

open class JavaTimeBaseTest : DatabaseTestsBase() {

    @Test
    fun javaTimeFunctions() {
        withTables(CitiesTime) {
            val now = LocalDateTime.now()

            val cityID = CitiesTime.insertAndGetId {
                it[name] = "Tunisia"
                it[local_time] = now
            }

            val insertedYear = CitiesTime.slice(CitiesTime.local_time.year()).select { CitiesTime.id.eq(cityID) }.single()[CitiesTime.local_time.year()]
            val insertedMonth = CitiesTime.slice(CitiesTime.local_time.month()).select { CitiesTime.id.eq(cityID) }.single()[CitiesTime.local_time.month()]
            val insertedDay = CitiesTime.slice(CitiesTime.local_time.day()).select { CitiesTime.id.eq(cityID) }.single()[CitiesTime.local_time.day()]
            val insertedHour = CitiesTime.slice(CitiesTime.local_time.hour()).select { CitiesTime.id.eq(cityID) }.single()[CitiesTime.local_time.hour()]
            val insertedMinute = CitiesTime.slice(CitiesTime.local_time.minute()).select { CitiesTime.id.eq(cityID) }.single()[CitiesTime.local_time.minute()]
            val insertedSecond = CitiesTime.slice(CitiesTime.local_time.second()).select { CitiesTime.id.eq(cityID) }.single()[CitiesTime.local_time.second()]

            assertEquals(now.year, insertedYear)
            assertEquals(now.month.value, insertedMonth)
            assertEquals(now.dayOfMonth, insertedDay)
            assertEquals(now.hour, insertedHour)
            assertEquals(now.minute, insertedMinute)
            assertEquals(now.second, insertedSecond)
        }
    }

    // Checks that old numeric datetime columns works fine with new text representation
    @Test
    fun testSQLiteDateTimeFieldRegression() {
        val testDate = object : IntIdTable("TestDate") {
            val time = datetime("time").defaultExpression(CurrentDateTime)
        }

        withDb(TestDB.SQLITE) {
            try {
                exec("CREATE TABLE IF NOT EXISTS TestDate (id INTEGER PRIMARY KEY AUTOINCREMENT, \"time\" NUMERIC DEFAULT (CURRENT_TIMESTAMP) NOT NULL);")
                testDate.insert { }
                val year = testDate.time.year()
                val month = testDate.time.month()
                val day = testDate.time.day()
                val hour = testDate.time.hour()
                val minute = testDate.time.minute()

                val result = testDate.slice(year, month, day, hour, minute).selectAll().single()

                val now = LocalDateTime.now()
                assertEquals(now.year, result[year])
                assertEquals(now.monthValue, result[month])
                assertEquals(now.dayOfMonth, result[day])
                assertEquals(now.hour, result[hour])
                assertEquals(now.minute, result[minute])
            } finally {
                SchemaUtils.drop(testDate)
            }
        }
    }

    @Test
    fun `test storing LocalDateTime with nanos`() {
        val testDate = object : IntIdTable("TestLocalDateTime") {
            val time = datetime("time")
        }
        withTables(testDate) {
            val dateTimeWithNanos = LocalDateTime.now().withNano(123)
            testDate.insert {
                it[time] = dateTimeWithNanos
            }

            val dateTimeFromDB = testDate.selectAll().single()[testDate.time]
            assertEqualDateTime(dateTimeWithNanos, dateTimeFromDB)
        }
    }

    @Test
    fun `test selecting Instant using expressions`() {
        val testTable = object : Table() {
            val ts = timestamp("ts")
            val tsn = timestamp("tsn").nullable()
        }

        val now = Instant.now()

        withTables(testTable) {
            testTable.insert {
                it[ts] = now
                it[tsn] = now
            }

            val maxTsExpr = testTable.ts.max()
            val maxTimestamp = testTable.slice(maxTsExpr).selectAll().single()[maxTsExpr]
            assertEqualDateTime(now, maxTimestamp)

            val minTsExpr = testTable.ts.min()
            val minTimestamp = testTable.slice(minTsExpr).selectAll().single()[minTsExpr]
            assertEqualDateTime(now, minTimestamp)

            val maxTsnExpr = testTable.tsn.max()
            val maxNullableTimestamp = testTable.slice(maxTsnExpr).selectAll().single()[maxTsnExpr]
            assertEqualDateTime(now, maxNullableTimestamp)

            val minTsnExpr = testTable.tsn.min()
            val minNullableTimestamp = testTable.slice(minTsnExpr).selectAll().single()[minTsnExpr]
            assertEqualDateTime(now, minNullableTimestamp)
        }
    }

    @Test
    fun testSQLiteDateFieldRegression01() {
        val (tableName, columnName) = "test_table" to "date_col"
        val testTable = object : IntIdTable(tableName) {
            val dateCol = date(columnName).defaultExpression(CurrentDate)
        }

        withDb(TestDB.SQLITE) {
            // force table creation using old numeric date column instead of new text column
            val createStatement = "CREATE TABLE IF NOT EXISTS $tableName (id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "$columnName DATE DEFAULT (CURRENT_DATE) NOT NULL);"
            try {
                exec(createStatement)
                testTable.insert { }

                val year = testTable.dateCol.year()
                val month = testTable.dateCol.month()
                val day = testTable.dateCol.day()

                val result1 = testTable.slice(year, month, day).selectAll().single()
                assertEquals(today.year, result1[year])
                assertEquals(today.monthValue, result1[month])
                assertEquals(today.dayOfMonth, result1[day])

                val lastDayOfMonth = CustomDateFunction(
                    "date",
                    testTable.dateCol,
                    stringLiteral("start of month"),
                    stringLiteral("+1 month"),
                    stringLiteral("-1 day")
                )
                val nextMonth = LocalDate.of(today.year, today.monthValue, 1).plusMonths(1)
                val expectedLastDayOfMonth = nextMonth.minusDays(1)

                val result2 = testTable.slice(lastDayOfMonth).selectAll().single()
                assertEquals(expectedLastDayOfMonth, result2[lastDayOfMonth])
            } finally {
                SchemaUtils.drop(testTable)
            }
        }
    }

    @Test
    fun testSQLiteDateFieldRegression02() {
        val (tableName, eventColumn, dateColumn) = Triple("test_table", "event", "date_col")
        val testTable = object : IntIdTable(tableName) {
            val event = varchar(eventColumn, 32)
            val defaultDate = date(dateColumn).defaultExpression(CurrentDate)
        }

        withDb(TestDB.SQLITE) {
            // force table creation using old numeric date column instead of new text column
            val createStatement = "CREATE TABLE IF NOT EXISTS $tableName (id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "$eventColumn VARCHAR(32) NOT NULL, $dateColumn DATE DEFAULT (CURRENT_DATE) NOT NULL);"
            try {
                exec(createStatement)
                val eventAId = testTable.insertAndGetId {
                    it[event] = "A"
                    it[defaultDate] = LocalDate.of(2000, 12, 25)
                }
                val eventBId = testTable.insertAndGetId {
                    it[event] = "B"
                }

                val inYear2000 = testTable.defaultDate.castTo<String>(TextColumnType()) like "2000%"
                assertEquals(1, testTable.select { inYear2000 }.count())

                val todayResult1 = testTable.select { testTable.defaultDate eq today }.single()
                assertEquals(eventBId, todayResult1[testTable.id])

                testTable.update({ testTable.id eq eventAId }) {
                    it[testTable.defaultDate] = today
                }

                val todayResult2 = testTable.select { testTable.defaultDate eq today }.count()
                assertEquals(2, todayResult2)

                val twoYearsAgo = today.minusYears(2)
                val twoYearsInFuture = today.plusYears(2)
                val isWithinTwoYears = testTable.defaultDate.between(twoYearsAgo, twoYearsInFuture)
                assertEquals(2, testTable.select { isWithinTwoYears }.count())

                val yesterday = today.minusDays(1)

                testTable.deleteWhere {
                    testTable.defaultDate.day() eq dateParam(yesterday).day()
                }

                assertEquals(2, testTable.selectAll().count())
            } finally {
                SchemaUtils.drop(testTable)
            }
        }
    }

    @Test
    fun testLocalDateComparison() {
        val testTable = object : Table("test_table") {
            val created = date("created")
            val deleted = date("deleted")
        }

        withTables(testTable) {
            val mayTheFourth = LocalDate.of(2023, 5, 4)
            testTable.insert {
                it[created] = mayTheFourth
                it[deleted] = mayTheFourth
            }
            testTable.insert {
                it[created] = mayTheFourth
                it[deleted] = mayTheFourth.plusDays(1L)
            }

            val sameDateResult = testTable.select { testTable.created eq testTable.deleted }.toList()
            assertEquals(1, sameDateResult.size)
            assertEquals(mayTheFourth, sameDateResult.single()[testTable.deleted])

            val sameMonthResult = testTable.select { testTable.created.month() eq testTable.deleted.month() }.toList()
            assertEquals(2, sameMonthResult.size)

            val year2023 = if (currentDialectTest is PostgreSQLDialect) {
                // PostgreSQL requires explicit type cast to resolve function date_part
                dateParam(mayTheFourth).castTo<LocalDate>(JavaLocalDateColumnType()).year()
            } else {
                dateParam(mayTheFourth).year()
            }
            val createdIn2023 = testTable.select { testTable.created.year() eq year2023 }.toList()
            assertEquals(2, createdIn2023.size)
        }
    }

    @Test
    fun testDateTimeAsJsonB() {
        val tester = object : Table("tester") {
            val created = datetime("created")
            val modified = jsonb<ModifierData>("modified", Json.Default)
        }

        withTables(excludeSettings = TestDB.allH2TestDB + TestDB.SQLITE + TestDB.SQLSERVER + TestDB.ORACLE, tester) {
            val dateTimeNow = LocalDateTime.now()
            tester.insert {
                it[created] = dateTimeNow.minusYears(1)
                it[modified] = ModifierData(1, dateTimeNow)
            }
            tester.insert {
                it[created] = dateTimeNow.plusYears(1)
                it[modified] = ModifierData(2, dateTimeNow)
            }

            val prefix = if (currentDialectTest is PostgreSQLDialect) "" else "."

            // value extracted in same manner it is stored, a json string
            val modifiedAsString = tester.modified.jsonExtract<String>("${prefix}timestamp")
            val allModifiedAsString = tester.slice(modifiedAsString).selectAll()
            assertTrue(allModifiedAsString.all { it[modifiedAsString] == dateTimeNow.toString() })

            // PostgreSQL requires explicit type cast to timestamp for in-DB comparison
            val dateModified = if (currentDialectTest is PostgreSQLDialect) {
                tester.modified.jsonExtract<LocalDateTime>("${prefix}timestamp").castTo(JavaLocalDateTimeColumnType())
            } else {
                tester.modified.jsonExtract<LocalDateTime>("${prefix}timestamp")
            }
            val modifiedBeforeCreation = tester.select { dateModified less tester.created }.single()
            assertEquals(2, modifiedBeforeCreation[tester.modified].userId)
        }
    }
}

fun <T : Temporal> assertEqualDateTime(d1: T?, d2: T?) {
    when {
        d1 == null && d2 == null -> return
        d1 == null -> error("d1 is null while d2 is not on ${currentDialectTest.name}")
        d2 == null -> error("d1 is not null while d2 is null on ${currentDialectTest.name}")
        d1 is LocalTime && d2 is LocalTime -> {
            assertEquals(d1.toSecondOfDay(), d2.toSecondOfDay(), "Failed on seconds ${currentDialectTest.name}")
            if (d2.nano != 0) {
                assertEqualFractionalPart(d1.nano, d2.nano)
            }
        }
        d1 is LocalDateTime && d2 is LocalDateTime -> {
            assertEquals(d1.toEpochSecond(ZoneOffset.UTC), d2.toEpochSecond(ZoneOffset.UTC), "Failed on epoch seconds ${currentDialectTest.name}")
            assertEqualFractionalPart(d1.nano, d2.nano)
        }
        d1 is Instant && d2 is Instant -> {
            assertEquals(d1.epochSecond, d2.epochSecond, "Failed on epoch seconds ${currentDialectTest.name}")
            assertEqualFractionalPart(d1.nano, d2.nano)
        }
        else -> assertEquals(d1, d2, "Failed on ${currentDialectTest.name}")
    }
}

private fun assertEqualFractionalPart(nano1: Int, nano2: Int) {
    when (currentDialectTest) {
        // nanoseconds (H2, Oracle & Sqlite could be here)
        // assertEquals(nano1, nano2, "Failed on nano ${currentDialectTest.name}")
        // accurate to 100 nanoseconds
        is SQLServerDialect -> assertEquals(roundTo100Nanos(nano1), roundTo100Nanos(nano2), "Failed on 1/10th microseconds ${currentDialectTest.name}")
        // microseconds
        is H2Dialect, is MariaDBDialect, is PostgreSQLDialect, is PostgreSQLNGDialect -> assertEquals(roundToMicro(nano1), roundToMicro(nano2), "Failed on microseconds ${currentDialectTest.name}")
        is MysqlDialect ->
            if ((currentDialectTest as? MysqlDialect)?.isFractionDateTimeSupported() == true) {
                // this should be uncommented, but mysql has different microseconds between save & read
//                assertEquals(roundToMicro(nano1), roundToMicro(nano2), "Failed on microseconds ${currentDialectTest.name}")
            } else {
                // don't compare fractional part
            }
        // milliseconds
        is OracleDialect -> assertEquals(roundToMilli(nano1), roundToMilli(nano2), "Failed on milliseconds ${currentDialectTest.name}")
        is SQLiteDialect -> assertEquals(floorToMilli(nano1), floorToMilli(nano2), "Failed on milliseconds ${currentDialectTest.name}")
        else -> fail("Unknown dialect ${currentDialectTest.name}")
    }
}

private fun roundTo100Nanos(nanos: Int): Int {
    return BigDecimal(nanos).divide(BigDecimal(100), RoundingMode.HALF_UP).toInt()
}

private fun roundToMicro(nanos: Int): Int {
    return BigDecimal(nanos).divide(BigDecimal(1_000), RoundingMode.HALF_UP).toInt()
}

private fun roundToMilli(nanos: Int): Int {
    return BigDecimal(nanos).divide(BigDecimal(1_000_000), RoundingMode.HALF_UP).toInt()
}

private fun floorToMilli(nanos: Int): Int {
    return nanos / 1_000_000
}

fun equalDateTime(d1: Temporal?, d2: Temporal?) = try {
    assertEqualDateTime(d1, d2)
    true
} catch (_: Exception) {
    false
}

val today: LocalDate = LocalDate.now()

object CitiesTime : IntIdTable("CitiesTime") {
    val name = varchar("name", 50) // Column<String>
    val local_time = datetime("local_time").nullable() // Column<datetime>
}

@Serializable
data class ModifierData(
    val userId: Int,
    @Serializable(with = DateTimeSerializer::class)
    val timestamp: LocalDateTime
)

object DateTimeSerializer : KSerializer<LocalDateTime> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("LocalDateTime", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: LocalDateTime) = encoder.encodeString(value.toString())
    override fun deserialize(decoder: Decoder): LocalDateTime = LocalDateTime.parse(decoder.decodeString())
}
