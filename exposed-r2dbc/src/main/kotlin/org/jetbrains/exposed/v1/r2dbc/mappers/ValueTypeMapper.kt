package org.jetbrains.exposed.v1.r2dbc.mappers

import io.r2dbc.spi.Statement
import org.jetbrains.exposed.v1.core.IColumnType
import org.jetbrains.exposed.v1.core.vendors.DatabaseDialect
import java.sql.Date
import java.sql.Time
import java.sql.Timestamp

/**
 * Mapper for values without a column type.
 * This mapper is used by the set method in `R2dbcPreparedStatementImpl`.
 */
class ValueTypeMapper : TypeMapper {
    // This mapper handles all column types, but only for specific value types
    // It's a fallback for values that don't have a specific mapper

    @Suppress("MagicNumber")
    override val priority = 0.1

    override fun setValue(
        statement: Statement,
        dialect: DatabaseDialect,
        typeMapping: R2dbcTypeMapping,
        columnType: IColumnType<*>,
        value: Any?,
        index: Int
    ): Boolean {
        if (value == null) {
            return false
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
