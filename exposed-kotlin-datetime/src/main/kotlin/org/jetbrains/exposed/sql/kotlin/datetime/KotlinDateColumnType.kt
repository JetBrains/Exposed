package org.jetbrains.exposed.sql.kotlin.datetime

import kotlinx.datetime.*
import kotlinx.datetime.TimeZone
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.ColumnType
import org.jetbrains.exposed.sql.IDateColumnType
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.vendors.*
import java.sql.ResultSet
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset.UTC
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.time.temporal.ChronoField
import java.util.*
import kotlin.time.Duration
import kotlin.time.Duration.Companion.nanoseconds

private const val MILLIS_IN_SECOND = 1000

private val DEFAULT_TIME_ZONE by lazy {
    TimeZone.currentSystemDefault()
}

private val DEFAULT_DATE_STRING_FORMATTER by lazy {
    DateTimeFormatter.ISO_LOCAL_DATE.withLocale(Locale.ROOT).withZone(ZoneId.systemDefault())
}

private val DEFAULT_DATE_TIME_STRING_FORMATTER by lazy {
    DateTimeFormatter.ISO_LOCAL_DATE_TIME.withLocale(Locale.ROOT).withZone(ZoneId.systemDefault())
}

private val DEFAULT_TIMESTAMP_STRING_FORMATTER by lazy {
    DateTimeFormatter.ISO_LOCAL_DATE_TIME.withLocale(Locale.ROOT).withZone(UTC)
}

private val SQLITE_AND_ORACLE_DATE_TIME_STRING_FORMATTER by lazy {
    DateTimeFormatter.ofPattern(
        "yyyy-MM-dd HH:mm:ss.SSS",
        Locale.ROOT
    ).withZone(ZoneId.systemDefault())
}

private val SQLITE_AND_ORACLE_TIMESTAMP_STRING_FORMATTER by lazy {
    DateTimeFormatter.ofPattern(
        "yyyy-MM-dd HH:mm:ss.SSS",
        Locale.ROOT
    ).withZone(UTC)
}

private val MYSQL_FRACTION_DATE_TIME_STRING_FORMATTER by lazy {
    DateTimeFormatter.ofPattern(
        "yyyy-MM-dd HH:mm:ss.SSSSSS",
        Locale.ROOT
    ).withZone(ZoneId.systemDefault())
}

private val MYSQL_FRACTION_TIMESTAMP_STRING_FORMATTER by lazy {
    DateTimeFormatter.ofPattern(
        "yyyy-MM-dd HH:mm:ss.SSSSSS",
        Locale.ROOT
    ).withZone(UTC)
}

private val MYSQL_DATE_TIME_STRING_FORMATTER by lazy {
    DateTimeFormatter.ofPattern(
        "yyyy-MM-dd HH:mm:ss",
        Locale.ROOT
    ).withZone(ZoneId.systemDefault())
}

private val MYSQL_TIMESTAMP_STRING_FORMATTER by lazy {
    DateTimeFormatter.ofPattern(
        "yyyy-MM-dd HH:mm:ss",
        Locale.ROOT
    ).withZone(UTC)
}

private val ORACLE_TIME_STRING_FORMATTER by lazy {
    DateTimeFormatter.ofPattern(
        "1970-01-01 HH:mm:ss",
        Locale.ROOT
    ).withZone(ZoneId.of("UTC"))
}

private val DEFAULT_TIME_STRING_FORMATTER by lazy {
    DateTimeFormatter.ISO_LOCAL_TIME.withLocale(Locale.ROOT).withZone(ZoneId.systemDefault())
}

// Example result: 2023-07-07 14:42:29.343+02:00 or 2023-07-07 12:42:29.343Z
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

// For UTC time zone, MySQL rejects the 'Z' and will only accept the offset '+00:00'
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

private val DEFAULT_OFFSET_DATE_TIME_FORMATTER by lazy {
    DateTimeFormatter.ISO_OFFSET_DATE_TIME.withLocale(Locale.ROOT)
}

