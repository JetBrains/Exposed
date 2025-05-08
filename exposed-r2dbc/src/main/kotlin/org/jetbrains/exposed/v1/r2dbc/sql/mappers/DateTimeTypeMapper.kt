package org.jetbrains.exposed.v1.r2dbc.sql.mappers

import io.r2dbc.spi.Statement
import org.jetbrains.exposed.v1.sql.IColumnType
import org.jetbrains.exposed.v1.sql.IDateColumnType
import org.jetbrains.exposed.v1.sql.vendors.DatabaseDialect
import org.jetbrains.exposed.v1.sql.vendors.H2Dialect
import org.jetbrains.exposed.v1.sql.vendors.MariaDBDialect
import org.jetbrains.exposed.v1.sql.vendors.MysqlDialect
import org.jetbrains.exposed.v1.sql.vendors.OracleDialect
import java.sql.Date
import java.sql.Time
import java.sql.Timestamp

private const val ORACLE_START_YEAR = 1970

/**
 * Mapper for date/time related types.
 */
class DateTimeTypeMapper : TypeMapper {
    // We don't specify columnTypes because IDateColumnType is an interface, not a class that extends IColumnType<*>
    // Instead, we'll check for IDateColumnType in the setValue method

    override fun setValue(
        statement: Statement,
        dialect: DatabaseDialect,
        mapperRegistry: TypeMapperRegistry,
        columnType: IColumnType<*>,
        value: Any?,
        index: Int
    ): Boolean {
        if (columnType !is IDateColumnType) return false

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
}
