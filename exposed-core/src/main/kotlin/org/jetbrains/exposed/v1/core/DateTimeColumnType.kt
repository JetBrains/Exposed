package org.jetbrains.exposed.v1.core

import org.jetbrains.exposed.v1.core.statements.api.RowApi
import org.jetbrains.exposed.v1.core.transactions.CoreTransactionManager
import org.jetbrains.exposed.v1.core.vendors.H2Dialect
import org.jetbrains.exposed.v1.core.vendors.MariaDBDialect
import org.jetbrains.exposed.v1.core.vendors.MysqlDialect
import org.jetbrains.exposed.v1.core.vendors.OracleDialect
import org.jetbrains.exposed.v1.core.vendors.PostgreSQLDialect
import org.jetbrains.exposed.v1.core.vendors.SQLiteDialect
import org.jetbrains.exposed.v1.core.vendors.currentDialect
import org.jetbrains.exposed.v1.core.vendors.h2Mode
import java.sql.Date
import java.sql.Timestamp
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset.UTC
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.time.temporal.ChronoField
import java.util.Locale
import java.util.TimeZone
import kotlin.time.Duration
import kotlin.time.Duration.Companion.nanoseconds

private val DEFAULT_TIME_ZONE
    get() = TimeZone.getDefault()

private val MYSQL_FRACTION_TIMESTAMP_STRING_FORMATTER_NOTZ by lazy {
    DateTimeFormatter.ofPattern(
        "yyyy-MM-dd HH:mm:ss.SSSSSS",
        Locale.ROOT
    )
}

private val MYSQL_FRACTION_TIMESTAMP_STRING_FORMATTER
    get() = MYSQL_FRACTION_TIMESTAMP_STRING_FORMATTER_NOTZ.withZone(ZoneId.systemDefault())

private val MYSQL_TIMESTAMP_STRING_FORMATTER by lazy {
    DateTimeFormatter.ofPattern(
        "yyyy-MM-dd HH:mm:ss",
        Locale.ROOT
    ).withZone(UTC)
}

private val SQLITE_AND_ORACLE_TIMESTAMP_STRING_FORMATTER_NOTZ by lazy {
    DateTimeFormatter.ofPattern(
        "yyyy-MM-dd HH:mm:ss.SSS",
        Locale.ROOT
    )
}

private val SQLITE_AND_ORACLE_TIMESTAMP_STRING_FORMATTER
    get() = SQLITE_AND_ORACLE_TIMESTAMP_STRING_FORMATTER_NOTZ.withZone(ZoneId.systemDefault())

private fun oracleTimestampLiteral(instant: Instant) =
    "TO_TIMESTAMP('${SQLITE_AND_ORACLE_TIMESTAMP_STRING_FORMATTER.format(instant)}', 'YYYY-MM-DD HH24:MI:SS.FF3')"

private val DEFAULT_TIMESTAMP_STRING_FORMATTER by lazy {
    DateTimeFormatter.ISO_LOCAL_DATE_TIME.withLocale(Locale.ROOT).withZone(UTC)
}

private val SQLITE_OFFSET_DATE_TIME_FORMATTER by lazy {
    DateTimeFormatterBuilder()
        .appendPattern("yyyy-MM-dd HH:mm:ss")
        .optionalStart()
        .appendPattern(".SSS")
        .optionalEnd()
        .optionalStart()
        .appendPattern("XXX")
        .optionalEnd()
        .toFormatter()
        .withLocale(Locale.ROOT)
}

private val MYSQL_OFFSET_DATE_TIME_FORMATTER by lazy {
    DateTimeFormatter.ofPattern(
        "yyyy-MM-dd HH:mm:ss.SSSSSS[xxx]",
        Locale.ROOT
    )
}

// Example result: 2023-07-07 14:42:29.343789 +02:00
private val ORACLE_OFFSET_DATE_TIME_FORMATTER by lazy {
    DateTimeFormatter.ofPattern(
        "yyyy-MM-dd HH:mm:ss.SSSSSS [xxx]",
        Locale.ROOT
    )
}

private fun oracleDateTimeWithTimezoneLiteral(dateTime: OffsetDateTime) =
    "TO_TIMESTAMP_TZ('${dateTime.format(ORACLE_OFFSET_DATE_TIME_FORMATTER)}', 'YYYY-MM-DD HH24:MI:SS.FF6 TZH:TZM')"

