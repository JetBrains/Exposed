package org.jetbrains.exposed

import org.jetbrains.exposed.sql.tests.DatabaseTestsBase
import org.jetbrains.exposed.sql.tests.currentDialectTest
import org.jetbrains.exposed.sql.vendors.MysqlDialect
import org.joda.time.DateTime
import org.joda.time.DateTimeZone
import kotlin.test.assertEquals

open class JodaTimeBaseTest : DatabaseTestsBase() {
    init {
        DateTimeZone.setDefault(DateTimeZone.UTC)
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