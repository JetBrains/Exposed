package org.jetbrains.exposed.v1.tests.shared.dml

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.between
import org.jetbrains.exposed.v1.datetime.KotlinLocalDateColumnType
import org.jetbrains.exposed.v1.datetime.date
import org.jetbrains.exposed.v1.datetime.datetime
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.tests.DatabaseTestsBase
import org.jetbrains.exposed.v1.tests.TestDB
import org.jetbrains.exposed.v1.tests.shared.assertEquals
import org.junit.jupiter.api.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

private object DateRange1459Table : Table("date_range_1459") {
    val dateCol = date("d")
    val value = varchar("v", 32)
}

private object DateTimeRange1459Table : Table("datetime_range_1459") {
    val datetimeCol = datetime("dt")
    val value = varchar("v", 32)
}

/**
 * Date and datetime values that cannot be represented as a JDBC date/timestamp parameter (milliseconds since the
 * epoch) used to be bound with a silently substituted value: the database received a different value than the one
 * supplied, so a predicate like `between(from, LocalDate.MAX.withDayOfMonth(1))` matched unrelated rows or nothing
 * at all, depending on the driver. Such values are now rejected with a clear error at binding time.
 *
 * See https://github.com/JetBrains/Exposed/issues/1459
 */
class DateParameterRangeTests : DatabaseTestsBase() {
    @Test
    fun testBetweenWithDateBeyondJdbcRangeIsRejectedWithClearError() {
        // The value is rejected before reaching the database. SQLite binds dates as strings and has no such limit.
        withTables(listOf(TestDB.SQLITE), DateRange1459Table) {
            seedDateRows()

            val exception = assertFailsWith<IllegalArgumentException> {
                DateRange1459Table
                    .selectAll()
                    .where { DateRange1459Table.dateCol.between(LocalDate(2022, 4, 1), LocalDate(999999999, 12, 1)) }
                    .toList()
            }

            val message = exception.message.orEmpty()
            assertContains(message, "999999999-12-01")
            assertContains(message, "out of range")
            assertContains(message, "DATE")
        }
    }

    @Test
    fun testBetweenWithDateTimeBeyondJdbcRangeIsRejectedWithClearError() {
        withTables(listOf(TestDB.SQLITE), DateTimeRange1459Table) {
            seedDateTimeRows()

            val exception = assertFailsWith<IllegalArgumentException> {
                DateTimeRange1459Table
                    .selectAll()
                    .where {
                        DateTimeRange1459Table.datetimeCol.between(
                            LocalDateTime(2022, 4, 1, 0, 0),
                            LocalDateTime(999999999, 12, 31, 23, 59, 59, 999_999_999)
                        )
                    }
                    .toList()
            }

            val message = exception.message.orEmpty()
            assertContains(message, "999999999-12-31")
            assertContains(message, "out of range")
        }
    }

    @Test
    fun testBetweenWithLargeButRepresentableDateReturnsAllRows() {
        // Year 294276 is the workaround bound from issue #1459: large, but still representable as a JDBC date
        // parameter. Comparing against it also requires a database whose DATE range is that wide, so this is
        // restricted to H2.
        withDb(TestDB.ALL_H2_V2) {
            SchemaUtils.create(DateRange1459Table)
            try {
                seedDateRows()

                val rows = DateRange1459Table
                    .selectAll()
                    .where { DateRange1459Table.dateCol.between(LocalDate(2022, 4, 1), LocalDate(294276, 9, 1)) }
                    .toList()

                assertEquals(3, rows.size)
            } finally {
                SchemaUtils.drop(DateRange1459Table)
            }
        }
    }

    @Test
    fun testBetweenWithOrdinaryDatesIsUnaffected() {
        withTables(DateRange1459Table) {
            seedDateRows()

            val allRows = DateRange1459Table
                .selectAll()
                .where { DateRange1459Table.dateCol.between(LocalDate(2022, 4, 1), LocalDate(2022, 6, 30)) }
                .toList()
            assertEquals(3, allRows.size)

            val firstMonthOnly = DateRange1459Table
                .selectAll()
                .where { DateRange1459Table.dateCol.between(LocalDate(2022, 4, 1), LocalDate(2022, 4, 30)) }
                .toList()
            assertEquals(1, firstMonthOnly.size)
        }
    }

    @Test
    fun testDateAdjacentToJdbcRangeBoundaryIsStillAccepted() {
        // The largest date that a JDBC date parameter can hold (292278994-08-17T15:12:55.807Z in UTC) must bind and
        // compare unchanged. Only H2 has a DATE range wide enough to verify this end to end.
        withDb(TestDB.ALL_H2_V2) {
            SchemaUtils.create(DateRange1459Table)
            try {
                seedDateRows()
                DateRange1459Table.insert {
                    it[dateCol] = LocalDate(292278994, 8, 17)
                    it[value] = "100"
                }

                val rows = DateRange1459Table
                    .selectAll()
                    .where { DateRange1459Table.dateCol.between(LocalDate(2022, 4, 1), LocalDate(292278994, 8, 17)) }
                    .toList()

                assertEquals(4, rows.size)
            } finally {
                SchemaUtils.drop(DateRange1459Table)
            }
        }
    }

    @Test
    fun testColumnTypeRejectsDatesBeyondJdbcMillisRange() {
        // The last representable day is 292278994-08-17 in UTC, the first one -292275055-05-16. SQLite binds dates
        // as strings, so its conversion path is not checked here.
        withDb(TestDB.ALL - TestDB.SQLITE) {
            val columnType = KotlinLocalDateColumnType()

            assertFailsWith<IllegalArgumentException> { columnType.notNullValueToDB(LocalDate(292278994, 12, 31)) }
            assertFailsWith<IllegalArgumentException> { columnType.notNullValueToDB(LocalDate(292278994, 8, 18)) }
            assertFailsWith<IllegalArgumentException> { columnType.notNullValueToDB(LocalDate(-292275055, 1, 1)) }

            // Days adjacent to both ends of the range must survive the conversion without being replaced.
            val upperBoundDate = LocalDate(292278994, 8, 17)
            val upperBound = columnType.notNullValueToDB(upperBoundDate) as java.sql.Date
            assertEquals(upperBoundDate.atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds(), upperBound.time)

            val lowerBoundDate = LocalDate(-292275055, 6, 1)
            val lowerBound = columnType.notNullValueToDB(lowerBoundDate) as java.sql.Date
            assertEquals(lowerBoundDate.atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds(), lowerBound.time)
        }
    }

    private fun seedDateRows() {
        listOf(LocalDate(2022, 4, 1), LocalDate(2022, 5, 1), LocalDate(2022, 6, 1)).forEach { d ->
            DateRange1459Table.insert {
                it[dateCol] = d
                it[value] = "100"
            }
        }
    }

    private fun seedDateTimeRows() {
        listOf(
            LocalDateTime(2022, 4, 1, 12, 0),
            LocalDateTime(2022, 5, 1, 12, 0),
            LocalDateTime(2022, 6, 1, 12, 0)
        ).forEach { dt ->
            DateTimeRange1459Table.insert {
                it[datetimeCol] = dt
                it[value] = "100"
            }
        }
    }
}
