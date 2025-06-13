package org.jetbrains.exposed.v1.r2dbc.mappers

import io.r2dbc.spi.Row
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
     * The priority of this type mapper used for ordering during mapper resolution.
     *
     * Mappers with higher priority values are consulted first when the registry
     * searches for a suitable mapper. This allows more specific or custom mappers
     * to override default implementations. The default priority is 0.
     *
     * Common priority ranges:
     * - High priority (0.5+, 1.]: Custom user mappers that should override defaults
     * - Standard priority (0., 0.5]: Priority of default Exposed type mappers
     * - Default priority (0.)
     *
     * @return The priority value, with higher values indicating higher priority
     */
    val priority: Double
        get() = 0.0

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
     * @param typeMapping The registry of type mappers.
     * @param columnType The column type.
     * @param value The value to set (can be null).
     * @param index The index of the parameter in the statement.
     * @return True if the value was set, false otherwise.
     */
    fun setValue(
        statement: Statement,
        dialect: DatabaseDialect,
        typeMapping: R2dbcTypeMapping,
        columnType: IColumnType<*>,
        value: Any?,
        index: Int
    ): Boolean {
        return false
    }

    /**
     * Retrieves a value from the given row at the specified index.
     * This method attempts to extract and convert a value from the database row
     * to the requested type, taking into account the database dialect and column type.
     *
     * @param T The expected type of the value to retrieve.
     * @param row The R2DBC Row containing the data.
     * @param type The Java Class representing the expected type T.
     * @param index The 1-based index of the column in the row.
     * @param dialect The database dialect being used.
     * @param columnType The column type definition from Exposed.
     * @return A [ValueContainer] containing either the retrieved value
     *         or indicating that no value could be provided. The default implementation
     *         returns [TypeMapperGetValueContainer.NoValue], indicating that this
     *         mapper cannot handle the requested type conversion.
     */
    fun <T> getValue(
        row: Row,
        type: Class<T>,
        index: Int,
        dialect: DatabaseDialect,
        columnType: IColumnType<*>,
    ): ValueContainer<T?> {
        return NoValueContainer()
    }
}
