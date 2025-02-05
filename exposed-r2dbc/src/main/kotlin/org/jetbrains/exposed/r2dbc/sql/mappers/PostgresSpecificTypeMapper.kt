package org.jetbrains.exposed.r2dbc.sql.mappers

import io.r2dbc.postgresql.codec.Json
import io.r2dbc.spi.Statement
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.vendors.DatabaseDialect
import org.jetbrains.exposed.sql.vendors.PostgreSQLDialect
import org.postgresql.util.PGobject
import kotlin.reflect.KClass

/**
 * Mapper for PostgreSQL-specific types.
 */
class PostgresSpecificTypeMapper : TypeMapper {
    override val dialects: List<KClass<out DatabaseDialect>>
        get() = listOf(PostgreSQLDialect::class)

    // We don't specify columnTypes because JsonColumnMarker is an interface, not a class that extends IColumnType<*>
    // Instead, we'll check for JsonColumnMarker in the setValue method

    override fun setValue(
        statement: Statement,
        dialect: DatabaseDialect,
        mapperRegistry: TypeMapperRegistry,
        columnType: IColumnType<*>,
        value: Any?,
        index: Int
    ): Boolean {
        // Check if this is a JSON column type
        if (columnType !is JsonColumnMarker) {
            return false
        }

        if (value == null) {
            statement.bindNull(index - 1, Json::class.java)
            return true
        } else if (value is PGobject) {
            statement.bind(index - 1, Json.of(value.value!!))
            return true
        } else if (value is String) {
            statement.bind(index - 1, Json.of(value))
            return true
        }

        return false
    }
}
