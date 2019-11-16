package org.jetbrains.exposed

import org.jetbrains.exposed.sql.tests.currentDialectTest
import org.jetbrains.exposed.sql.vendors.MysqlDialect
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.temporal.Temporal
import kotlin.test.assertEquals


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