package org.jetbrains.exposed.sql.kotlin.datetime

import kotlinx.datetime.*
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.tests.DatabaseTestsBase
import org.jetbrains.exposed.sql.tests.TestDB
import org.jetbrains.exposed.sql.tests.currentDialectTest
import org.jetbrains.exposed.sql.tests.shared.assertEquals
import org.jetbrains.exposed.sql.vendors.DatabaseDialect
import org.jetbrains.exposed.sql.vendors.MysqlDialect
import org.jetbrains.exposed.sql.vendors.PostgreSQLDialect
import org.jetbrains.exposed.sql.vendors.SQLiteDialect
import org.junit.Test
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
}

fun assertEqualDateTime(d1: LocalDateTime?, d2: LocalDateTime?) {
    when {
        d1 == null && d2 == null -> return
        d1 == null && d2 != null -> error("d1 is null while d2 is not on ${currentDialectTest.name}")
        d2 == null -> error("d1 is not null while d2 is null on ${currentDialectTest.name}")
        d1 == null -> error("Impossible")
        (currentDialectTest as? MysqlDialect)?.isFractionDateTimeSupported() == false ->
            assertEquals(d1.toInstant(TimeZone.UTC).toEpochMilliseconds() / 1000, d2.toInstant(TimeZone.UTC).toEpochMilliseconds() / 1000, "Failed on ${currentDialectTest.name}")
        else -> {
            val d1Nanos = currentDialectTest.extractNanos(d1)
            val d2Nanos = currentDialectTest.extractNanos(d1)
            assertEquals(d1.second + d1Nanos, d2.second + d2Nanos, "Failed on ${currentDialectTest.name}")
        }
    }
}

//fun <T : Temporal> assertEqualDateTime(d1: T?, d2: T?) {
//    when {
//        d1 == null && d2 == null -> return
//        d1 == null && d2 != null -> error("d1 is null while d2 is not on ${currentDialectTest.name}")
//        d2 == null -> error("d1 is not null while d2 is null on ${currentDialectTest.name}")
//        d1 == null -> error("Impossible")
//        d1 is LocalDateTime && d2 is LocalDateTime && (currentDialectTest as? MysqlDialect)?.isFractionDateTimeSupported() == false ->
//            assertEquals(d1.toInstant(ZoneOffset.UTC).toEpochMilli() / 1000, d2.toInstant(ZoneOffset.UTC).toEpochMilli() / 1000, "Failed on ${currentDialectTest.name}")
//        d1 is Instant && d2 is Instant && (currentDialectTest as? MysqlDialect)?.isFractionDateTimeSupported() == false ->
//            assertEquals(d1.toEpochMilli() / 1000, d2.toEpochMilli() / 1000, "Failed on ${currentDialectTest.name}")
//        d1 is Instant && d2 is Instant -> assertEquals(d1.toEpochMilli(), d2.toEpochMilli(), "Failed on ${currentDialectTest.name}")
//        d1 is LocalTime && d2 is LocalTime && d2.nano == 0 -> assertEquals<LocalTime>(d1.withNano(0), d2, "Failed on ${currentDialectTest.name}")
//        d1 is LocalTime && d2 is LocalTime -> assertEquals<LocalTime>(d1, d2, "Failed on ${currentDialectTest.name}")
//        d1 is LocalDateTime && d2 is LocalDateTime -> {
//            val d1Nanos = currentDialectTest.extractNanos(d1)
//            val d2Nanos = currentDialectTest.extractNanos(d1)
//            assertEquals(d1.second + d1Nanos, d2.second + d2Nanos, "Failed on ${currentDialectTest.name}")
//        }
//        else -> assertEquals(d1, d2, "Failed on ${currentDialectTest.name}")
//    }
//}

private fun DatabaseDialect.extractNanos(dt: LocalDateTime) = when (this) {
    is MysqlDialect -> dt.nanosecond.toString().take(6).toInt() // 1000000 ns
    is SQLiteDialect -> 0
    is PostgreSQLDialect -> dt.nanosecond.toString().take(1).toInt() // 1 ms
    else -> dt.nanosecond
}

//fun equalDateTime(d1: Temporal?, d2: Temporal?) = try {
//    assertEqualDateTime(d1, d2)
//    true
//} catch (e: Exception) {
//    false
//}

val today: LocalDate = now().date

object CitiesTime : IntIdTable("CitiesTime") {
    val name = varchar("name", 50) // Column<String>
    val local_time = datetime("local_time").nullable() // Column<datetime>
}
