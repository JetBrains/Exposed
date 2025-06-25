package org.jetbrains.exposed.v1.r2dbc.mappers

import io.r2dbc.spi.Row
import io.r2dbc.spi.Statement
import org.jetbrains.exposed.v1.core.IColumnType
import org.jetbrains.exposed.v1.core.IDateColumnType
import org.jetbrains.exposed.v1.core.vendors.DatabaseDialect
import java.sql.Date
import java.sql.Time
import java.sql.Timestamp
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Mapper for date/time related types.
 */
class DateTimeTypeMapper : TypeMapper {
    // We don't specify columnTypes because IDateColumnType is an interface, not a class that extends IColumnType<*>
    // Instead, we'll check for IDateColumnType in the setValue method

    @Suppress("MagicNumber")
    override val priority = 0.2

    override fun setValue(
        statement: Statement,
        dialect: DatabaseDialect,
        typeMapping: R2dbcTypeMapping,
        columnType: IColumnType<*>,
        value: Any?,
        index: Int
    ): Boolean {
        if (columnType !is IDateColumnType) return false
        if (value == null) return false

        val convertedValue = when (value) {
            is Time -> value.toLocalTime()
            is Date -> value.toLocalDate()
            is Timestamp -> value.toLocalDateTime()
            is LocalTime -> Time.valueOf(value)
            else -> value
        }
        statement.bind(index - 1, convertedValue)
        return true
    }

    @Suppress("UNCHECKED_CAST")
    override fun <T> getValue(
        row: Row,
        type: Class<T>?,
        index: Int,
        dialect: DatabaseDialect,
        columnType: IColumnType<*>
    ): ValueContainer<T?> {
        return when (type) {
            Time::class.java -> {
                PresentValueContainer(
                    row.get(index - 1, LocalTime::class.java)?.let { Time.valueOf(it) as T }
                )
            }
            Date::class.java -> {
                PresentValueContainer(
                    row.get(index - 1, LocalDate::class.java)?.let { Date.valueOf(it) as T }
                )
            }
            Timestamp::class.java -> {
                try {
                    PresentValueContainer(
                        row.get(index - 1, LocalDateTime::class.java)?.let { Timestamp.valueOf(it) as T }
                    )
                } catch (_: Exception) {
                    PresentValueContainer(
                        row.get(index - 1, String::class.java)?.let { Timestamp.valueOf(it) as T }
                    )
                }
            }
            else -> NoValueContainer()
        }
    }
}
