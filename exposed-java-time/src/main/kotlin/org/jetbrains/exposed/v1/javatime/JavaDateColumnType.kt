package org.jetbrains.exposed.v1.javatime

import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.core.statements.api.RowApi
import org.jetbrains.exposed.v1.core.transactions.CoreTransactionManager
import org.jetbrains.exposed.v1.core.vendors.*
import java.time.*
import java.time.ZoneOffset.UTC
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.time.temporal.ChronoField
import java.util.*

private const val ORACLE_START_YEAR = 1970

private val DEFAULT_DATE_STRING_FORMATTER by lazy {
    DateTimeFormatter.ISO_LOCAL_DATE.withLocale(Locale.ROOT).withZone(ZoneId.systemDefault())
}

private val DEFAULT_DATE_TIME_STRING_FORMATTER by lazy {
    DateTimeFormatter.ISO_LOCAL_DATE_TIME.withLocale(Locale.ROOT).withZone(ZoneId.systemDefault())
}

private val DEFAULT_TIMESTAMP_STRING_FORMATTER by lazy {
    DateTimeFormatter.ISO_LOCAL_DATE_TIME.withLocale(Locale.ROOT).withZone(UTC)
}

private val SQLITE_AND_ORACLE_DATE_TIME_STRING_FORMATTER_NOTZ by lazy {
    DateTimeFormatter.ofPattern(
        "yyyy-MM-dd HH:mm:ss.SSS",
        Locale.ROOT
    )
}

private val SQLITE_AND_ORACLE_DATE_TIME_STRING_FORMATTER
    get() = SQLITE_AND_ORACLE_DATE_TIME_STRING_FORMATTER_NOTZ.withZone(ZoneId.systemDefault())

private val SQLITE_AND_ORACLE_TIMESTAMP_STRING_FORMATTER_NOTZ by lazy {
    DateTimeFormatter.ofPattern(
        "yyyy-MM-dd HH:mm:ss.SSS"
    )
}

private val SQLITE_AND_ORACLE_TIMESTAMP_STRING_FORMATTER
    get() = SQLITE_AND_ORACLE_TIMESTAMP_STRING_FORMATTER_NOTZ.withZone(ZoneId.systemDefault())

private val MYSQL_FRACTION_DATE_TIME_STRING_FORMATTER_NOTZ by lazy {
    DateTimeFormatter.ofPattern(
        "yyyy-MM-dd HH:mm:ss.SSSSSS"
    )
}

private val MYSQL_FRACTION_DATE_TIME_STRING_FORMATTER
    get() = MYSQL_FRACTION_DATE_TIME_STRING_FORMATTER_NOTZ.withZone(ZoneId.systemDefault())

private val MYSQL_FRACTION_TIMESTAMP_STRING_FORMATTER_NOTZ by lazy {
    DateTimeFormatter.ofPattern(
        "yyyy-MM-dd HH:mm:ss.SSSSSS"
    )
}

private val MYSQL_FRACTION_TIMESTAMP_STRING_FORMATTER
    get() = MYSQL_FRACTION_TIMESTAMP_STRING_FORMATTER_NOTZ.withZone(ZoneId.systemDefault())

private val MYSQL_DATE_TIME_STRING_FORMATTER_NOTZ by lazy {
    DateTimeFormatter.ofPattern(
        "yyyy-MM-dd HH:mm:ss",
        Locale.ROOT
    )
}

private val MYSQL_DATE_TIME_STRING_FORMATTER
    get() = MYSQL_DATE_TIME_STRING_FORMATTER_NOTZ.withZone(ZoneId.systemDefault())

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
    ).withZone(UTC)
}

private val DEFAULT_TIME_STRING_FORMATTER_NOTZ by lazy {
    DateTimeFormatter.ISO_LOCAL_TIME.withLocale(Locale.ROOT).withZone(ZoneId.systemDefault())
}

private val DEFAULT_TIME_STRING_FORMATTER
    get() = DEFAULT_TIME_STRING_FORMATTER_NOTZ.withZone(ZoneId.systemDefault())

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

