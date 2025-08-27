package org.jetbrains.exposed.v1.datetime

import kotlinx.datetime.*
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.core.datetime.DurationColumnType
import org.jetbrains.exposed.v1.core.datetime.InstantColumnType
import org.jetbrains.exposed.v1.core.datetime.LocalDateColumnType
import org.jetbrains.exposed.v1.core.datetime.LocalDateTimeColumnType
import org.jetbrains.exposed.v1.core.datetime.LocalTimeColumnType
import org.jetbrains.exposed.v1.core.datetime.OffsetDateTimeColumnType
import java.sql.Timestamp
import java.time.OffsetDateTime
import kotlin.time.Duration
import kotlin.time.Instant
import kotlinx.datetime.Instant as xInstant

/**
 * Column for storing dates, as [LocalDate].
 *
 * @sample date
 */
class KotlinLocalDateColumnType : LocalDateColumnType<LocalDate>() {
    override fun toLocalDate(value: LocalDate) = value

    override fun fromLocalDate(value: LocalDate) = value

    companion object {
        internal val INSTANCE = KotlinLocalDateColumnType()
    }
}

/**
 * Column for storing dates and times without time zone, as [LocalDateTime].
 *
 * @sample datetime
 */
class KotlinLocalDateTimeColumnType : LocalDateTimeColumnType<LocalDateTime>() {
    override fun toLocalDateTime(value: LocalDateTime) = value

    override fun fromLocalDateTime(value: LocalDateTime) = value

    override fun valueFromDB(value: Any): LocalDateTime? = when (value) {
        is LocalDateTime -> value
        else -> super.valueFromDB(value)
    }

    companion object {
        internal val INSTANCE = KotlinLocalDateTimeColumnType()
    }
}

/**
 * Column for storing times, as [LocalTime].
 *
 * @sample time
 */
class KotlinLocalTimeColumnType : LocalTimeColumnType<LocalTime>() {
    override fun toLocalTime(value: LocalTime): LocalTime = value

    override fun fromLocalTime(value: LocalTime) = value

    override fun valueFromDB(value: Any): LocalTime? = when (value) {
        is LocalTime -> value
        else -> super.valueFromDB(value)
    }

    companion object {
        internal val INSTANCE = KotlinLocalTimeColumnType()
    }
}

/**
 * Column for storing dates and times without time zone, as [kotlinx.datetime.Instant].
 *
 * @sample timestamp
 */
@Deprecated(
    "Deprecated due to usage of old kotlinx.datetime.Instant",
    replaceWith = ReplaceWith("KotlinInstantColumnType")
)
class XKotlinInstantColumnType : InstantColumnType<xInstant>() {
    override fun toInstant(value: xInstant) = value.toStdlibInstant()

    override fun fromInstant(instant: Instant) = instant.toDeprecatedInstant()

    companion object {
        internal val INSTANCE = XKotlinInstantColumnType()
    }
}

/**
 * Column for storing dates and times without time zone, as [kotlin.time.Instant].
 *
 * @sample Timestamp
 */
class KotlinInstantColumnType : InstantColumnType<Instant>() {
    override fun toInstant(value: Instant) = value

    override fun fromInstant(instant: Instant) = instant

    companion object {
        internal val INSTANCE = KotlinInstantColumnType()
    }
}

/**
 * Column for storing dates and times with time zone, as [OffsetDateTime].
 *
 * @sample timestampWithTimeZone
 */
class KotlinOffsetDateTimeColumnType : OffsetDateTimeColumnType<OffsetDateTime>() {
    override fun toOffsetDateTime(value: OffsetDateTime) = value

    override fun fromOffsetDateTime(datetime: OffsetDateTime) = datetime

    companion object {
        internal val INSTANCE = KotlinOffsetDateTimeColumnType()
    }
}

/**
 * Column for storing time-based amounts of time, as [Duration].
 *
 * @sample duration
 */
class KotlinDurationColumnType : DurationColumnType<Duration>() {
    override fun toDuration(value: Duration): Duration = value

    override fun fromDuration(value: Duration): Duration = value

    companion object {
        internal val INSTANCE = KotlinDurationColumnType()
    }
}

/**
 * A date column to store a date.
 *
 * @param name The column name
 */
fun Table.date(name: String): Column<LocalDate> = registerColumn(name, KotlinLocalDateColumnType())

/**
 * A datetime column to store both a date and a time without time zone.
 *
 * @param name The column name
 */
fun Table.datetime(name: String): Column<LocalDateTime> = registerColumn(name, KotlinLocalDateTimeColumnType())

/**
 * A time column to store a time.
 *
 * Doesn't return nanos from database.
 *
 * @param name The column name
 */
fun Table.time(name: String): Column<LocalTime> = registerColumn(name, KotlinLocalTimeColumnType())

/**
 * A timestamp column to store both a date and a time without time zone.
 *
 * @param name The column name
 */
@Deprecated(
    "Deprecated due to usage of old kotlinx.datetime.Instant. " +
        "The change caused by deprecation of Instant in the kotlinx.datetime " +
        "(see more on https://github.com/Kotlin/kotlinx-datetime?tab=readme-ov-file#deprecation-of-instant)",
    replaceWith = ReplaceWith("timestamp")
)
fun Table.xTimestamp(name: String): Column<xInstant> = registerColumn(name, XKotlinInstantColumnType())

/**
 * A timestamp column to store both a date and a time without time zone.
 *
 * @param name The column name
 */
fun Table.timestamp(name: String): Column<Instant> = registerColumn(name, KotlinInstantColumnType())

/**
 * A timestamp column to store both a date and a time with time zone.
 *
 * Note: PostgreSQL and MySQL always store the timestamp in UTC, thereby losing the original time zone. To preserve the
 * original time zone, store the time zone information in a separate column.
 *
 * @param name The column name
 */
fun Table.timestampWithTimeZone(name: String): Column<OffsetDateTime> =
    registerColumn(name, KotlinOffsetDateTimeColumnType())

/**
 * A date column to store a duration.
 *
 * @param name The column name
 */
fun Table.duration(name: String): Column<Duration> = registerColumn(name, KotlinDurationColumnType())
