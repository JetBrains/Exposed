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

    /**
     * Sealed class representing the result of a getValue operation.
     * Contains either a present value or indicates that no value was provided.
     *
     * @param T The type of the value being retrieved.
     * @param isPresent True if the container holds a value, false otherwise.
     */
    sealed class ValueContainer<T>(val isPresent: Boolean) {
        /**
         * Retrieves the present value from the container.
         * @return The present value if [isPresent] is true.
         * @throws IllegalStateException if called on a container with no present value.
         */
        abstract fun value(): T

        /**
         * Represents a container with no present value.
         * This is used when the mapper cannot or should not provide a value.
         */
        class NoValue<T> : ValueContainer<T>(false) {
            override fun value(): T = error("No value provided")
        }

        /**
         * Represents a container with a present value.
         *
         * @param value The present value to be contained.
         */
        class PresentValue<T>(val value: T) : ValueContainer<T>(true) {
            override fun value() = value
        }
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
        return ValueContainer.NoValue()
    }
}