private val DEFAULT_OFFSET_DATE_TIME_FORMATTER by lazy {
    DateTimeFormatter.ISO_OFFSET_DATE_TIME.withLocale(Locale.ROOT)
}

// Example result: 2023-07-07 14:42:29.343
private val POSTGRESQL_OFFSET_DATE_TIME_AS_DEFAULT_FORMATTER by lazy {
    DateTimeFormatterBuilder()
        .parseCaseInsensitive()
        .append(DateTimeFormatter.ISO_LOCAL_DATE)
        .appendLiteral(' ')
        .append(DateTimeFormatter.ISO_LOCAL_TIME)
        .toFormatter(Locale.ROOT)
}

private val MYSQL_OFFSET_DATE_TIME_AS_DEFAULT_FORMATTER by lazy {
    DateTimeFormatter.ofPattern(
        "yyyy-MM-dd HH:mm:ss.SSSSSS",
        Locale.ROOT
    ).withZone(ZoneId.of("UTC"))
}

private val DEFAULT_DATE_STRING_FORMATTER_NOTZ by lazy {
    DateTimeFormatter.ISO_LOCAL_DATE.withLocale(Locale.ROOT)
}

private val DEFAULT_DATE_STRING_FORMATTER
    get() = DEFAULT_DATE_STRING_FORMATTER_NOTZ.withZone(ZoneId.systemDefault())

private fun oracleDateLiteral(instant: Instant) =
    "TO_DATE('${DEFAULT_DATE_STRING_FORMATTER.format(instant)}', 'YYYY-MM-DD')"

private val SQLITE_AND_ORACLE_DATE_TIME_STRING_FORMATTER_NOTZ by lazy {
    DateTimeFormatter.ofPattern(
        "yyyy-MM-dd HH:mm:ss.SSS",
        Locale.ROOT
    )
}

private val SQLITE_AND_ORACLE_DATE_TIME_STRING_FORMATTER
    get() = SQLITE_AND_ORACLE_DATE_TIME_STRING_FORMATTER_NOTZ.withZone(ZoneId.systemDefault())

private fun oracleDateTimeLiteral(instant: Instant) =
    "TO_TIMESTAMP('${SQLITE_AND_ORACLE_DATE_TIME_STRING_FORMATTER.format(instant)}', 'YYYY-MM-DD HH24:MI:SS.FF3')"

private val MYSQL_FRACTION_DATE_TIME_STRING_FORMATTER_NOTZ by lazy {
    DateTimeFormatter.ofPattern(
        "yyyy-MM-dd HH:mm:ss.SSSSSS"
    )
}

private val MYSQL_FRACTION_DATE_TIME_STRING_FORMATTER
    get() = MYSQL_FRACTION_DATE_TIME_STRING_FORMATTER_NOTZ.withZone(ZoneId.systemDefault())

private val MYSQL_DATE_TIME_STRING_FORMATTER_NOTZ by lazy {
    DateTimeFormatter.ofPattern(
        "yyyy-MM-dd HH:mm:ss",
        Locale.ROOT
    )
}

private val MYSQL_DATE_TIME_STRING_FORMATTER
    get() = MYSQL_DATE_TIME_STRING_FORMATTER_NOTZ.withZone(ZoneId.systemDefault())

private val DEFAULT_DATE_TIME_STRING_FORMATTER_NOTZ by lazy {
    DateTimeFormatter.ISO_LOCAL_DATE_TIME.withLocale(Locale.ROOT)
}

private val DEFAULT_DATE_TIME_STRING_FORMATTER
    get() = DEFAULT_DATE_TIME_STRING_FORMATTER_NOTZ.withZone(ZoneId.systemDefault())

private val ORACLE_TIME_STRING_FORMATTER by lazy {
    DateTimeFormatter.ofPattern(
        "1970-01-01 HH:mm:ss",
        Locale.ROOT
    ).withZone(UTC)
}

private val DEFAULT_TIME_STRING_FORMATTER_NOTZ by lazy {
    DateTimeFormatter.ISO_LOCAL_TIME.withLocale(Locale.ROOT).withZone(ZoneId.systemDefault())
}

