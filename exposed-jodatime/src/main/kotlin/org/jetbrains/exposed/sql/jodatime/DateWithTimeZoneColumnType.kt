package org.jetbrains.exposed.sql.jodatime

import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.ColumnType
import org.jetbrains.exposed.sql.IDateColumnType
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.vendors.currentDialect
import org.joda.time.DateTime
import org.joda.time.DateTimeZone
import org.joda.time.format.ISODateTimeFormat

class DateTimeWithTimeZoneColumnType: ColumnType(), IDateColumnType {
    override fun sqlType(): String  = currentDialect.dataTypeProvider.dateTimeTzType()

    override fun nonNullValueToString(value: Any): String {
        val dateTime = when (value) {
            is String -> DateTime.parse(value, DEFAULT_DATE_TIME_FORMATTER)
            is DateTime -> value
            else -> error("Unexpected value: $value of ${value::class.qualifiedName}")
        }
        return "'${DEFAULT_DATE_TIME_FORMATTER.print(dateTime)}'"
    }

    override fun valueFromDB(value: Any): Any = when(value) {
        is DateTime -> value
        is String -> DateTime.parse(value, DEFAULT_DATE_TIME_FORMATTER)
        else -> error("Unexpected value: $value of ${value::class.qualifiedName}")
    }

    override fun notNullValueToDB(value: Any): Any = when {
        value is DateTime -> java.sql.Timestamp(value.millis)
        else -> error("Unexpected value: $value of ${value::class.qualifiedName}")
    }

    override val hasTimePart: Boolean
        get() = true

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is DateColumnType) return false
        if (!super.equals(other)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = super.hashCode()
        result *= 31
        return result
    }

    companion object {
        private val DEFAULT_DATE_TIME_FORMATTER = ISODateTimeFormat.dateTime()
    }
}

/**
 * A datetime column to store a date, a time and a timezone.
 *
 * @param name The column name
 */
fun Table.datetimetz(name: String): Column<DateTimeZone> = registerColumn(name, DateTimeWithTimeZoneColumnType())