private val MYSQL_FRACTION_OFFSET_DATE_TIME_AS_DEFAULT_FORMATTER by lazy {
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
    "TO_TIMESTAMP('${SQLITE_AND_ORACLE_DATE_TIME_STRING_FORMATTER.format(instant)}', 'YYYY-MM-DD HH24:MI:SS.FF3')"

private fun oracleTimestampLiteral(instant: Instant) =
    "TO_TIMESTAMP('${SQLITE_AND_ORACLE_TIMESTAMP_STRING_FORMATTER.format(instant)}', 'YYYY-MM-DD HH24:MI:SS.FF3')"

private fun oracleTimestampWithTimezoneLiteral(dateTime: OffsetDateTime) =
    "TO_TIMESTAMP_TZ('${dateTime.format(ORACLE_OFFSET_DATE_TIME_FORMATTER)}', 'YYYY-MM-DD HH24:MI:SS.FF6 TZH:TZM')"

private fun oracleDateLiteral(instant: Instant) =
    "TO_DATE('${DEFAULT_DATE_STRING_FORMATTER.format(instant)}', 'YYYY-MM-DD')"

@Suppress("MagicNumber")
private val LocalDate.millis get() = atStartOfDay(ZoneId.systemDefault()).toEpochSecond() * 1000

/**
 * Column for storing dates, as [LocalDate].
 *
 * @sample date
 */
@Suppress("MagicNumber")
class JavaLocalDateColumnType : ColumnType<LocalDate>(), IDateColumnType {
    override val hasTimePart: Boolean = false

    override fun sqlType(): String = currentDialect.dataTypeProvider.dateType()

    override fun nonNullValueToString(value: LocalDate): String {
        val instant = Instant.from(value.atStartOfDay(ZoneId.systemDefault()))
        val formatted = DEFAULT_DATE_STRING_FORMATTER.format(instant)
        if (currentDialect is OracleDialect) {
            // Date literal in Oracle DB must match NLS_DATE_FORMAT parameter.
            // That parameter can be changed on DB level.
            // But format can be also specified per literal with TO_DATE function
            return oracleDateLiteral(instant)
        }
        return "'$formatted'"
    }

    override fun valueFromDB(value: Any): LocalDate? = when (value) {
        is LocalDate -> value
        is java.sql.Date -> longToLocalDate(value.time)
        is java.sql.Timestamp -> longToLocalDate(value.time)
        is Int -> longToLocalDate(value.toLong())
        is Long -> longToLocalDate(value)
        is String -> LocalDate.parse(value)
        else -> LocalDate.parse(value.toString())
    }

    override fun notNullValueToDB(value: LocalDate): Any = when {
        currentDialect is SQLiteDialect -> DEFAULT_DATE_STRING_FORMATTER.format(value)
        else -> java.sql.Date(value.millis)
    }

    override fun nonNullValueAsDefaultString(value: LocalDate): String = when (currentDialect) {
        is PostgreSQLDialect -> "${nonNullValueToString(value)}::date"
        else -> super.nonNullValueAsDefaultString(value)
    }

    override fun readObject(rs: RowApi, index: Int): Any? {
        val dialect = currentDialect
        return if (dialect is OracleDialect || dialect.h2Mode == H2Dialect.H2CompatibilityMode.Oracle) {
            rs.getObject(index, java.sql.Timestamp::class.java, this)
        } else {
            super.readObject(rs, index)
        }
    }

    private fun longToLocalDate(instant: Long) =
        Instant.ofEpochMilli(instant).atZone(ZoneId.systemDefault()).toLocalDate()

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
class JavaLocalDateTimeColumnType : ColumnType<LocalDateTime>(), IDateColumnType {
    override val hasTimePart: Boolean = true
    override fun sqlType(): String = currentDialect.dataTypeProvider.dateTimeType()

    override fun nonNullValueToString(value: LocalDateTime): String {
        val instant = Instant.from(value.atZone(ZoneId.systemDefault()))

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

    override fun valueFromDB(value: Any): LocalDateTime? = when (value) {
        is LocalDateTime -> value
        is java.sql.Date -> longToLocalDateTime(value.time)
        is java.sql.Timestamp -> longToLocalDateTime(value.time / 1000, value.nanos.toLong())
        is Int -> longToLocalDateTime(value.toLong())
        is Long -> longToLocalDateTime(value)
        is String -> LocalDateTime.parse(value, formatterForDateString(value))
        is OffsetDateTime -> value.toLocalDateTime()
        else -> valueFromDB(value.toString())
    }

    override fun notNullValueToDB(value: LocalDateTime): Any = when {
        currentDialect is SQLiteDialect ->
            SQLITE_AND_ORACLE_DATE_TIME_STRING_FORMATTER.format(value.atZone(ZoneId.systemDefault()))

        else -> {
            val instant = value.atZone(ZoneId.systemDefault()).toInstant()
            java.sql.Timestamp(instant.toEpochMilli()).apply { nanos = instant.nano }
        }
    }

    override fun readObject(rs: RowApi, index: Int): Any? {
        return if (currentDialect is OracleDialect) {
            rs.getObject(index, java.sql.Timestamp::class.java)
        } else {
            super.readObject(rs, index)
        }
    }

    override fun nonNullValueAsDefaultString(value: LocalDateTime): String {
        val dialect = currentDialect
        return when {
            dialect is PostgreSQLDialect ->
                "'${
                    SQLITE_AND_ORACLE_DATE_TIME_STRING_FORMATTER.format(value).trimEnd('0').trimEnd('.')
                }'::timestamp without time zone"

            (dialect as? H2Dialect)?.h2Mode == H2Dialect.H2CompatibilityMode.Oracle ->
                "'${SQLITE_AND_ORACLE_DATE_TIME_STRING_FORMATTER.format(value).trimEnd('0').trimEnd('.')}'"

            else -> super.nonNullValueAsDefaultString(value)
        }
    }

    private fun longToLocalDateTime(millis: Long) =
        LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), ZoneId.systemDefault())

    private fun longToLocalDateTime(seconds: Long, nanos: Long) =
        LocalDateTime.ofInstant(Instant.ofEpochSecond(seconds, nanos), ZoneId.systemDefault())

    companion object {
        internal val INSTANCE = JavaLocalDateTimeColumnType()
    }
}