private val DEFAULT_TIME_STRING_FORMATTER
    get() = DEFAULT_TIME_STRING_FORMATTER_NOTZ.withZone(ZoneId.systemDefault())

private val MYSQL_TIME_AS_DEFAULT_STRING_FORMATTER_NOTZ by lazy {
    DateTimeFormatter.ofPattern(
        "HH:mm:ss",
        Locale.ROOT
    )
}

private val MYSQL_TIME_AS_DEFAULT_STRING_FORMATTER
    get() = MYSQL_TIME_AS_DEFAULT_STRING_FORMATTER_NOTZ.withZone(ZoneId.systemDefault())

/**
 * Base column type for storing date values without time components.
 *
 * This abstract class provides the foundation for date-only columns across different database dialects.
 * It handles the conversion between application date types and database representations, ensuring
 * proper formatting and parsing based on the current database dialect.
 *
 * @param T The application-specific date type (e.g., [LocalDate], kotlinx.datetime.LocalDate)
 * @see IDateColumnType
 * @see KotlinLocalDateColumnType
 */
abstract class LocalDateColumnType<T> : ColumnType<T>(), IDateColumnType {
    abstract fun toLocalDate(value: T): LocalDate

    abstract fun fromLocalDate(value: LocalDate): T

    override val hasTimePart: Boolean = false

    override fun sqlType(): String = currentDialect.dataTypeProvider.dateType()

    override fun nonNullValueToString(value: T & Any): String {
        val localDate = toLocalDate(value)

        val instant = Instant.ofEpochMilli(localDate.atStartOfDayIn(DEFAULT_TIME_ZONE).toEpochMilli())
        if (currentDialect is OracleDialect) {
            return oracleDateLiteral(instant)
        }
        return "'${DEFAULT_DATE_STRING_FORMATTER.format(instant)}'"
    }

    private fun localDateValueFromDB(value: Any): LocalDate = when (value) {
        is LocalDate -> value
        is java.sql.Date -> longToLocalDate(value.time)
        is java.sql.Timestamp -> longToLocalDate(value.time)
        is Int -> longToLocalDate(value.toLong())
        is Long -> longToLocalDate(value)
        is String -> LocalDate.parse(value)
        else -> LocalDate.parse(value.toString())
    }

    override fun valueFromDB(value: Any): T? {
        return fromLocalDate(localDateValueFromDB(value))
    }

    private fun longToLocalDate(instant: Long) = Instant
        .ofEpochMilli(instant)
        .atZone(DEFAULT_TIME_ZONE.toZoneId())
        .toLocalDate()

    override fun notNullValueToDB(value: T & Any): Any {
        val localDate = toLocalDate(value)
        return when {
            currentDialect is SQLiteDialect -> DEFAULT_DATE_STRING_FORMATTER.format(localDate)
            else -> Date(localDate.atStartOfDayIn(DEFAULT_TIME_ZONE).toEpochMilli())
        }
    }

    override fun nonNullValueAsDefaultString(value: T & Any): String {
        return when (currentDialect) {
            is PostgreSQLDialect -> "${nonNullValueToString(value)}::date"
            else -> super.nonNullValueAsDefaultString(value)
        }
    }

    override fun readObject(rs: RowApi, index: Int): Any? {
        val dialect = currentDialect
        return if (dialect is OracleDialect || dialect.h2Mode == H2Dialect.H2CompatibilityMode.Oracle) {
            rs.getObject(index, Timestamp::class.java, this)
        } else {
            super.readObject(rs, index)
        }
    }
}

private fun LocalDate.atStartOfDayIn(timeZone: TimeZone): Instant =
    this.atStartOfDay(timeZone.toZoneId()).toInstant()

/**
 * Base column type for storing date and time values without timezone information.
 *
 * This abstract class handles datetime columns that store both date and time components
 * but do not preserve timezone information. The values are interpreted in the system's
 * default timezone when converting to/from database representations.
 *
 * @param T The application-specific datetime type (e.g., [LocalDateTime], kotlinx.datetime.LocalDateTime)
 * @see IDateColumnType
 * @see KotlinLocalDateTimeColumnType
 */
abstract class LocalDateTimeColumnType<T> : ColumnType<T>(), IDateColumnType {
    abstract fun toLocalDateTime(value: T): LocalDateTime

