package org.jetbrains.exposed.v1.r2dbc.mappers

import io.r2dbc.spi.Statement
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.core.vendors.DatabaseDialect
import kotlin.reflect.KClass

/**
 * Mapper for special column types like EntityIDColumnType and ColumnWithTransform.
 * This mapper should be registered first in the registry.
 */
class ExposedColumnTypeMapper : TypeMapper {
    @Suppress("MagicNumber")
    override val priority = 0.5

    override val columnTypes: List<KClass<out IColumnType<*>>>
        get() = listOf(
            EntityIDColumnType::class,
            ColumnWithTransform::class
        )

    override fun setValue(
        statement: Statement,
        dialect: DatabaseDialect,
        typeMapping: R2dbcTypeMapping,
        columnType: IColumnType<*>,
        value: Any?,
        index: Int
    ): Boolean {
        when (columnType) {
            is EntityIDColumnType<*> -> {
                return typeMapping.setValue(statement, dialect, columnType.idColumn.columnType, value, index)
            }
            is ColumnWithTransform<*, *> -> {
                return typeMapping.setValue(statement, dialect, columnType.delegate, value, index)
            }
        }
        return false
    }
}
