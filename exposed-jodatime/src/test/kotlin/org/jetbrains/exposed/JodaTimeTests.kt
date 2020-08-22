package org.jetbrains.exposed

import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.jodatime.*
import org.jetbrains.exposed.sql.tests.DatabaseTestsBase
import org.jetbrains.exposed.sql.tests.currentDialectTest
import org.jetbrains.exposed.sql.tests.shared.assertEquals
import org.jetbrains.exposed.sql.vendors.MysqlDialect
import org.joda.time.DateTime
import org.joda.time.DateTimeZone
import org.junit.Test
import kotlin.test.assertEquals

open class JodaTimeBaseTest : DatabaseTestsBase() {
    init {
        DateTimeZone.setDefault(DateTimeZone.UTC)
    }

    @Test
    fun jodaTimeFunctions() {
        withTables(CitiesTime) {
            val now = DateTime.now()

            val cityID = CitiesTime.insertAndGetId {
                it[name] = "St. Petersburg"
                it[local_time] = now.toDateTime()
            }

            val insertedYear = CitiesTime.slice(CitiesTime.local_time.year()).select { CitiesTime.id.eq(cityID) }.single()[CitiesTime.local_time.year()]
            val insertedMonth = CitiesTime.slice(CitiesTime.local_time.month()).select { CitiesTime.id.eq(cityID) }.single()[CitiesTime.local_time.month()]
            val insertedDay = CitiesTime.slice(CitiesTime.local_time.day()).select { CitiesTime.id.eq(cityID) }.single()[CitiesTime.local_time.day()]
            val insertedHour = CitiesTime.slice(CitiesTime.local_time.hour()).select { CitiesTime.id.eq(cityID) }.single()[CitiesTime.local_time.hour()]
            val insertedMinute = CitiesTime.slice(CitiesTime.local_time.minute()).select { CitiesTime.id.eq(cityID) }.single()[CitiesTime.local_time.minute()]
            val insertedSecond = CitiesTime.slice(CitiesTime.local_time.second()).select { CitiesTime.id.eq(cityID) }.single()[CitiesTime.local_time.second()]

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

object CitiesTime : IntIdTable("CitiesTime") {
    val name = varchar("name", 50) // Column<String>
    val local_time = datetime("local_time").nullable() // Column<datetime>
}