    abstract fun fromLocalDateTime(value: LocalDateTime): T

    override val hasTimePart: Boolean = true

    override fun sqlType(): String = currentDialect.dataTypeProvider.dateTimeType()

    override fun nonNullValueToString(value: T & Any): String {
        val localDateTime = toLocalDateTime(value)

        val instant = Instant.from(localDateTime.atZone(ZoneId.systemDefault()))

        return when (val dialect = currentDialect) {
            is SQLiteDialect -> "'${SQLITE_AND_ORACLE_DATE_TIME_STRING_FORMATTER.format(instant)}'"
            is OracleDialect -> oracleDateTimeLiteral(instant)
            is MysqlDialect -> {
                val formatter =
                    if (dialect.isFractionDateTimeSupported()) MYSQL_FRACTION_DATE_TIME_STRING_FORMATTER else MYSQL_DATE_TIME_STRING_FORMATTER
                "'${formatter.format(instant)}'"
            }

            else -> "'${DEFAULT_DATE_TIME_STRING_FORMATTER.format(instant)}'"
        }
    }

    @Suppress("MagicNumber")
    private fun localDateTimeValueFromDB(value: Any): LocalDateTime? = when (value) {
        is LocalDateTime -> value
        is java.sql.Date -> longToLocalDateTime(value.time)
        is java.sql.Timestamp -> longToLocalDateTime(value.time / 1000, value.nanos.toLong())
        is Int -> longToLocalDateTime(value.toLong())
        is Long -> longToLocalDateTime(value)
        is String -> LocalDateTime.parse(value, formatterForDateString(value))
        is OffsetDateTime -> value.toLocalDateTime()
        else -> localDateTimeValueFromDB(value.toString())
    }

    override fun valueFromDB(value: Any): T? {
        return localDateTimeValueFromDB(value)?.let { fromLocalDateTime(it) }
    }

    private fun longToLocalDateTime(millis: Long) =
        LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), ZoneId.systemDefault())

    private fun longToLocalDateTime(seconds: Long, nanos: Long) =
        LocalDateTime.ofInstant(Instant.ofEpochSecond(seconds, nanos), ZoneId.systemDefault())

    override fun notNullValueToDB(value: T & Any): Any {
        val localDateTime = toLocalDateTime(value)
        return when {
            currentDialect is SQLiteDialect ->
                SQLITE_AND_ORACLE_DATE_TIME_STRING_FORMATTER.format(localDateTime.atZone(ZoneId.systemDefault()))

            else -> {
                val instant = localDateTime.atZone(ZoneId.systemDefault()).toInstant()
                java.sql.Timestamp(instant.toEpochMilli()).apply { nanos = instant.nano }
            }
        }
    }

    override fun readObject(rs: RowApi, index: Int): Any? {
        return if (currentDialect is OracleDialect) {
            rs.getObject(index, java.sql.Timestamp::class.java)
        } else {
            super.readObject(rs, index)
        }
    }

    override fun nonNullValueAsDefaultString(value: T & Any): String {
        val localDateTime = toLocalDateTime(value)
        val dialect = currentDialect
        return when {
            dialect is PostgreSQLDialect ->
                "'${
                    SQLITE_AND_ORACLE_DATE_TIME_STRING_FORMATTER.format(localDateTime).trimEnd('0').trimEnd('.')
                }'::timestamp without time zone"

            (dialect as? H2Dialect)?.h2Mode == H2Dialect.H2CompatibilityMode.Oracle ->
                "'${SQLITE_AND_ORACLE_DATE_TIME_STRING_FORMATTER.format(localDateTime).trimEnd('0').trimEnd('.')}'"

            else -> super.nonNullValueAsDefaultString(value)
        }
    }
}

private fun dateTimeWithFractionFormat(fraction: Int): DateTimeFormatter {
    val baseFormat = "yyyy-MM-dd HH:mm:ss"
    val newFormat = if (fraction in 1..9) {
        (1..fraction).joinToString(prefix = "$baseFormat.", separator = "") { "S" }
    } else {
        baseFormat
    }
    return DateTimeFormatter.ofPattern(newFormat).withLocale(Locale.ROOT).withZone(ZoneId.systemDefault())
}

private fun formatterForDateString(date: String) = dateTimeWithFractionFormat(
    date.substringAfterLast('.', "").length
)

