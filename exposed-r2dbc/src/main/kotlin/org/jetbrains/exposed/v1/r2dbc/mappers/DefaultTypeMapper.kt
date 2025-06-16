package org.jetbrains.exposed.v1.r2dbc.mappers

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

    @Suppress("MagicNumber")
    override val priority: Double = 0.01

    override fun setValue(
        statement: Statement,
        dialect: DatabaseDialect,
        typeMapping: R2dbcTypeMapping,
        columnType: IColumnType<*>,
        value: Any?,
        index: Int
    ): Boolean {
        if (value == null) {
            if (currentDialect is PostgreSQLDialect) {
                statement.bindNull(index - 1, Object::class.java)
            } else {
                statement.bindNull(index - 1, String::class.java)
            }
        } else {
            statement.bind(index - 1, value)
        }

        return true
    }
}
