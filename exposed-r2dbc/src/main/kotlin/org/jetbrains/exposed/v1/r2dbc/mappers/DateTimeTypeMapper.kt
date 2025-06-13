package org.jetbrains.exposed.v1.r2dbc.mappers

import io.r2dbc.spi.Row
import io.r2dbc.spi.Statement
import org.jetbrains.exposed.v1.core.IColumnType
import org.jetbrains.exposed.v1.core.IDateColumnType
import org.jetbrains.exposed.v1.core.vendors.DatabaseDialect
import org.jetbrains.exposed.v1.core.vendors.H2Dialect
import org.jetbrains.exposed.v1.core.vendors.MariaDBDialect
import org.jetbrains.exposed.v1.core.vendors.MysqlDialect
import org.jetbrains.exposed.v1.core.vendors.OracleDialect
import org.jetbrains.exposed.v1.core.vendors.currentDialect
import java.sql.Date
import java.sql.Time
import java.sql.Timestamp
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

private const val ORACLE_START_YEAR = 1970

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

        // TODO if we have priority for TypeMappers we could split this logic into different type mappers

        // most db throw: Cannot encode [null] parameter of type [java.time.temporal.Temporal]
        val temporalSupported = dialect is OracleDialect || (dialect is MysqlDialect && dialect !is MariaDBDialect)
        if (value == null && temporalSupported) {
            // For null values, we need to determine the appropriate Java class type
            // Since we don't know the exact type, we'll use a generic approach
            statement.bindNull(index - 1, java.time.temporal.Temporal::class.java)
            return true
        } else if (value == null) {
            val classOptions = listOf(
                java.time.LocalDateTime::class.java,
                java.time.LocalTime::class.java,
                java.time.LocalDate::class.java,
            )
            var optionIndex = 0
            var incorrectOption = true
            while (optionIndex < classOptions.size && incorrectOption) {
                try {
                    statement.bindNull(index - 1, classOptions[optionIndex])
                    incorrectOption = false
                } catch (_: RuntimeException) {
                    optionIndex++
                }
            }
            return true
        }

        // For Oracle dialect, we need to handle time values differently
        // because Oracle dialect defines time columns as TIMESTAMP columns
        if (value is java.time.LocalTime) {
            when {
                dialect is OracleDialect -> {
                    // For Oracle dialect, convert LocalTime to java.sql.Timestamp with a fixed date (1970-01-01)
                    // This is because Oracle dialect defines time columns as TIMESTAMP columns
                    val dateTime = java.time.LocalDateTime.of(java.time.LocalDate.of(ORACLE_START_YEAR, 1, 1), value)
                    val timestamp = Timestamp.valueOf(dateTime)
                    statement.bind(index - 1, timestamp)
                    return true
                }
                dialect is H2Dialect && dialect.h2Mode == H2Dialect.H2CompatibilityMode.Oracle -> {
                    // For H2 in Oracle compatibility mode, format LocalTime as a string in the format "1970-01-01 HH:mm:ss"
                    // This is consistent with the JDBC implementation
                    val formatter = java.time.format.DateTimeFormatter.ofPattern("1970-01-01 HH:mm:ss", java.util.Locale.ROOT)
                        .withZone(java.time.ZoneId.of("UTC"))
                    val timeString = formatter.format(value)
                    statement.bind(index - 1, timeString)
                    return true
                }
            }
        }

        val convertedValue = when (value) {
            is Time -> value.toLocalTime()
            is Date -> value.toLocalDate()
            is Timestamp -> value.toLocalDateTime()
            is java.time.LocalTime -> Time.valueOf(value)
            else -> value
        }
        statement.bind(index - 1, convertedValue)
        return true
    }

    @Suppress("UNCHECKED_CAST")
    override fun <T> getValue(
        row: Row,
        type: Class<T>,
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
                // It is tricky, probably the reason for MySql special case is not here but in `KotlinInstantColumnType`
                // The problem is that the line `rs.getObject(index, java.sql.Timestamp::class.java)` in method `valueFromDB()` inside
                // the column type changes the time according to the time zone, and reverts it back in `valueFromDB`
                // But for R2DBC it does not happen. This line changes that behaviour to match it to JDBC behaviour.
                if (currentDialect is MysqlDialect && currentDialect !is MariaDBDialect) {
                    PresentValueContainer(
                        row.get(index - 1, Instant::class.java)?.let { Timestamp.from(it) as T }
                    )
                } else {
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
            }
            else -> NoValueContainer()
        }
    }
}
