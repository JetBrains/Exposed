package org.jetbrains.exposed.sql.kotlin.datetime

import kotlinx.datetime.*
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.tests.DatabaseTestsBase
import org.jetbrains.exposed.sql.tests.TestDB
import org.jetbrains.exposed.sql.tests.currentDialectTest
import org.jetbrains.exposed.sql.tests.shared.assertEquals
import org.jetbrains.exposed.sql.vendors.*
import org.junit.Assert.fail
import org.junit.Test
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.ZoneOffset
import kotlin.test.assertEquals

open class KotlinTimeBaseTest : DatabaseTestsBase() {

    @Test
    fun kotlinTimeFunctions() {
        withTables(CitiesTime) {
            val now = now()

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
        val TestDate = object : IntIdTable("TestDate") {
            val time = datetime("time").defaultExpression(CurrentDateTime)
        }

        withDb(TestDB.SQLITE) {
            try {
                exec("CREATE TABLE IF NOT EXISTS TestDate (id INTEGER PRIMARY KEY AUTOINCREMENT, \"time\" NUMERIC DEFAULT (CURRENT_TIMESTAMP) NOT NULL);")
                TestDate.insert { }
                val year = TestDate.time.year()
                val month = TestDate.time.month()
                val day = TestDate.time.day()
                val hour = TestDate.time.hour()
                val minute = TestDate.time.minute()

                val result = TestDate.slice(year, month, day, hour, minute).selectAll().single()

                val now = now()
                assertEquals(now.year, result[year])
                assertEquals(now.monthNumber, result[month])
                assertEquals(now.dayOfMonth, result[day])
                assertEquals(now.hour, result[hour])
                assertEquals(now.minute, result[minute])
            } finally {
                SchemaUtils.drop(TestDate)
            }
        }
    }

    @Test
    fun `test storing LocalDateTime with nanos`() {
        val TestDate = object : IntIdTable("TestLocalDateTime") {
            val time = datetime("time")
        }
        withTables(TestDate) {
            val dateTimeWithNanos = Clock.System.now().plus(DateTimeUnit.NANOSECOND * 123).toLocalDateTime(TimeZone.currentSystemDefault())
            TestDate.insert {
                it[TestDate.time] = dateTimeWithNanos
            }

            val dateTimeFromDB = TestDate.selectAll().single()[TestDate.time]
            assertEqualDateTime(dateTimeWithNanos, dateTimeFromDB)
        }
    }

    @Test
    fun `test selecting Instant using expressions`() {
        val TestTable = object : Table() {
            val ts = timestamp("ts")
            val tsn = timestamp("tsn").nullable()
        }

        val now = Clock.System.now()

        withTables(TestTable) {
            TestTable.insert {
                it[ts] = now
                it[tsn] = now
            }

            val maxTsExpr = TestTable.ts.max()
            val maxTimestamp = TestTable.slice(maxTsExpr).selectAll().single()[maxTsExpr]
            assertEqualDateTime(now, maxTimestamp)

            val minTsExpr = TestTable.ts.min()
            val minTimestamp = TestTable.slice(minTsExpr).selectAll().single()[minTsExpr]
            assertEqualDateTime(now, minTimestamp)

            val maxTsnExpr = TestTable.tsn.max()
            val maxNullableTimestamp = TestTable.slice(maxTsnExpr).selectAll().single()[maxTsnExpr]
            assertEqualDateTime(now, maxNullableTimestamp)

            val minTsnExpr = TestTable.tsn.min()
            val minNullableTimestamp = TestTable.slice(minTsnExpr).selectAll().single()[minTsnExpr]
            assertEqualDateTime(now, minNullableTimestamp)
        }
    }
}

fun <T> assertEqualDateTime(d1: T?, d2: T?) {
    when {
        d1 == null && d2 == null -> return
        d1 == null -> error("d1 is null while d2 is not on ${currentDialectTest.name}")
        d2 == null -> error("d1 is not null while d2 is null on ${currentDialectTest.name}")
        d1 is LocalTime && d2 is LocalTime -> {
            assertEquals(d1.toSecondOfDay(), d2.toSecondOfDay(), "Failed on seconds ${currentDialectTest.name}")
            if (d2.nanosecond != 0) {
                assertEqualFractionalPart(d1.nanosecond, d2.nanosecond)
            }
        }

        d1 is LocalDateTime && d2 is LocalDateTime -> {
            assertEquals(
                d1.toJavaLocalDateTime().toEpochSecond(ZoneOffset.UTC),
                d2.toJavaLocalDateTime().toEpochSecond(ZoneOffset.UTC),
                "Failed on epoch seconds ${currentDialectTest.name}"
            )
            assertEqualFractionalPart(d1.nanosecond, d2.nanosecond)
        }

        d1 is Instant && d2 is Instant -> {
            assertEquals(d1.epochSeconds, d2.epochSeconds, "Failed on epoch seconds ${currentDialectTest.name}")
            assertEqualFractionalPart(d1.nanosecondsOfSecond, d2.nanosecondsOfSecond)
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

val today: LocalDate = now().date

object CitiesTime : IntIdTable("CitiesTime") {
    val name = varchar("name", 50) // Column<String>
    val local_time = datetime("local_time").nullable() // Column<datetime>
}
