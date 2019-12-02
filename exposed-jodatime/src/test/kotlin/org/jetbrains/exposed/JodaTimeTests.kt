package org.jetbrains.exposed

import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.jodatime.*
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.tests.DatabaseTestsBase
import org.jetbrains.exposed.sql.tests.TestDB
import org.jetbrains.exposed.sql.tests.currentDialectTest
import org.jetbrains.exposed.sql.tests.shared.assertEquals
import org.jetbrains.exposed.sql.vendors.MysqlDialect
import org.joda.time.DateTime
import org.joda.time.DateTimeZone
import org.joda.time.LocalDateTime
import org.junit.Test
import kotlin.test.assertEquals

open class JodaTimeBaseTest : DatabaseTestsBase() {
    init {
        DateTimeZone.setDefault(DateTimeZone.UTC)
    }

    @Test
    fun jodaTimeFunctions() {
        withTables(listOf(TestDB.SQLITE), Cities) {
            SchemaUtils.create(Cities)

            val now = LocalDateTime.now()

            val cityID = Cities.insert {
                it[name] = "St. Petersburg"
                it[local_time] = now.toDateTime()
            } get Cities.id

            val insertedYear = Cities.slice(Cities.local_time.year()).select { Cities.id.eq(cityID) }.single()[Cities.local_time.year()]
            val insertedMonth = Cities.slice(Cities.local_time.month()).select { Cities.id.eq(cityID) }.single()[Cities.local_time.month()]
            val insertedDay = Cities.slice(Cities.local_time.day()).select { Cities.id.eq(cityID) }.single()[Cities.local_time.day()]
            val insertedHour = Cities.slice(Cities.local_time.hour()).select { Cities.id.eq(cityID) }.single()[Cities.local_time.hour()]
            val insertedMinute = Cities.slice(Cities.local_time.minute()).select { Cities.id.eq(cityID) }.single()[Cities.local_time.minute()]
            val insertedSecond = Cities.slice(Cities.local_time.second()).select { Cities.id.eq(cityID) }.single()[Cities.local_time.second()]

            assertEquals(now.year, insertedYear)
            assertEquals(now.monthOfYear, insertedMonth)
            assertEquals(now.dayOfMonth, insertedDay)
            assertEquals(now.hourOfDay, insertedHour)
            assertEquals(now.minuteOfHour, insertedMinute)
            assertEquals(now.secondOfMinute, insertedSecond)
        }
    }
}

fun assertEqualDateTime(d1: DateTime?, d2: DateTime?) {
    when{
        d1 == null && d2 == null -> return
        d1 == null && d2 != null -> error("d1 is null while d2 is not on ${currentDialectTest.name}")
        d2 == null -> error ("d1 is not null while d2 is null on ${currentDialectTest.name}")
        d1 == null -> error("Impossible")
        // Mysql doesn't support millis prior 5.6.4
        (currentDialectTest as? MysqlDialect)?.isFractionDateTimeSupported() == false ->
            assertEquals(d1.millis / 1000, d2.millis / 1000,  "Failed on ${currentDialectTest.name}")
        else -> assertEquals(d1.millis, d2.millis,   "Failed on ${currentDialectTest.name}")
    }
}

fun equalDateTime(d1: DateTime?, d2: DateTime?) = try {
    assertEqualDateTime(d1, d2)
    true
} catch (e: Exception) {
    false
}

val today: DateTime = DateTime.now().withTimeAtStartOfDay()

object Cities : Table() {
    val id = integer("cityId").autoIncrement("cities_seq").primaryKey() // PKColumn<Int>
    val name = varchar("name", 50) // Column<String>
    val local_time = datetime("local_time").nullable() // Column<datetime>
}