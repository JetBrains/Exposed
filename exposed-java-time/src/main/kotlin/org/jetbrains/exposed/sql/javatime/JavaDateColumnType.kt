package org.jetbrains.exposed.sql.javatime

import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.ColumnType
import org.jetbrains.exposed.sql.IDateColumnType
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.vendors.OracleDialect
import org.jetbrains.exposed.sql.vendors.SQLiteDialect
import org.jetbrains.exposed.sql.vendors.currentDialect
import java.sql.ResultSet
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.*

private fun defaultDateStringFormatter(zoneId: ZoneId) = DateTimeFormatter.ISO_LOCAL_DATE.withLocale(Locale.ROOT).withZone(zoneId)
private fun defaultDateTimeStringFormatter(zoneId: ZoneId) = DateTimeFormatter.ISO_LOCAL_DATE_TIME.withLocale(Locale.ROOT).withZone(zoneId)
private fun sqliteAndOracleDateTimeStringFormatter(zoneId: ZoneId) = DateTimeFormatter.ISO_LOCAL_DATE_TIME.withLocale(Locale.ROOT).withZone(zoneId)
private fun oracleTimeStringFormatter(zoneId: ZoneId) = DateTimeFormatter.ofPattern(
    "1900-01-01 HH:mm:ss",
    Locale.ROOT
).withZone(zoneId)
private fun defaultTimeStringFormatter(zoneId: ZoneId) = DateTimeFormatter.ISO_LOCAL_TIME.withLocale(Locale.ROOT).withZone(zoneId)

internal fun formatterForDateString(zoneId: ZoneId, date: String) = dateTimeWithFractionFormat(zoneId, date.substringAfterLast('.', "").length)
internal fun dateTimeWithFractionFormat(zoneId: ZoneId, fraction: Int): DateTimeFormatter {
    val baseFormat = "yyyy-MM-d HH:mm:ss"
    val newFormat = if (fraction in 1..9) {
        (1..fraction).joinToString(prefix = "$baseFormat.", separator = "") { "S" }
    } else {
        baseFormat
    }
    return DateTimeFormatter.ofPattern(newFormat).withLocale(Locale.ROOT).withZone(zoneId)
}

internal fun LocalDate.millis(zoneId: ZoneId) = atStartOfDay(zoneId).toEpochSecond() * 1000

class JavaLocalDateColumnType(private val zoneId: ZoneId) : ColumnType(), IDateColumnType {
    override val hasTimePart: Boolean = false
    private val defaultDateStringFormatter = defaultDateStringFormatter(zoneId)

    override fun sqlType(): String = "DATE"

    override fun nonNullValueToString(value: Any): String {
        val instant = when (value) {
            is String -> return value
            is LocalDate -> Instant.from(value.atStartOfDay(zoneId))
            is java.sql.Date -> Instant.ofEpochMilli(value.time)
            is java.sql.Timestamp -> Instant.ofEpochSecond(value.time / 1000, value.nanos.toLong())
            else -> error("Unexpected value: $value of ${value::class.qualifiedName}")
        }

        return "'${defaultDateStringFormatter.format(instant)}'"
    }

    override fun valueFromDB(value: Any): Any = when (value) {
        is LocalDate -> value
        is java.sql.Date -> longToLocalDate(value.time)
        is java.sql.Timestamp -> longToLocalDate(value.time)
        is Int -> longToLocalDate(value.toLong())
        is Long -> longToLocalDate(value)
        is String -> when (currentDialect) {
            is SQLiteDialect -> LocalDate.parse(value)
            else -> value
        }
        else -> LocalDate.parse(value.toString())
    }

    override fun notNullValueToDB(value: Any) = when {
        value is LocalDate -> java.sql.Date(value.millis(zoneId))
        else -> value
    }

    private fun longToLocalDate(instant: Long) = Instant.ofEpochMilli(instant).atZone(zoneId).toLocalDate()
}

class JavaLocalDateTimeColumnType(private val zoneId: ZoneId) : ColumnType(), IDateColumnType {
    override val hasTimePart: Boolean = true
    override fun sqlType(): String = currentDialect.dataTypeProvider.dateTimeType()
    private val sqliteAndOracleDateTimeStringFormatter = sqliteAndOracleDateTimeStringFormatter(zoneId)
    private val defaultDateTimeStringFormatter = defaultDateTimeStringFormatter(zoneId)

    override fun nonNullValueToString(value: Any): String {
        val instant = when (value) {
            is String -> return value
            is LocalDateTime -> Instant.from(value.atZone(zoneId))
            is java.sql.Date -> Instant.ofEpochMilli(value.time)
            is java.sql.Timestamp -> Instant.ofEpochSecond(value.time / 1000, value.nanos.toLong())
            else -> error("Unexpected value: $value of ${value::class.qualifiedName}")
        }

        return when (currentDialect) {
            is SQLiteDialect, is OracleDialect -> "'${sqliteAndOracleDateTimeStringFormatter.format(instant)}'"
            else -> "'${defaultDateTimeStringFormatter.format(instant)}'"
        }
    }

