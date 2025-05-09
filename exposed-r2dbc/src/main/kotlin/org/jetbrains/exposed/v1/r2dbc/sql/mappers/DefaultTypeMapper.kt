package org.jetbrains.exposed.v1.r2dbc.sql.mappers

import io.r2dbc.spi.Statement
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.core.vendors.DatabaseDialect
import org.jetbrains.exposed.v1.core.vendors.PostgreSQLDialect
import org.jetbrains.exposed.v1.core.vendors.currentDialect

/**
 * Default mapper for types that aren't handled by other mappers.
 * This mapper should be registered last in the registry.
 *
 * This mapper handles all types, so we don't specify columnTypes or dialects
 * It will be used as a last resort when no other mapper can handle the column type
 */
class DefaultTypeMapper : TypeMapper {

    // TODO we could add ordering for column mappers based on priority
    //  fun priority(): Double = 0.5

    override fun setValue(
        statement: Statement,
        dialect: DatabaseDialect,
        mapperRegistry: TypeMapperRegistry,
        columnType: IColumnType<*>,
        value: Any?,
        index: Int
    ): Boolean {
        if (value == null) {
            // TODO this code could be simplified
            if (currentDialect is PostgreSQLDialect) {
                val typeProvider = currentDialect.dataTypeProvider
                when (columnType.sqlType()) {
                    typeProvider.integerType() -> statement.bindNull(index - 1, Int::class.java)
                    typeProvider.longType() -> statement.bindNull(index - 1, Long::class.java)
                    else -> statement.bindNull(index - 1, String::class.java)
                }
                return true
            } else {
                statement.bindNull(index - 1, String::class.java)
                return true
            }
        } else {
            statement.bind(index - 1, value)
            return true
        }
    }
}
