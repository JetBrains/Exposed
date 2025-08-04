package org.jetbrains.exposed.v1.r2dbc.mappers

import io.r2dbc.spi.Statement
import org.jetbrains.exposed.v1.core.IColumnType
import org.jetbrains.exposed.v1.core.IDateColumnType
import org.jetbrains.exposed.v1.core.vendors.DatabaseDialect
import org.jetbrains.exposed.v1.core.vendors.H2Dialect
import org.jetbrains.exposed.v1.core.vendors.OracleDialect
import java.sql.Timestamp

private const val ORACLE_START_YEAR = 1970

class DateTimeOracleTypeMapper : TypeMapper {
    @Suppress("MagicNumber")
    override val priority = 0.25

    override val dialects = listOf(H2Dialect::class, OracleDialect::class)

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
        if (value is String) {
            when {
                dialect is H2Dialect && dialect.h2Mode == H2Dialect.H2CompatibilityMode.Oracle -> {
                    statement.bind(index - 1, Timestamp.valueOf(value).toLocalDateTime())
                    return true
                }
            }
        }

        return false
    }
}