/**
 * Column for storing times, as [LocalTime].
 *
 * @sample time
 */
class JavaLocalTimeColumnType : ColumnType<LocalTime>(), IDateColumnType {
    override val hasTimePart: Boolean = true

    override fun sqlType(): String {
        val dialect = currentDialect
        // TODO Check if this change actually changes something?
        // TODO If it changes from 'TIME' to 'TIMESTAMP' for H2 Oracle, should it be inside OracleDataTypeProvider
        // TODO like `integerType` for example
        return if (dialect is OracleDialect || dialect.h2Mode == H2Dialect.H2CompatibilityMode.Oracle) {
            // For Oracle dialect, use TIMESTAMP type
            "TIMESTAMP"
        } else {
            dialect.dataTypeProvider.timeType()
        }
    }

    override fun nonNullValueToString(value: LocalTime): String {
        val dialect = currentDialect
        if (dialect is OracleDialect || dialect.h2Mode == H2Dialect.H2CompatibilityMode.Oracle) {
            return "TIMESTAMP '${ORACLE_TIME_STRING_FORMATTER.format(value)}'"
        }
        return "'${DEFAULT_TIME_STRING_FORMATTER.format(value)}'"
    }

    override fun valueFromDB(value: Any): LocalTime = when (value) {
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

        else -> valueFromDB(value.toString())
    }

    // TODO check if we still need it after introducing type mappers?
    override fun notNullValueToDB(value: LocalTime): Any {
        val dialect = currentDialect
        return when {
            dialect is SQLiteDialect -> {
                DEFAULT_TIME_STRING_FORMATTER.format(value)
            }

            dialect is OracleDialect || dialect.h2Mode == H2Dialect.H2CompatibilityMode.Oracle -> {
                // For Oracle dialect, convert LocalTime to java.sql.Timestamp with a fixed date (1970-01-01)
                val dateTime = LocalDateTime.of(LocalDate.of(ORACLE_START_YEAR, 1, 1), value)
                java.sql.Timestamp.valueOf(dateTime)
            }

            else -> {
                java.sql.Time.valueOf(value)
            }
        }
    }

    override fun nonNullValueAsDefaultString(value: LocalTime): String = when (currentDialect) {
        is PostgreSQLDialect -> "${nonNullValueToString(value)}::time without time zone"
        is MysqlDialect -> "'${MYSQL_TIME_AS_DEFAULT_STRING_FORMATTER.format(value)}'"
        else -> super.nonNullValueAsDefaultString(value)
    }

    override fun readObject(rs: RowApi, index: Int): Any? {
        val dialect = currentDialect
        return if (dialect is OracleDialect || dialect.h2Mode == H2Dialect.H2CompatibilityMode.Oracle) {
            rs.getObject(index, java.sql.Timestamp::class.java, this)
        } else {
            super.readObject(rs, index)
        }
    }

    private fun longToLocalTime(millis: Long) =
        Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalTime()

    companion object {
        internal val INSTANCE = JavaLocalTimeColumnType()
    }
}

/**
 * Column for storing dates and times without time zone, as [Instant].
 *
 * @sample timestamp
 */
class JavaInstantColumnType : ColumnType<Instant>(), IDateColumnType {
    override val hasTimePart: Boolean = true

    override fun sqlType(): String = currentDialect.dataTypeProvider.timestampType()

    override fun nonNullValueToString(value: Instant): String {
        return when (val dialect = currentDialect) {
            is OracleDialect -> oracleTimestampLiteral(value)

            is SQLiteDialect ->
                "'${SQLITE_AND_ORACLE_TIMESTAMP_STRING_FORMATTER.format(value)}'"

            is MysqlDialect -> {
                val formatter =
                    if (dialect.isFractionDateTimeSupported()) MYSQL_FRACTION_TIMESTAMP_STRING_FORMATTER else MYSQL_TIMESTAMP_STRING_FORMATTER
                "'${formatter.format(value)}'"
            }

            else -> "'${DEFAULT_TIMESTAMP_STRING_FORMATTER.format(value)}'"
        }
    }