/**
 * Base column type for storing timestamp values representing instants in time.
 *
 * This abstract class handles timestamp columns that store precise moments in time,
 * typically represented as instants since the Unix epoch. Unlike datetime columns,
 * timestamps are timezone-aware and represent absolute points in time.
 *
 * @param T The application-specific instant type (e.g., [Instant], kotlin.time.Instant, kotlinx.datetime.Instant)
 * @see IDateColumnType
 * @see KotlinInstantColumnType
 */
abstract class DatetimeColumnType<T> : ColumnType<T>(), IDateColumnType {
    abstract fun toInstant(value: T): Instant

    abstract fun fromInstant(instant: Instant): T

    override val hasTimePart: Boolean = true

    override fun sqlType(): String = currentDialect.dataTypeProvider.timestampType()

    override fun nonNullValueToString(value: T & Any): String {
        val instant = toInstant(value)
        return when (val dialect = currentDialect) {
            is MysqlDialect -> {
                val formatter =
                    if (dialect.isFractionDateTimeSupported()) MYSQL_FRACTION_TIMESTAMP_STRING_FORMATTER else MYSQL_TIMESTAMP_STRING_FORMATTER
                "'${formatter.format(instant)}'"
            }

            is SQLiteDialect ->
                "'${SQLITE_AND_ORACLE_TIMESTAMP_STRING_FORMATTER.format(instant)}'"

            is OracleDialect -> oracleTimestampLiteral(instant)

            else -> "'${DEFAULT_TIMESTAMP_STRING_FORMATTER.format(instant)}'"
        }
    }

    private fun instantValueFromDB(value: Any): Instant = when (value) {
        is Timestamp -> value.toInstant()
        is String -> Instant.parse(value)
        is java.time.LocalDateTime -> value.atZone(ZoneId.systemDefault()).toInstant()
        else -> instantValueFromDB(value.toString())
    }

    override fun valueFromDB(value: Any): T {
        return fromInstant(instantValueFromDB(value))
    }

    override fun readObject(rs: RowApi, index: Int): Any? {
        return rs.getObject(index, java.sql.Timestamp::class.java, this)
    }

    override fun notNullValueToDB(value: T & Any): Any {
        val instant = toInstant(value)

        val dialect = currentDialect
        @OptIn(InternalApi::class)
        @Suppress("MagicNumber")
        return when {
            dialect is SQLiteDialect ->
                SQLITE_AND_ORACLE_TIMESTAMP_STRING_FORMATTER.format(instant)

            dialect is MysqlDialect && dialect !is MariaDBDialect &&
                !CoreTransactionManager.currentTransaction().db.version.covers(8, 0) -> {
                val formatter =
                    if (dialect.isFractionDateTimeSupported()) MYSQL_FRACTION_TIMESTAMP_STRING_FORMATTER else MYSQL_TIMESTAMP_STRING_FORMATTER
                formatter.format(instant)
            }

            else -> {
                Timestamp.from(instant)
            }
        }
    }

    override fun nonNullValueAsDefaultString(value: T & Any): String {
        val instant = toInstant(value)

        val dialect = currentDialect
        return when {
            dialect is PostgreSQLDialect ->
                "'${
                    SQLITE_AND_ORACLE_TIMESTAMP_STRING_FORMATTER.format(instant).trimEnd('0').trimEnd('.')
                }'::timestamp without time zone"

            dialect.h2Mode == H2Dialect.H2CompatibilityMode.Oracle ->
                "'${
                    SQLITE_AND_ORACLE_TIMESTAMP_STRING_FORMATTER.format(instant).trimEnd('0').trimEnd('.')
                }'"

            else -> super.nonNullValueAsDefaultString(value)
        }
    }
}

/**
 * Base column type for storing date and time values with timezone offset information.
 *
 * This abstract class handles columns that preserve both the datetime value and its
 * timezone offset, allowing for proper timezone-aware operations. This is crucial
 * for applications that need to maintain timezone context across different regions.
 *
 * @param T The application-specific offset datetime type (e.g., [OffsetDateTime])
 * @see IDateColumnType
 * @see KotlinOffsetDateTimeColumnType
 */
