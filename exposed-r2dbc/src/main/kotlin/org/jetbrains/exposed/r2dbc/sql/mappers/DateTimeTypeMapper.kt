package org.jetbrains.exposed.r2dbc.sql.mappers

import io.r2dbc.spi.Statement
import org.jetbrains.exposed.sql.IColumnType
import org.jetbrains.exposed.sql.IDateColumnType
import org.jetbrains.exposed.sql.vendors.DatabaseDialect
import java.sql.Date
import java.sql.Time
import java.sql.Timestamp

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

        if (value == null) {
            // For null values, we need to determine the appropriate Java class type
            // Since we don't know the exact type, we'll use a generic approach
            statement.bindNull(index - 1, java.time.temporal.Temporal::class.java)
            return true
        }

        val convertedValue = when (value) {
            is Time -> value.toLocalTime()
            is Date -> value.toLocalDate()
            is Timestamp -> value.toLocalDateTime()
            else -> value
        }
        statement.bind(index - 1, convertedValue)
        return true
    }
}