    override fun valueFromDB(value: Any): Any = when (value) {
        is LocalDateTime -> value
        is java.sql.Date -> longToLocalDateTime(value.time)
        is java.sql.Timestamp -> longToLocalDateTime(value.time / 1000, value.nanos.toLong())
        is Int -> longToLocalDateTime(value.toLong())
        is Long -> longToLocalDateTime(value)
        is String -> {
            if (currentDialect is OracleDialect) {
                formatterForDateString(zoneId, value)
            } else {
                defaultDateTimeStringFormatter.parse(value, LocalDateTime::from)
            }
        }
        else -> valueFromDB(value.toString())
    }

    override fun notNullValueToDB(value: Any): Any = when {
        value is LocalDateTime && currentDialect is SQLiteDialect ->
            sqliteAndOracleDateTimeStringFormatter.format(value.atZone(zoneId))
        value is LocalDateTime -> {
            val instant = value.atZone(zoneId).toInstant()
            java.sql.Timestamp(instant.toEpochMilli()).apply { nanos = instant.nano }
        }
        else -> value
    }

    private fun longToLocalDateTime(millis: Long) = LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), zoneId)
    private fun longToLocalDateTime(seconds: Long, nanos: Long) =
        LocalDateTime.ofInstant(Instant.ofEpochSecond(seconds, nanos), zoneId)
}

class JavaLocalTimeColumnType(private val zoneId: ZoneId) : ColumnType(), IDateColumnType {
    override val hasTimePart: Boolean = true
    private val oracleTimeStringFormatter = oracleTimeStringFormatter(zoneId)
    private val defaultTimeStringFormatter = defaultTimeStringFormatter(zoneId)

    override fun sqlType(): String = currentDialect.dataTypeProvider.timeType()

    override fun nonNullValueToString(value: Any): String {
        val instant = when (value) {
            is String -> return value
            is LocalTime -> value
            is java.sql.Time -> Instant.ofEpochMilli(value.time).atZone(zoneId)
            is java.sql.Timestamp -> Instant.ofEpochMilli(value.time).atZone(zoneId)
            else -> error("Unexpected value: $value of ${value::class.qualifiedName}")
        }

        val formatter = if (currentDialect is OracleDialect) {
            oracleTimeStringFormatter
        } else {
            defaultTimeStringFormatter
        }
        return "'${formatter.format(instant)}'"
    }

    override fun valueFromDB(value: Any): LocalTime = when (value) {
        is LocalTime -> value
        is java.sql.Time -> value.toLocalTime()
        is java.sql.Timestamp -> value.toLocalDateTime().toLocalTime()
        is Int -> longToLocalTime(value.toLong())
        is Long -> longToLocalTime(value)
        is String -> {
            val formatter = if (currentDialect is OracleDialect) {
                formatterForDateString(zoneId, value)
            } else {
                defaultTimeStringFormatter
            }
            LocalTime.parse(value, formatter)
        }
        else -> valueFromDB(value.toString())
    }

    override fun notNullValueToDB(value: Any): Any = when (value) {
        is LocalTime -> java.sql.Time.valueOf(value)
        else -> value
    }

    private fun longToLocalTime(millis: Long) = Instant.ofEpochMilli(millis).atZone(zoneId).toLocalTime()
}

class JavaInstantColumnType(private val zoneId: ZoneId) : ColumnType(), IDateColumnType {
    override val hasTimePart: Boolean = true
    override fun sqlType(): String = currentDialect.dataTypeProvider.dateTimeType()
    private val sqliteAndOracleDateTimeStringFormatter = sqliteAndOracleDateTimeStringFormatter(zoneId)
    private val defaultDateTimeStringFormatter = defaultDateTimeStringFormatter(zoneId)

    override fun nonNullValueToString(value: Any): String {
        val instant = when (value) {
            is String -> return value
            is Instant -> value
            is java.sql.Timestamp -> value.toInstant()
            else -> error("Unexpected value: $value of ${value::class.qualifiedName}")
        }

        return when (currentDialect) {
            is OracleDialect -> "'${sqliteAndOracleDateTimeStringFormatter.format(instant)}'"
            else -> "'${defaultDateTimeStringFormatter.format(instant)}'"
        }
    }

    override fun valueFromDB(value: Any): Instant = when (value) {
        is java.sql.Timestamp -> value.toInstant()
        is Instant -> value
        is String -> defaultDateTimeStringFormatter.parse(value, Instant::from)
        else -> valueFromDB(value.toString())
    }

    override fun readObject(rs: ResultSet, index: Int): Any? {
        return rs.getObject(index, LocalDateTime::class.java)
    }

