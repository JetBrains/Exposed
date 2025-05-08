package org.jetbrains.exposed.v1.r2dbc.sql.mappers

import io.r2dbc.postgresql.codec.Json
import io.r2dbc.spi.Statement
import org.jetbrains.exposed.v1.sql.IColumnType
import org.jetbrains.exposed.v1.sql.vendors.DatabaseDialect
import org.jetbrains.exposed.v1.sql.vendors.PostgreSQLDialect
import org.postgresql.util.PGobject
import java.sql.Date
import java.sql.Time
import java.sql.Timestamp

/**
 * Mapper for values without a column type.
 * This mapper is used by the set method in R2dbcPreparedStatementImpl.
 */
class ValueTypeMapper : org.jetbrains.exposed.v1.r2dbc.sql.mappers.TypeMapper {
    // This mapper handles all column types, but only for specific value types
    // It's a fallback for values that don't have a specific mapper

    override fun setValue(
        statement: Statement,
        dialect: DatabaseDialect,
        mapperRegistry: org.jetbrains.exposed.v1.r2dbc.sql.mappers.TypeMapperRegistry,
        columnType: IColumnType<*>,
        value: Any?,
        index: Int
    ): Boolean {
        if (value == null) {
            return false
        }

        val convertedValue = when {
            value is Time -> value.toLocalTime()
            value is Date -> value.toLocalDate()
            value is Timestamp -> value.toLocalDateTime()
            dialect is PostgreSQLDialect && value is PGobject -> Json.of(value.value!!)
            else -> value
        }

        statement.bind(index - 1, convertedValue)
        return true
    }
}
