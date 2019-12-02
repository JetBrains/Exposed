package org.jetbrains.exposed

import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.`java-time`.*
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.tests.DatabaseTestsBase
import org.jetbrains.exposed.sql.tests.TestDB
import org.jetbrains.exposed.sql.tests.currentDialectTest
import org.jetbrains.exposed.sql.tests.shared.assertEquals
import org.jetbrains.exposed.sql.vendors.MysqlDialect
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.temporal.Temporal
import kotlin.test.assertEquals

open class JavaTimeBaseTest : DatabaseTestsBase() {

    @Test
    fun javaTimeFunctions() {
        withTables(listOf(TestDB.SQLITE), Cities) {
            SchemaUtils.create(Cities)

            val now = LocalDateTime.now()

            val cityID = Cities.insert {
                it[name] = "Tunisia"
                it[local_time] = now
            } get Cities.id

            val insertedYear = Cities.slice(Cities.local_time.year()).select { Cities.id.eq(cityID) }.single()[Cities.local_time.year()]
            val insertedMonth = Cities.slice(Cities.local_time.month()).select { Cities.id.eq(cityID) }.single()[Cities.local_time.month()]
            val insertedDay = Cities.slice(Cities.local_time.day()).select { Cities.id.eq(cityID) }.single()[Cities.local_time.day()]
            val insertedHour = Cities.slice(Cities.local_time.hour()).select { Cities.id.eq(cityID) }.single()[Cities.local_time.hour()]
            val insertedMinute = Cities.slice(Cities.local_time.minute()).select { Cities.id.eq(cityID) }.single()[Cities.local_time.minute()]
            val insertedSecond = Cities.slice(Cities.local_time.second()).select { Cities.id.eq(cityID) }.single()[Cities.local_time.second()]

            assertEquals(now.year, insertedYear)
            assertEquals(now.month.value, insertedMonth)
            assertEquals(now.dayOfMonth, insertedDay)
            assertEquals(now.hour, insertedHour)
            assertEquals(now.minute, insertedMinute)
            assertEquals(now.second, insertedSecond)
        }
    }
}

fun <T:Temporal> assertEqualDateTime(d1: T?, d2: T?) {
    when{
        d1 == null && d2 == null -> return
        d1 == null && d2 != null -> error("d1 is null while d2 is not on ${currentDialectTest.name}")
        d2 == null -> error ("d1 is not null while d2 is null on ${currentDialectTest.name}")
        d1 == null -> error("Impossible")
        d1 is LocalDateTime && d2 is LocalDateTime && (currentDialectTest as? MysqlDialect)?.isFractionDateTimeSupported() == false ->
            assertEquals(d1.toInstant(ZoneOffset.UTC).toEpochMilli() / 1000, d2.toInstant(ZoneOffset.UTC).toEpochMilli() / 1000,  "Failed on ${currentDialectTest.name}")
        else -> assertEquals(d1, d2,   "Failed on ${currentDialectTest.name}")
    }
}

fun equalDateTime(d1: Temporal?, d2: Temporal?) = try {
    assertEqualDateTime(d1, d2)
    true
} catch (e: Exception) {
    false
}

val today: LocalDate = LocalDate.now()

object Cities : Table() {
    val id = integer("cityId").autoIncrement("cities_seq").primaryKey() // PKColumn<Int>
    val name = varchar("name", 50) // Column<String>
    val local_time = datetime("local_time").nullable() // Column<datetime>
}