    override fun notNullValueToDB(value: Any): Any = when {
        value is Instant && currentDialect is SQLiteDialect ->
            sqliteAndOracleDateTimeStringFormatter.format(value)
        value is Instant -> LocalDateTime.ofInstant(value, zoneId)
        else -> value
    }
}

class JavaDurationColumnType : ColumnType() {
    override fun sqlType(): String = currentDialect.dataTypeProvider.longType()

    override fun nonNullValueToString(value: Any): String {
        val duration = when (value) {
            is String -> return value
            is Duration -> value
            is Long -> Duration.ofNanos(value)
            is Number -> Duration.ofNanos(value.toLong())
            else -> error("Unexpected value: $value of ${value::class.qualifiedName}")
        }

        return "'${duration.toNanos()}'"
    }

    override fun valueFromDB(value: Any): Duration = when (value) {
        is Long -> Duration.ofNanos(value)
        is Number -> Duration.ofNanos(value.toLong())
        is String -> Duration.parse(value)
        else -> valueFromDB(value.toString())
    }

    override fun readObject(rs: ResultSet, index: Int): Any? {
        // ResultSet.getLong returns 0 instead of null
        return rs.getLong(index).takeIf { rs.getObject(index) != null }
    }

    override fun notNullValueToDB(value: Any): Any {
        if (value is Duration) {
            return value.toNanos()
        }
        return value
    }

    companion object {
        internal val INSTANCE = JavaDurationColumnType()
    }
}

/**
 * A date column to store a date.
 *
 * @param name   The column name
 */
@Deprecated("This uses the ZoneId.systemDefault() as the ZoneId, which can cause inconsistencies if you change your machine is in a different timezone. Please explictly define the ZoneId!",
            ReplaceWith("date(name, ZoneId.systemDefault())", "java.time.ZoneId")
)
fun Table.date(name: String): Column<LocalDate> = date(name, ZoneId.systemDefault())

/**
 * A datetime column to store both a date and a time.
 *
 * @param name The column name
 */
@Deprecated("This uses the ZoneId.systemDefault() as the ZoneId, which can cause inconsistencies if you change your machine is in a different timezone. Please explictly define the ZoneId!",
            ReplaceWith("datetime(name, ZoneId.systemDefault())", "java.time.ZoneId")
)
fun Table.datetime(name: String): Column<LocalDateTime> = datetime(name, ZoneId.systemDefault())

/**
 * A time column to store a time.
 *
 * Doesn't return nanos from database.
 *
 * @param name The column name
 * @author Maxim Vorotynsky
 */
@Deprecated("This uses the ZoneId.systemDefault() as the ZoneId, which can cause inconsistencies if you change your machine is in a different timezone. Please explictly define the ZoneId!",
            ReplaceWith("time(name, ZoneId.systemDefault())", "java.time.ZoneId")
)
fun Table.time(name: String): Column<LocalTime> = time(name, ZoneId.systemDefault())

/**
 * A timestamp column to store both a date and a time.
 *
 * @param name The column name
 */
@Deprecated("This uses the ZoneId.systemDefault() as the ZoneId, which can cause inconsistencies if you change your machine is in a different timezone. Please explictly define the ZoneId!",
            ReplaceWith("timestamp(name, ZoneId.systemDefault())", "java.time.ZoneId")
)
fun Table.timestamp(name: String): Column<Instant> = timestamp(name, ZoneId.systemDefault())


/**
 * A date column to store a date.
 *
 * @param name   The column name
 * @param zoneId The zone ID
 */
fun Table.date(name: String, zoneId: ZoneId): Column<LocalDate> = registerColumn(name, JavaLocalDateColumnType(zoneId))

/**
 * A datetime column to store both a date and a time.
 *
 * @param name The column name
 * @param zoneId The zone ID
 */
fun Table.datetime(name: String, zoneId: ZoneId): Column<LocalDateTime> = registerColumn(name, JavaLocalDateTimeColumnType(zoneId))

/**
 * A time column to store a time.
 *
 * Doesn't return nanos from database.
 *
 * @param name The column name
 * @param zoneId The zone ID
 * @author Maxim Vorotynsky
 */
fun Table.time(name: String, zoneId: ZoneId): Column<LocalTime> = registerColumn(name, JavaLocalTimeColumnType(zoneId))

/**
 * A timestamp column to store both a date and a time.
 *
 * @param name The column name
 * @param zoneId The zone ID
 */
fun Table.timestamp(name: String, zoneId: ZoneId): Column<Instant> = registerColumn(name, JavaInstantColumnType(zoneId))

/**
 * A date column to store a duration.
 *
 * @param name The column name
 */
fun Table.duration(name: String): Column<Duration> = registerColumn(name, JavaDurationColumnType.INSTANCE)
