package org.jetbrains.exposed.v1.javatime

import org.jetbrains.exposed.v1.core.*
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.OffsetDateTime
import kotlin.time.toJavaDuration
import kotlin.time.toKotlinDuration

/**
 * Column for storing dates, as [LocalDate].
 *
 * @sample date
 */
@Suppress("MagicNumber")
class JavaLocalDateColumnType : LocalDateColumnType<LocalDate>() {
    override fun toLocalDate(value: LocalDate) = value

    override fun fromLocalDate(value: LocalDate) = value

    companion object {
        internal val INSTANCE = JavaLocalDateColumnType()
    }
}

/**
 * Column for storing dates and times without time zone, as [LocalDateTime].
 *
 * @sample datetime
 */
@Suppress("MagicNumber")
class JavaLocalDateTimeColumnType : LocalDateTimeColumnType<LocalDateTime>() {
    override fun toLocalDateTime(value: LocalDateTime) = value

    override fun fromLocalDateTime(value: LocalDateTime) = value

    companion object {
        internal val INSTANCE = JavaLocalDateTimeColumnType()
    }
}

/**
 * Column for storing times, as [LocalTime].
 *
 * @sample time
 */
class JavaLocalTimeColumnType : LocalTimeColumnType<LocalTime>() {
    override fun toLocalTime(value: LocalTime) = value

    override fun fromLocalTime(value: LocalTime) = value

    companion object {
        internal val INSTANCE = JavaLocalTimeColumnType()
    }
}

/**
 * Column for storing dates and times without time zone, as [Instant].
 *
 * @sample timestamp
 */
class JavaInstantColumnType : DatetimeColumnType<Instant>() {
    override fun toInstant(value: Instant) = value

    override fun fromInstant(instant: Instant) = instant

    companion object {
        internal val INSTANCE = JavaInstantColumnType()
    }
}

/**
 * Column for storing dates and times with time zone, as [OffsetDateTime].
 *
 * @sample timestampWithTimeZone
 */
class JavaOffsetDateTimeColumnType : OffsetDateTimeColumnType<OffsetDateTime>() {
    override fun toOffsetDateTime(value: OffsetDateTime) = value

    override fun fromOffsetDateTime(datetime: OffsetDateTime) = datetime

    companion object {
        internal val INSTANCE = JavaOffsetDateTimeColumnType()
    }
}

/**
 * Column for storing time-based amounts of time, as [Duration].
 *
 * @sample duration
 */
class JavaDurationColumnType : DurationColumnType<java.time.Duration>() {
    override fun toDuration(value: Duration): kotlin.time.Duration = value.toKotlinDuration()

    override fun fromDuration(value: kotlin.time.Duration): Duration = value.toJavaDuration()

    companion object {
        internal val INSTANCE = JavaDurationColumnType()
    }
}

/**
 * A date column to store a date.
 *
 * @param name The column name
 */
fun Table.date(name: String): Column<LocalDate> = registerColumn(name, JavaLocalDateColumnType())

/**
 * A datetime column to store both a date and a time without time zone.
 *
 * @param name The column name
 */
fun Table.datetime(name: String): Column<LocalDateTime> = registerColumn(name, JavaLocalDateTimeColumnType())

/**
 * A time column to store a time.
 *
 * Doesn't return nanos from database.
 *
 * @param name The column name
 * @author Maxim Vorotynsky
 */
fun Table.time(name: String): Column<LocalTime> = registerColumn(name, JavaLocalTimeColumnType())

/**
 * A timestamp column to store both a date and a time without time zone.
 *
 * @param name The column name
 */
fun Table.timestamp(name: String): Column<Instant> = registerColumn(name, JavaInstantColumnType())

/**
 * A timestamp column to store both a date and a time with time zone.
 *
 * Note: PostgreSQL and MySQL always store the timestamp in UTC, thereby losing the original time zone. To preserve the
 * original time zone, store the time zone information in a separate column.
 *
 * @param name The column name
 */
fun Table.timestampWithTimeZone(name: String): Column<OffsetDateTime> =
    registerColumn(name, JavaOffsetDateTimeColumnType())

/**
 * A date column to store a duration.
 *
 * @param name The column name
 */
fun Table.duration(name: String): Column<Duration> = registerColumn(name, JavaDurationColumnType())
