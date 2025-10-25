package org.jetbrains.exposed.v1.core.datetime

import org.jetbrains.exposed.v1.core.ColumnType
import org.jetbrains.exposed.v1.core.IDateColumnType
import org.jetbrains.exposed.v1.core.statements.api.RowApi
import org.jetbrains.exposed.v1.core.vendors.H2Dialect
import org.jetbrains.exposed.v1.core.vendors.MysqlDialect
import org.jetbrains.exposed.v1.core.vendors.OracleDialect
import org.jetbrains.exposed.v1.core.vendors.PostgreSQLDialect
import org.jetbrains.exposed.v1.core.vendors.SQLiteDialect
import org.jetbrains.exposed.v1.core.vendors.currentDialect
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset.UTC
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.time.temporal.ChronoField
import java.util.*

private val SQLITE_OFFSET_DATE_TIME_FORMATTER: DateTimeFormatter by lazy {
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

private val MYSQL_OFFSET_DATE_TIME_FORMATTER: DateTimeFormatter by lazy {
    DateTimeFormatter.ofPattern(
        "yyyy-MM-dd HH:mm:ss.SSSSSS[xxx]",
        Locale.ROOT
    )
}

// Example result: 2023-07-07 14:42:29.343789 +02:00
private val ORACLE_OFFSET_DATE_TIME_FORMATTER: DateTimeFormatter by lazy {
    DateTimeFormatter.ofPattern(
        "yyyy-MM-dd HH:mm:ss.SSSSSS [xxx]",
        Locale.ROOT
    )
}

private fun oracleDateTimeWithTimezoneLiteral(dateTime: OffsetDateTime): String {
    return "TO_TIMESTAMP_TZ('${dateTime.format(ORACLE_OFFSET_DATE_TIME_FORMATTER)}', 'YYYY-MM-DD HH24:MI:SS.FF6 TZH:TZM')"
}

private val DEFAULT_OFFSET_DATE_TIME_FORMATTER: DateTimeFormatter by lazy {
    DateTimeFormatter.ISO_OFFSET_DATE_TIME.withLocale(Locale.ROOT)
}

private val POSTGRESQL_OFFSET_DATE_TIME_AS_DEFAULT_FORMATTER: DateTimeFormatter by lazy {
    DateTimeFormatterBuilder()
        .parseCaseInsensitive()
        .append(DateTimeFormatter.ISO_LOCAL_DATE)
        .appendLiteral(' ')
        .append(DateTimeFormatter.ISO_LOCAL_TIME)
        .toFormatter(Locale.ROOT)
}

private val MYSQL_OFFSET_DATE_TIME_AS_DEFAULT_FORMATTER: DateTimeFormatter by lazy {
    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSSSSS", Locale.ROOT).withZone(ZoneId.of("UTC"))
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
                    java.time.LocalDateTime.from(temporalAccessor).atOffset(UTC)
                }
            } else {
                OffsetDateTime.parse(value)
            }
        }
        else -> error("Unexpected value: $value of ${value::class.qualifiedName}")
    }

    override fun valueFromDB(value: Any): T? = fromOffsetDateTime(offsetDatetimeValueFromDB(value))

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
            dialect is PostgreSQLDialect ->
                "'${offsetDateTime.format(POSTGRESQL_OFFSET_DATE_TIME_AS_DEFAULT_FORMATTER)}+00'::timestamp with time zone"
            dialect is H2Dialect && dialect.h2Mode == H2Dialect.H2CompatibilityMode.Oracle ->
                "'${offsetDateTime.format(POSTGRESQL_OFFSET_DATE_TIME_AS_DEFAULT_FORMATTER)}'"
            dialect is MysqlDialect -> "'${offsetDateTime.format(MYSQL_OFFSET_DATE_TIME_AS_DEFAULT_FORMATTER)}'"
            dialect is OracleDialect -> oracleDateTimeWithTimezoneLiteral(offsetDateTime)
            else -> super.nonNullValueAsDefaultString(value)
        }
    }
}
