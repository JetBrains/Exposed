package org.jetbrains.exposed.v1.r2dbc.mappers

import io.r2dbc.spi.Row
import io.r2dbc.spi.Statement
import org.jetbrains.exposed.v1.core.IColumnType
import org.jetbrains.exposed.v1.core.vendors.DatabaseDialect
import java.util.ServiceLoader

interface R2dbcTypeMapping {
    /**
     * Tries to set a value using the registered mappers.
     * @param statement The statement to set the value in.
     * @param dialect The database dialect.
     * @param columnType The column type.
     * @param value The value to set (can be null).
     * @param index The index of the parameter in the statement.
     * @return True if a mapper was found and the value was set, false otherwise.
     */
    fun setValue(
        statement: Statement,
        dialect: DatabaseDialect,
        columnType: IColumnType<*>,
        value: Any?,
        index: Int
    ): Boolean

    /**
     * Tries to get a value using the registered mappers.
     * @param row The row to get the value from.
     * @param type The target type class to convert the value to.
     * @param index The index of the column in the row.
     * @param dialect The database dialect.
     * @param columnType The column type.
     * @return The converted value of type T, or null.
     */
    fun <T> getValue(
        row: Row,
        type: Class<T>,
        index: Int,
        dialect: DatabaseDialect,
        columnType: IColumnType<*>,
    ): T?
}

interface R2dbcRegistryTypeMapping : R2dbcTypeMapping {
    /**
     * Registers a new type mapper.
     * @param mapper The mapper to register.
     * @return This registry for chaining.
     */
    fun register(mapper: TypeMapper): R2dbcRegistryTypeMappingImpl

    companion object {
        /**
         * Lazy-loaded list of mappers from ServiceLoader.
         * This will only be initialized when needed.
         */
        private val serviceLoaderMappers by lazy {
            ServiceLoader.load(TypeMapper::class.java).toList()
        }

        /**
         * Creates a default registry with all the standard mappers.
         * Mappers are loaded using ServiceLoader if available, otherwise falls back to hardcoded defaults.
         * @return A new [R2dbcTypeMapping] with all the standard mappers registered.
         */
        fun default(): R2dbcTypeMapping {
            val registry = R2dbcRegistryTypeMappingImpl()

            // If service loader found mappers, use them
            if (serviceLoaderMappers.isNotEmpty()) {
                serviceLoaderMappers.forEach { registry.register(it) }
            } else {
                // Fallback to hardcoded defaults
                registry.register(ExposedColumnTypeMapper())
                    .register(PrimitiveTypeMapper())
                    .register(DateTimeTypeMapper())
                    .register(BinaryTypeMapper())
                    .register(ArrayTypeMapper())
                    .register(PostgresSpecificTypeMapper())
                    .register(ValueTypeMapper())
                    .register(DefaultTypeMapper())
            }

            return registry
        }
    }
}

/**
 * Registry for type mappers.
 * This class holds a list of type mappers and provides methods to register and use them.
 */
class R2dbcRegistryTypeMappingImpl : R2dbcRegistryTypeMapping {
    private val mappers: MutableList<TypeMapper> = mutableListOf()

    override fun register(mapper: TypeMapper): R2dbcRegistryTypeMappingImpl {
        mappers.add(mapper)
        mappers.sortBy { -it.priority }
        return this
    }

    override fun setValue(
        statement: Statement,
        dialect: DatabaseDialect,
        columnType: IColumnType<*>,
        value: Any?,
        index: Int
    ): Boolean {
        for (mapper in getMatchingMappers(dialect, columnType)) {
            // Try to set the value
            if (mapper.setValue(statement, dialect, this, columnType, value, index)) return true
        }
        return false
    }

    override fun <T> getValue(
        row: Row,
        type: Class<T>,
        index: Int,
        dialect: DatabaseDialect,
        columnType: IColumnType<*>,
    ): T? {
        for (mapper in getMatchingMappers(dialect, columnType)) {
            val result = mapper.getValue(row, type, index, dialect, columnType)
            if (result.isPresent) {
                return result.value() as T?
            }
        }
        return row.get(index - 1, type)
    }

    private fun getMatchingMappers(
        dialect: DatabaseDialect,
        columnType: IColumnType<*>,
    ): List<TypeMapper> {
        return mappers
            .filter { mapper -> mapper.dialects.isEmpty() || mapper.dialects.any { it.isInstance(dialect) } }
            .filter { mapper -> mapper.columnTypes.isEmpty() || mapper.columnTypes.any { it.isInstance(columnType) } }
    }
}