    override fun valueFromDB(value: Any): Instant = when (value) {
        is java.sql.Timestamp -> value.toInstant()
        is String -> Instant.parse(value)
        is LocalDateTime -> value.atZone(ZoneId.systemDefault()).toInstant()
        else -> valueFromDB(value.toString())
    }

    override fun readObject(rs: RowApi, index: Int): Any? {
        return rs.getObject(index, java.sql.Timestamp::class.java, this)
    }

    @Suppress("MagicNumber")
    override fun notNullValueToDB(value: Instant): Any {
        val dialect = currentDialect
        @OptIn(InternalApi::class)
        return when {
            dialect is SQLiteDialect ->
                SQLITE_AND_ORACLE_TIMESTAMP_STRING_FORMATTER.format(value)

            dialect is MysqlDialect && dialect !is MariaDBDialect && !CoreTransactionManager.currentTransaction().db.version.covers(
                8
            ) -> {
                val formatter =
                    if (dialect.isFractionDateTimeSupported()) MYSQL_FRACTION_TIMESTAMP_STRING_FORMATTER else MYSQL_TIMESTAMP_STRING_FORMATTER
                formatter.format(value)
            }

            else -> java.sql.Timestamp.from(value)
        }
    }

    override fun nonNullValueAsDefaultString(value: Instant): String {
        val dialect = currentDialect
        return when {
            dialect is PostgreSQLDialect ->
                "'${
                    SQLITE_AND_ORACLE_TIMESTAMP_STRING_FORMATTER.format(value).trimEnd('0').trimEnd('.')
                }'::timestamp without time zone"

            dialect.h2Mode == H2Dialect.H2CompatibilityMode.Oracle ->
                "'${SQLITE_AND_ORACLE_TIMESTAMP_STRING_FORMATTER.format(value).trimEnd('0').trimEnd('.')}'"

            else -> super.nonNullValueAsDefaultString(value)
        }
    }

    companion object {
        internal val INSTANCE = JavaInstantColumnType()
    }
}

/**
 * Column for storing dates and times with time zone, as [OffsetDateTime].
 *
 * @sample timestampWithTimeZone
 */
class JavaOffsetDateTimeColumnType : ColumnType<OffsetDateTime>(), IDateColumnType {
    override val hasTimePart: Boolean = true

    override fun sqlType(): String = currentDialect.dataTypeProvider.timestampWithTimeZoneType()

    override fun nonNullValueToString(value: OffsetDateTime): String = when (currentDialect) {
        is SQLiteDialect -> "'${value.format(SQLITE_OFFSET_DATE_TIME_FORMATTER)}'"
        is MysqlDialect -> "'${value.format(MYSQL_OFFSET_DATE_TIME_FORMATTER)}'"
        is OracleDialect -> oracleTimestampWithTimezoneLiteral(value)
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
                    OffsetDateTime.from(LocalDateTime.from(temporalAccessor).atOffset(UTC))
                }
            } else {
                OffsetDateTime.parse(value)
            }
        }

        else -> error("Unexpected value: $value of ${value::class.qualifiedName}")
    }

    override fun readObject(rs: RowApi, index: Int): Any? = when (currentDialect) {
        is SQLiteDialect -> super.readObject(rs, index)
        is OracleDialect -> rs.getObject(index, ZonedDateTime::class.java, this)
        else -> rs.getObject(index, OffsetDateTime::class.java, this)
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

            dialect is MysqlDialect -> "'${value.format(MYSQL_FRACTION_OFFSET_DATE_TIME_AS_DEFAULT_FORMATTER)}'"
            else -> super.nonNullValueAsDefaultString(value)
        }
    }

    companion object {
        internal val INSTANCE = JavaOffsetDateTimeColumnType()
    }
}

/**
 * Column for storing time-based amounts of time, as [Duration].
 *
 * @sample duration
 */
class JavaDurationColumnType : ColumnType<Duration>() {
    override fun sqlType(): String = currentDialect.dataTypeProvider.longType()

    override fun nonNullValueToString(value: Duration): String = "'${value.toNanos()}'"

    override fun valueFromDB(value: Any): Duration = when (value) {
        is Long -> Duration.ofNanos(value)
        is Number -> valueFromDB(value.toLong())
        is String -> Duration.parse(value)
        else -> valueFromDB(value.toString())
    }

    override fun notNullValueToDB(value: Duration): Any = value.toNanos()

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