abstract class OffsetDateTimeColumnType<T> : ColumnType<T>(), IDateColumnType {
    abstract fun toOffsetDateTime(value: T): OffsetDateTime

    abstract fun fromOffsetDateTime(datetime: OffsetDateTime): T

    override val hasTimePart: Boolean = true

    override fun sqlType(): String = currentDialect.dataTypeProvider.timestampWithTimeZoneType()

    override fun nonNullValueToString(value: T & Any): String {
        val datetime = toOffsetDateTime(value)
        return when (currentDialect) {
            is SQLiteDialect -> "'${datetime.format(SQLITE_OFFSET_DATE_TIME_FORMATTER)}'"
            is MysqlDialect -> "'${datetime.format(MYSQL_OFFSET_DATE_TIME_FORMATTER)}'"
            is OracleDialect -> oracleDateTimeWithTimezoneLiteral(datetime)
            else -> "'${datetime.format(DEFAULT_OFFSET_DATE_TIME_FORMATTER)}'"
        }
    }

    private fun offsetDatetimeValueFromDB(value: Any): OffsetDateTime = when (value) {
        is OffsetDateTime -> value
        is ZonedDateTime -> value.toOffsetDateTime()
        is String -> {
            if (currentDialect is SQLiteDialect) {
                val temporalAccessor = SQLITE_OFFSET_DATE_TIME_FORMATTER.parse(value)
                if (temporalAccessor.isSupported(ChronoField.OFFSET_SECONDS)) {
                    OffsetDateTime.from(temporalAccessor)
                } else {
                    OffsetDateTime.from(java.time.LocalDateTime.from(temporalAccessor).atOffset(UTC))
                }
            } else {
                OffsetDateTime.parse(value)
            }
        }

        else -> error("Unexpected value: $value of ${value::class.qualifiedName}")
    }

    override fun valueFromDB(value: Any): T? {
        return fromOffsetDateTime(offsetDatetimeValueFromDB(value))
    }

    override fun readObject(rs: RowApi, index: Int): Any? = when (currentDialect) {
        is SQLiteDialect -> super.readObject(rs, index)
        is OracleDialect -> rs.getObject(index, ZonedDateTime::class.java, this)
        else -> rs.getObject(index, OffsetDateTime::class.java, this)
    }

    override fun notNullValueToDB(value: T & Any): Any {
        val offsetDateTime = toOffsetDateTime(value)
        return when (currentDialect) {
            is SQLiteDialect -> offsetDateTime.format(SQLITE_OFFSET_DATE_TIME_FORMATTER)
            is MysqlDialect -> offsetDateTime.format(MYSQL_OFFSET_DATE_TIME_FORMATTER)
            else -> offsetDateTime
        }
    }

    override fun nonNullValueAsDefaultString(value: T & Any): String {
        val offsetDateTime = toOffsetDateTime(value)

        val dialect = currentDialect
        return when {
            dialect is PostgreSQLDialect -> // +00 appended because PostgreSQL stores it in UTC time zone
                "'${offsetDateTime.format(POSTGRESQL_OFFSET_DATE_TIME_AS_DEFAULT_FORMATTER)}+00'::timestamp with time zone"

            dialect is H2Dialect && dialect.h2Mode == H2Dialect.H2CompatibilityMode.Oracle ->
                "'${offsetDateTime.format(POSTGRESQL_OFFSET_DATE_TIME_AS_DEFAULT_FORMATTER)}'"

            dialect is MysqlDialect -> "'${offsetDateTime.format(MYSQL_OFFSET_DATE_TIME_AS_DEFAULT_FORMATTER)}'"

            dialect is OracleDialect -> oracleDateTimeWithTimezoneLiteral(offsetDateTime)
            else -> super.nonNullValueAsDefaultString(value)
        }
    }
}

/**
 * Base column type for storing time-of-day values without date components.
 *
 * This abstract class handles time-only columns that store hours, minutes, seconds,
 * and optionally fractional seconds, but no date information. Times are typically
 * interpreted within a day context.
 *
 * @param T The application-specific time type (e.g., [LocalTime], kotlinx.datetime.LocalTime)
 * @see IDateColumnType
 * @see KotlinLocalTimeColumnType
 */
abstract class LocalTimeColumnType<T> : ColumnType<T>(), IDateColumnType {
    abstract fun toLocalTime(value: T): LocalTime