private val MYSQL_TIME_AS_DEFAULT_STRING_FORMATTER by lazy {
    DateTimeFormatter.ofPattern(
        "HH:mm:ss",
        Locale.ROOT
    ).withZone(ZoneId.systemDefault())
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

private fun formatterForDateString(date: String) = dateTimeWithFractionFormat(
    date.substringAfterLast('.', "").length
)

private fun dateTimeWithFractionFormat(fraction: Int): DateTimeFormatter {
    val baseFormat = "yyyy-MM-dd HH:mm:ss"
    val newFormat = if (fraction in 1..9) {
        (1..fraction).joinToString(prefix = "$baseFormat.", separator = "") { "S" }
    } else {
        baseFormat
    }
    return DateTimeFormatter.ofPattern(newFormat).withLocale(Locale.ROOT).withZone(ZoneId.systemDefault())
}

private fun oracleDateTimeLiteral(instant: Instant) =
    "TO_TIMESTAMP('${SQLITE_AND_ORACLE_DATE_TIME_STRING_FORMATTER.format(instant.toJavaInstant())}', 'YYYY-MM-DD HH24:MI:SS.FF3')"

private fun oracleTimestampLiteral(instant: Instant) =
    "TO_TIMESTAMP('${SQLITE_AND_ORACLE_TIMESTAMP_STRING_FORMATTER.format(instant.toJavaInstant())}', 'YYYY-MM-DD HH24:MI:SS.FF3')"

private fun oracleDateTimeWithTimezoneLiteral(dateTime: OffsetDateTime) =
    "TO_TIMESTAMP_TZ('${dateTime.format(ORACLE_OFFSET_DATE_TIME_FORMATTER)}', 'YYYY-MM-DD HH24:MI:SS.FF6 TZH:TZM')"

private fun oracleDateLiteral(instant: Instant) =
    "TO_DATE('${DEFAULT_DATE_STRING_FORMATTER.format(instant.toJavaInstant())}', 'YYYY-MM-DD')"

private val LocalDate.millis get() = this.atStartOfDayIn(TimeZone.currentSystemDefault()).epochSeconds * MILLIS_IN_SECOND

/**
 * Column for storing dates, as [LocalDate].
 *
 * @sample org.jetbrains.exposed.sql.kotlin.datetime.date
 */
class KotlinLocalDateColumnType : ColumnType<LocalDate>(), IDateColumnType {
    override val hasTimePart: Boolean = false

    override fun sqlType(): String = currentDialect.dataTypeProvider.dateType()

    override fun nonNullValueToString(value: LocalDate): String {
        val instant = Instant.fromEpochMilliseconds(value.atStartOfDayIn(DEFAULT_TIME_ZONE).toEpochMilliseconds())
        if (currentDialect is OracleDialect) {
            return oracleDateLiteral(instant)
        }
        return "'${DEFAULT_DATE_STRING_FORMATTER.format(instant.toJavaInstant())}'"
    }

    override fun valueFromDB(value: Any): LocalDate = when (value) {
        is LocalDate -> value
        is java.sql.Date -> longToLocalDate(value.time)
        is java.sql.Timestamp -> longToLocalDate(value.time)
        is Int -> longToLocalDate(value.toLong())
        is Long -> longToLocalDate(value)
        is String -> LocalDate.parse(value)
        else -> LocalDate.parse(value.toString())
    }

    override fun notNullValueToDB(value: LocalDate) = when {
        currentDialect is SQLiteDialect -> DEFAULT_DATE_STRING_FORMATTER.format(value.toJavaLocalDate())
        else -> java.sql.Date(value.millis)
    }

    override fun nonNullValueAsDefaultString(value: LocalDate): String = when (currentDialect) {
        is PostgreSQLDialect -> "${nonNullValueToString(value)}::date"
        else -> super.nonNullValueAsDefaultString(value)
    }

    private fun longToLocalDate(instant: Long) = Instant.fromEpochMilliseconds(instant).toLocalDateTime(DEFAULT_TIME_ZONE).date

    companion object {
        internal val INSTANCE = KotlinLocalDateColumnType()
    }
}

/**
 * Column for storing dates and times without time zone, as [LocalDateTime].
 *
 * @sample org.jetbrains.exposed.sql.kotlin.datetime.datetime
 */
class KotlinLocalDateTimeColumnType : ColumnType<LocalDateTime>(), IDateColumnType {
    override val hasTimePart: Boolean = true

    override fun sqlType(): String = currentDialect.dataTypeProvider.dateTimeType()

    override fun nonNullValueToString(value: LocalDateTime): String {
        val instant = value.toInstant(DEFAULT_TIME_ZONE)

        return when (val dialect = currentDialect) {
            is SQLiteDialect -> "'${SQLITE_AND_ORACLE_DATE_TIME_STRING_FORMATTER.format(instant.toJavaInstant())}'"
            is OracleDialect -> oracleDateTimeLiteral(instant)

            is MysqlDialect -> {
                val formatter = if (dialect.isFractionDateTimeSupported()) MYSQL_FRACTION_DATE_TIME_STRING_FORMATTER else MYSQL_DATE_TIME_STRING_FORMATTER
                "'${formatter.format(instant.toJavaInstant())}'"
            }
            else -> "'${DEFAULT_DATE_TIME_STRING_FORMATTER.format(instant.toJavaInstant())}'"
        }
    }

    override fun valueFromDB(value: Any): LocalDateTime = when (value) {
        is LocalDateTime -> value
        is java.sql.Date -> longToLocalDateTime(value.time)
        is java.sql.Timestamp -> longToLocalDateTime(value.time / MILLIS_IN_SECOND, value.nanos.toLong())
        is Int -> longToLocalDateTime(value.toLong())
        is Long -> longToLocalDateTime(value)
        is java.time.LocalDateTime -> value.toKotlinLocalDateTime()
        is String -> java.time.LocalDateTime.parse(value, formatterForDateString(value)).toKotlinLocalDateTime()
        is OffsetDateTime -> value.toLocalDateTime().toKotlinLocalDateTime()
        else -> valueFromDB(value.toString())
    }

    override fun notNullValueToDB(value: LocalDateTime): Any = when {
        currentDialect is SQLiteDialect ->
            SQLITE_AND_ORACLE_DATE_TIME_STRING_FORMATTER.format(value.toJavaLocalDateTime().atZone(ZoneId.systemDefault()))
        else -> {
            val instant = value.toJavaLocalDateTime().atZone(ZoneId.systemDefault()).toInstant()
            java.sql.Timestamp(instant.toEpochMilli()).apply { nanos = instant.nano }
        }
    }

    override fun readObject(rs: ResultSet, index: Int): Any? {
        return if (currentDialect is OracleDialect) {
            rs.getObject(index, java.sql.Timestamp::class.java)
        } else {
            super.readObject(rs, index)
        }
    }

    override fun nonNullValueAsDefaultString(value: LocalDateTime): String {
        val instant = value.toInstant(DEFAULT_TIME_ZONE).toJavaInstant()
        val dialect = currentDialect
        return when {
            dialect is PostgreSQLDialect ->
                "'${SQLITE_AND_ORACLE_DATE_TIME_STRING_FORMATTER.format(instant).trimEnd('0').trimEnd('.')}'::timestamp without time zone"
            dialect.h2Mode == H2Dialect.H2CompatibilityMode.Oracle ->
                "'${SQLITE_AND_ORACLE_DATE_TIME_STRING_FORMATTER.format(instant).trimEnd('0').trimEnd('.')}'"
            else -> super.nonNullValueAsDefaultString(value)
        }
    }

    private fun longToLocalDateTime(millis: Long) = Instant.fromEpochMilliseconds(millis).toLocalDateTime(DEFAULT_TIME_ZONE)
    private fun longToLocalDateTime(seconds: Long, nanos: Long) = Instant.fromEpochSeconds(seconds, nanos).toLocalDateTime(DEFAULT_TIME_ZONE)

    companion object {
        internal val INSTANCE = KotlinLocalDateTimeColumnType()
    }
}

/**
 * Column for storing times, as [LocalTime].
 *
 * @sample org.jetbrains.exposed.sql.kotlin.datetime.time
 */
class KotlinLocalTimeColumnType : ColumnType<LocalTime>(), IDateColumnType {
    override val hasTimePart: Boolean = true

    override fun sqlType(): String = currentDialect.dataTypeProvider.timeType()

    override fun nonNullValueToString(value: LocalTime): String {
        val dialect = currentDialect
        val instant = value.toJavaLocalTime()

        if (dialect is OracleDialect || dialect.h2Mode == H2Dialect.H2CompatibilityMode.Oracle) {
            return "TIMESTAMP '${ORACLE_TIME_STRING_FORMATTER.format(instant)}'"
        }

        return "'${DEFAULT_TIME_STRING_FORMATTER.format(instant)}'"
    }

    override fun valueFromDB(value: Any): LocalTime = when (value) {
        is LocalTime -> value
        is java.sql.Time -> value.toLocalTime().toKotlinLocalTime()
        is java.sql.Timestamp -> value.toLocalDateTime().toLocalTime().toKotlinLocalTime()
        is Int -> longToLocalTime(value.toLong())
        is Long -> longToLocalTime(value)
        is String -> {
            val dialect = currentDialect
            val formatter = if (dialect is OracleDialect || dialect.h2Mode == H2Dialect.H2CompatibilityMode.Oracle) {
                formatterForDateString(value)
            } else {
                DEFAULT_TIME_STRING_FORMATTER
            }
            java.time.LocalTime.parse(value, formatter).toKotlinLocalTime()
        }

        else -> valueFromDB(value.toString())
    }

    override fun notNullValueToDB(value: LocalTime): Any = when {
        currentDialect is SQLiteDialect -> DEFAULT_TIME_STRING_FORMATTER.format(value.toJavaLocalTime())
        currentDialect.h2Mode == H2Dialect.H2CompatibilityMode.Oracle ->
            ORACLE_TIME_STRING_FORMATTER.format(value.toJavaLocalTime())
        else -> java.sql.Time.valueOf(value.toJavaLocalTime())
    }

    override fun nonNullValueAsDefaultString(value: LocalTime): String = when (currentDialect) {
        is PostgreSQLDialect -> "${nonNullValueToString(value)}::time without time zone"
        is MysqlDialect -> "'${MYSQL_TIME_AS_DEFAULT_STRING_FORMATTER.format(value.toJavaLocalTime())}'"
        else -> super.nonNullValueAsDefaultString(value)
    }

    private fun longToLocalTime(millis: Long) = Instant.fromEpochMilliseconds(millis).toLocalDateTime(DEFAULT_TIME_ZONE).time

    companion object {
        internal val INSTANCE = KotlinLocalTimeColumnType()
    }
}

/**
 * Column for storing dates and times without time zone, as [Instant].
 *
 * @sample org.jetbrains.exposed.sql.kotlin.datetime.timestamp
 */
class KotlinInstantColumnType : ColumnType<Instant>(), IDateColumnType {
    override val hasTimePart: Boolean = true

    override fun sqlType(): String = currentDialect.dataTypeProvider.timestampType()

    override fun nonNullValueToString(value: Instant): String {
        val instant = value.toJavaInstant()

        return when (val dialect = currentDialect) {
            is MysqlDialect -> {
                val formatter = if (dialect.isFractionDateTimeSupported()) MYSQL_FRACTION_TIMESTAMP_STRING_FORMATTER else MYSQL_TIMESTAMP_STRING_FORMATTER
                "'${formatter.format(instant)}'"
            }

            is SQLiteDialect ->
                "'${SQLITE_AND_ORACLE_TIMESTAMP_STRING_FORMATTER.format(instant)}'"

            is OracleDialect -> oracleTimestampLiteral(value)

            else -> "'${DEFAULT_TIMESTAMP_STRING_FORMATTER.format(instant)}'"
        }
    }

    override fun valueFromDB(value: Any): Instant = when (value) {
        is java.sql.Timestamp -> value.toInstant().toKotlinInstant()
        is String -> Instant.parse(value)
        else -> valueFromDB(value.toString())
    }

    override fun readObject(rs: ResultSet, index: Int): Any? {
        return rs.getTimestamp(index)
    }

    override fun notNullValueToDB(value: Instant): Any {
        val dialect = currentDialect
        return when {
            dialect is SQLiteDialect ->
                SQLITE_AND_ORACLE_TIMESTAMP_STRING_FORMATTER.format(value.toJavaInstant())
            dialect is MysqlDialect && dialect !is MariaDBDialect -> {
                val formatter = if (dialect.isFractionDateTimeSupported()) MYSQL_FRACTION_TIMESTAMP_STRING_FORMATTER else MYSQL_TIMESTAMP_STRING_FORMATTER
                formatter.format(value.toJavaInstant())
            }
            else -> java.sql.Timestamp.from(value.toJavaInstant())
        }
    }

    override fun nonNullValueAsDefaultString(value: Instant): String {
        val dialect = currentDialect
        return when {
            dialect is PostgreSQLDialect ->
                "'${SQLITE_AND_ORACLE_TIMESTAMP_STRING_FORMATTER.format(value.toJavaInstant()).trimEnd('0').trimEnd('.')}'::timestamp without time zone"

            dialect.h2Mode == H2Dialect.H2CompatibilityMode.Oracle ->
                "'${SQLITE_AND_ORACLE_TIMESTAMP_STRING_FORMATTER.format(value.toJavaInstant()).trimEnd('0').trimEnd('.')}'"

            else -> super.nonNullValueAsDefaultString(value)
        }
    }

    companion object {
        internal val INSTANCE = KotlinInstantColumnType()
    }
}

/**
 * Column for storing dates and times with time zone, as [OffsetDateTime].
 *
 * @sample org.jetbrains.exposed.sql.kotlin.datetime.timestampWithTimeZone
 */
class KotlinOffsetDateTimeColumnType : ColumnType<OffsetDateTime>(), IDateColumnType {
    override val hasTimePart: Boolean = true

    override fun sqlType(): String = currentDialect.dataTypeProvider.timestampWithTimeZoneType()

    override fun nonNullValueToString(value: OffsetDateTime): String = when (currentDialect) {
        is SQLiteDialect -> "'${value.format(SQLITE_OFFSET_DATE_TIME_FORMATTER)}'"
        is MysqlDialect -> "'${value.format(MYSQL_OFFSET_DATE_TIME_FORMATTER)}'"
        is OracleDialect -> oracleDateTimeWithTimezoneLiteral(value)
        else -> "'${value.format(DEFAULT_OFFSET_DATE_TIME_FORMATTER)}'"
    }

    override fun valueFromDB(value: Any): OffsetDateTime = when (value) {
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

    override fun readObject(rs: ResultSet, index: Int): Any? = when (currentDialect) {
        is SQLiteDialect -> super.readObject(rs, index)
        is OracleDialect -> rs.getObject(index, ZonedDateTime::class.java)
        else -> rs.getObject(index, OffsetDateTime::class.java)
    }

    override fun notNullValueToDB(value: OffsetDateTime): Any = when (currentDialect) {
        is SQLiteDialect -> value.format(SQLITE_OFFSET_DATE_TIME_FORMATTER)
        is MysqlDialect -> value.format(MYSQL_OFFSET_DATE_TIME_FORMATTER)
        else -> value
    }

    override fun nonNullValueAsDefaultString(value: OffsetDateTime): String {
        val dialect = currentDialect
        return when {
            dialect is PostgreSQLDialect -> // +00 appended because PostgreSQL stores it in UTC time zone
                "'${value.format(POSTGRESQL_OFFSET_DATE_TIME_AS_DEFAULT_FORMATTER)}+00'::timestamp with time zone"

            dialect is H2Dialect && dialect.h2Mode == H2Dialect.H2CompatibilityMode.Oracle ->
                "'${value.format(POSTGRESQL_OFFSET_DATE_TIME_AS_DEFAULT_FORMATTER)}'"

            dialect is MysqlDialect -> "'${value.format(MYSQL_OFFSET_DATE_TIME_AS_DEFAULT_FORMATTER)}'"

            dialect is OracleDialect -> oracleDateTimeWithTimezoneLiteral(value)
            else -> super.nonNullValueAsDefaultString(value)
        }
    }

    companion object {
        internal val INSTANCE = KotlinOffsetDateTimeColumnType()
    }
}

/**
 * Column for storing time-based amounts of time, as [Duration].
 *
 * @sample org.jetbrains.exposed.sql.kotlin.datetime.duration
 */
class KotlinDurationColumnType : ColumnType<Duration>() {
    override fun sqlType(): String = currentDialect.dataTypeProvider.longType()

    override fun nonNullValueToString(value: Duration): String {
        val duration = value.inWholeNanoseconds
        return "'$duration'"
    }

    override fun valueFromDB(value: Any): Duration = when (value) {
        Duration.INFINITE.inWholeNanoseconds -> Duration.INFINITE
        is Long -> value.nanoseconds
        is Number -> value.toLong().nanoseconds
        is String -> Duration.parse(value)
        else -> valueFromDB(value.toString())
    }

    override fun readObject(rs: ResultSet, index: Int): Any? {
        // ResultSet.getLong returns 0 instead of null
        return rs.getLong(index).takeIf { rs.getObject(index) != null }
    }

    override fun notNullValueToDB(value: Duration): Any = value.inWholeNanoseconds

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
