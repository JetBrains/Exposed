package org.jetbrains.exposed.v1.r2dbc.sql.mappers

import io.r2dbc.spi.Statement
import org.jetbrains.exposed.v1.core.IColumnType
import org.jetbrains.exposed.v1.core.vendors.DatabaseDialect
import kotlin.reflect.KClass

/**
 * Interface for mapping values to R2DBC statements.
 * Implementations of this interface are responsible for binding values to statements
 * based on the column type and dialect.
 */
interface TypeMapper {
    /**
     * List of dialects this mapper supports.
     * If empty, the mapper supports all dialects.
     */
    val dialects: List<KClass<out DatabaseDialect>>
        get() = emptyList()

    /**
     * List of column types this mapper supports.
     * If empty, the mapper supports all column types.
     */
    val columnTypes: List<KClass<out IColumnType<*>>>
        get() = emptyList()

    /**
     * Sets a value in the statement.
     * @param statement The statement to set the value in.
     * @param dialect The database dialect.
     * @param mapperRegistry The registry of type mappers.
     * @param columnType The column type.
     * @param value The value to set (can be null).
     * @param index The index of the parameter in the statement.
     * @return True if the value was set, false otherwise.
     */
    fun setValue(
        statement: Statement,
        dialect: DatabaseDialect,
        mapperRegistry: TypeMapperRegistry,
        columnType: IColumnType<*>,
        value: Any?,
        index: Int
    ): Boolean

    // TODO Implement getValue() in same way as setValue()
    //  It could be optional
}