    abstract fun fromLocalTime(value: LocalTime): T

    override val hasTimePart: Boolean = true

    override fun sqlType(): String = currentDialect.dataTypeProvider.timeType()

    override fun nonNullValueToString(value: T & Any): String {
        val localTime = toLocalTime(value)
        val dialect = currentDialect
        if (dialect is OracleDialect || dialect.h2Mode == H2Dialect.H2CompatibilityMode.Oracle) {
            return "TIMESTAMP '${ORACLE_TIME_STRING_FORMATTER.format(localTime)}'"
        }
        return "'${DEFAULT_TIME_STRING_FORMATTER.format(localTime)}'"
    }

    private fun localTimeValueFromDB(value: Any): LocalTime = when (value) {
        is LocalTime -> value
        is java.sql.Time -> value.toLocalTime()
        is java.sql.Timestamp -> value.toLocalDateTime().toLocalTime()
        is Int -> longToLocalTime(value.toLong())
        is Long -> longToLocalTime(value)
        is String -> {
            val dialect = currentDialect
            val formatter = if (dialect is OracleDialect || dialect.h2Mode == H2Dialect.H2CompatibilityMode.Oracle) {
                formatterForDateString(value)
            } else {
                DEFAULT_TIME_STRING_FORMATTER
            }
            LocalTime.parse(value, formatter)
        }

        else -> localTimeValueFromDB(value.toString())
    }

    private fun longToLocalTime(millis: Long) =
        Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalTime()

    override fun valueFromDB(value: Any): T? {
        return fromLocalTime(localTimeValueFromDB(value))
    }

    override fun notNullValueToDB(value: T & Any): Any {
        val localTimeValue = toLocalTime(value)

        return when {
            currentDialect is SQLiteDialect -> DEFAULT_TIME_STRING_FORMATTER.format(localTimeValue)
            currentDialect.h2Mode == H2Dialect.H2CompatibilityMode.Oracle ->
                Timestamp.valueOf(ORACLE_TIME_STRING_FORMATTER.format(localTimeValue)).toInstant()

            else -> java.sql.Time.valueOf(localTimeValue)
        }
    }

    override fun nonNullValueAsDefaultString(value: T & Any): String {
        val localTime = toLocalTime(value)
        return when (currentDialect) {
            is PostgreSQLDialect -> "${nonNullValueToString(value)}::time without time zone"
            is MysqlDialect -> "'${MYSQL_TIME_AS_DEFAULT_STRING_FORMATTER.format(localTime)}'"
            else -> super.nonNullValueAsDefaultString(value)
        }
    }

    override fun readObject(rs: RowApi, index: Int): Any? {
        val dialect = currentDialect
        return if (dialect is OracleDialect || dialect.h2Mode == H2Dialect.H2CompatibilityMode.Oracle) {
            rs.getObject(index, java.sql.Timestamp::class.java, this)
        } else {
            super.readObject(rs, index)
        }
    }
}

/**
 * Base column type for storing duration/time interval values.
 *
 * This abstract class handles columns that store time durations or intervals,
 * representing spans of time rather than specific moments. Durations are stored
 * as nanosecond values for maximum precision and cross-database compatibility.
 *
 * @param T The application-specific duration type (e.g., [kotlin.time.Duration])
 * @see KotlinDurationColumnType
 */
abstract class DurationColumnType<T> : ColumnType<T>() {
    abstract fun toDuration(value: T): Duration

    abstract fun fromDuration(value: Duration): T

    override fun sqlType(): String = currentDialect.dataTypeProvider.longType()

    override fun nonNullValueToString(value: T & Any): String {
        val duration = toDuration(value).inWholeNanoseconds
        return "'$duration'"
    }

    private fun durationValueFromDB(value: Any): Duration = when (value) {
        Duration.INFINITE.inWholeNanoseconds -> Duration.INFINITE
        is Long -> value.nanoseconds
        is Number -> durationValueFromDB(value.toLong())
        is String -> Duration.parse(value)
        else -> durationValueFromDB(value.toString())
    }

    override fun valueFromDB(value: Any): T? {
        return fromDuration(durationValueFromDB(value))
    }

    override fun notNullValueToDB(value: T & Any): Any {
        return toDuration(value).inWholeNanoseconds
    }
}
