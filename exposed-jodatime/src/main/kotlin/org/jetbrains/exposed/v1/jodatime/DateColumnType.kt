package org.jetbrains.exposed.v1.jodatime

import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.LocalDateColumnType
import org.jetbrains.exposed.v1.core.LocalDateTimeColumnType
import org.jetbrains.exposed.v1.core.LocalTimeColumnType
import org.jetbrains.exposed.v1.core.OffsetDateTimeColumnType
import org.jetbrains.exposed.v1.core.Table
import org.joda.time.DateTime
import org.joda.time.DateTimeZone
import org.joda.time.LocalTime
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset

class JodaLocalDateColumnType : LocalDateColumnType<DateTime>() {
    override fun toLocalDate(value: DateTime): LocalDate {
        return LocalDate.of(
            value.year,
            value.monthOfYear,
            value.dayOfMonth
        )
    }

    override fun fromLocalDate(value: LocalDate): DateTime {
        return DateTime(
            value.year,
            value.monthValue,
            value.dayOfMonth,
            0, 0
        )
    }

    override fun valueFromDB(value: Any) = when (value) {
        is DateTime -> value
        else -> super.valueFromDB(value)
    }
}

/**
 * Column for storing dates, as [DateTime]. If [time] is set to `true`, both date and time data is stored.
 *
 * @sample datetime
 */
class JodaLocalDateTimeColumnType : LocalDateTimeColumnType<DateTime>() {
    override fun toLocalDateTime(value: DateTime): LocalDateTime {
        return value.toDate()
            .toInstant()
            .atZone(ZoneId.systemDefault())
            .toLocalDateTime()
    }

    override fun fromLocalDateTime(value: LocalDateTime): DateTime {
        return DateTime(value.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli())
    }

    override fun valueFromDB(value: Any): DateTime? = when (value) {
        is DateTime -> value
        else -> super.valueFromDB(value)
    }
}

/**
 * Column for storing times, as [LocalTime].
 *
 * @sample time
 */
class JodaLocalTimeColumnType : LocalTimeColumnType<LocalTime>() {
    override fun toLocalTime(value: LocalTime): java.time.LocalTime = java.time.LocalTime.of(
        value.hourOfDay,
        value.minuteOfHour,
        value.secondOfMinute
    )

    @Suppress("MagicNumber")
    override fun fromLocalTime(value: java.time.LocalTime): LocalTime = LocalTime(
        value.hour,
        value.minute,
        value.second,
        (value.nano / 1_000_000) % 1000
    )
}

/**
 * Column for storing dates and times with time zone, as [DateTime].
 *
 * @sample timestampWithTimeZone
 */
class DateTimeWithTimeZoneColumnType : OffsetDateTimeColumnType<DateTime>() {
    @Suppress("MagicNumber")
    override fun toOffsetDateTime(value: DateTime): OffsetDateTime {
        val offsetSeconds: Int = value.zone.getOffset(value) / 1000
        val zoneOffset = ZoneOffset.ofTotalSeconds(offsetSeconds)

        return OffsetDateTime.of(
            value.year,
            value.monthOfYear,
            value.dayOfMonth,
            value.hourOfDay,
            value.minuteOfHour,
            value.secondOfMinute,
            value.millisOfSecond * 1000000,
            zoneOffset
        )
    }

    @Suppress("MagicNumber")
    override fun fromOffsetDateTime(datetime: OffsetDateTime): DateTime {
        val epochMilli: Long = datetime.toInstant().toEpochMilli()
        val dateTimeZone = DateTimeZone.forOffsetMillis(datetime.offset.totalSeconds * 1000)

        return DateTime(epochMilli, dateTimeZone)
    }

    override fun valueFromDB(value: Any) = when (value) {
        is DateTime -> value
        else -> super.valueFromDB(value)
    }
}

/**
 * A date column to store a date.
 *
 * @param name The column name
 */
fun Table.date(name: String): Column<DateTime> = registerColumn(name, JodaLocalDateColumnType())

/**
 * A datetime column to store both a date and a time.
 *
 * @param name The column name
 */
fun Table.datetime(name: String): Column<DateTime> = registerColumn(name, JodaLocalDateTimeColumnType())

/**
 * A time column to store a time.
 *
 * @param name The column name
 */
fun Table.time(name: String): Column<LocalTime> = registerColumn(name, JodaLocalTimeColumnType())

/**
 * A timestamp column to store both a date and a time with time zone.
 *
 * Note: PostgreSQL and MySQL always store the timestamp in UTC, thereby losing the original time zone. To preserve the
 * original time zone, store the time zone information in a separate column.
 *
 * @param name The column name
 */
fun Table.timestampWithTimeZone(name: String): Column<DateTime> = registerColumn(name, DateTimeWithTimeZoneColumnType())
