package org.jetbrains.exposed.v1.r2dbc.sql.mappers

import io.r2dbc.spi.Statement
import org.jetbrains.exposed.v1.sql.*
import org.jetbrains.exposed.v1.sql.vendors.DatabaseDialect
import kotlin.reflect.KClass

/**
 * Mapper for special column types like EntityIDColumnType and ColumnWithTransform.
 * This mapper should be registered first in the registry.
 */
class ExposedColumnTypeMapper : TypeMapper {
    override val columnTypes: List<KClass<out IColumnType<*>>>
        get() = listOf(
            EntityIDColumnType::class,
            ColumnWithTransform::class
        )

    override fun setValue(
        statement: Statement,
        dialect: DatabaseDialect,
        mapperRegistry: TypeMapperRegistry,
        columnType: IColumnType<*>,
        value: Any?,
        index: Int
    ): Boolean {
        when (columnType) {
            is EntityIDColumnType<*> -> {
                return mapperRegistry.setValue(statement, dialect, columnType.idColumn.columnType, value, index)
            }
            is ColumnWithTransform<*, *> -> {
                return mapperRegistry.setValue(statement, dialect, columnType.delegate, value, index)
            }
        }
        return false
    }
}
