package org.jetbrains.exposed.r2dbc.sql.mappers

import io.r2dbc.spi.Statement
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.vendors.DatabaseDialect

/**
 * Default mapper for types that aren't handled by other mappers.
 * This mapper should be registered last in the registry.
 *
 * This mapper handles all types, so we don't specify columnTypes or dialects
 * It will be used as a last resort when no other mapper can handle the column type
 */
class DefaultTypeMapper : TypeMapper {

    override fun setValue(
        statement: Statement,
        dialect: DatabaseDialect,
        mapperRegistry: TypeMapperRegistry,
        columnType: IColumnType<*>,
        value: Any?,
        index: Int
    ): Boolean {
        if (value == null) {
            statement.bindNull(index - 1, String::class.java)
            return true
        } else {
            statement.bind(index - 1, value)
            return true
        }
    }
}
