package org.jetbrains.exposed.r2dbc.sql.mappers

import io.r2dbc.spi.Parameters
import io.r2dbc.spi.R2dbcType
import io.r2dbc.spi.Statement
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.vendors.DatabaseDialect
import org.jetbrains.exposed.sql.vendors.PostgreSQLDialect
import kotlin.reflect.KClass

/**
 * Mapper for array types.
 */
class ArrayTypeMapper : TypeMapper {
    override val columnTypes: List<KClass<out IColumnType<*>>>
        get() = listOf(ArrayColumnType::class)

    override val dialects: List<KClass<out DatabaseDialect>>
        get() = listOf() // Support all dialects

    override fun setValue(
        statement: Statement,
        dialect: DatabaseDialect,
        mapperRegistry: TypeMapperRegistry,
        columnType: IColumnType<*>,
        value: Any?,
        index: Int
    ): Boolean {
        if (columnType !is ArrayColumnType<*, *>) return false

        if (value == null) {
            statement.bindNull(index - 1, columnType.arrayDeclaration())
            return true
        }

        if (value !is Array<*>) return false

        if (dialect !is PostgreSQLDialect) {
            val convertedValue = Parameters.`in`(R2dbcType.COLLECTION, value)
            statement.bind(index - 1, convertedValue)
            return true
        }

        val dimension = columnType.dimensions
        val result = when (dimension) {
            1 -> mapPgArray(dialect, mapperRegistry, columnType, value)
            else -> error("Unsupported array dimension: $dimension. https://github.com/pgjdbc/r2dbc-postgresql#data-type-mapping")
        }
        statement.bind(index - 1, result)
        return true
    }

    private fun mapPgArray(
        dialect: DatabaseDialect,
        mapperRegistry: TypeMapperRegistry,
        columnType: ArrayColumnType<*, *>,
        value: Array<*>
    ): Any {
        val list = value.toList()
        val itemType = columnType.delegate

        // Map each element using the mapperRegistry
        val mappedList = list.map { element ->
            if (element == null) return@map null

            // Create a temporary statement to map the element
            val tempStatement = TempStatement()
            val tempIndex = 1

            // Try to map the element using the mapperRegistry
            if (mapperRegistry.setValue(tempStatement, dialect, itemType, element, tempIndex)) {
                tempStatement.getValue(tempIndex - 1)
            } else {
                element
            }
        }

        // For PostgreSQL, we need to explicitly create arrays of primitive types
        return when (columnType.delegate) {
            is BooleanColumnType -> (mappedList as List<Boolean>).toTypedArray()
            is ByteColumnType -> (mappedList as List<Byte>).toTypedArray()
            is UByteColumnType -> (mappedList as List<UByte>).toTypedArray()
            is ShortColumnType -> (mappedList as List<Short>).toTypedArray()
            is UShortColumnType -> (mappedList as List<UShort>).toTypedArray()
            is IntegerColumnType -> (mappedList as List<Int>).toTypedArray()
            is UIntegerColumnType -> (mappedList as List<UInt>).toTypedArray()
            is LongColumnType -> (mappedList as List<Long>).toTypedArray()
            is ULongColumnType -> (mappedList as List<ULong>).toTypedArray()
            is FloatColumnType -> (mappedList as List<Float>).toTypedArray()
            is DoubleColumnType -> (mappedList as List<Double>).toTypedArray()
            is BinaryColumnType -> (mappedList as List<ByteArray>).toTypedArray()
            is TextColumnType -> (mappedList as List<String>).toTypedArray()
            is DecimalColumnType -> (mappedList as List<java.math.BigDecimal>).toTypedArray()
            else -> error("Unsupported array type: $columnType:${columnType::class}")
        }
    }
}

/**
 * A temporary statement used for mapping array elements.
 * This statement only stores the values and doesn't execute anything.
 */
private class TempStatement : Statement {
    private val values = mutableMapOf<Int, Any?>()

    override fun add(): Statement = this

    override fun bind(index: Int, value: Any): Statement {
        values[index] = value
        return this
    }

    override fun bindNull(index: Int, type: Class<*>): Statement {
        values[index] = null
        return this
    }

    fun getValue(index: Int): Any? = values[index]

    // The following methods are not used for our purpose but must be implemented
    override fun execute() = throw UnsupportedOperationException("Not implemented")
    override fun returnGeneratedValues(vararg columns: String?): Statement = this
    override fun fetchSize(rows: Int): Statement = this

    // Additional methods required by the Statement interface
    override fun bind(name: String, value: Any): Statement = this
    override fun bindNull(name: String, type: Class<*>): Statement = this
}

/**
 * Extension function to get the Java class type for an array column type.
 */
private fun ArrayColumnType<*, *>.arrayDeclaration(): Class<out Array<out Any>> = when (delegate) {
    is ByteColumnType -> Array<Byte>::class.java
    is UByteColumnType -> Array<UByte>::class.java
    is ShortColumnType -> Array<Short>::class.java
    is UShortColumnType -> Array<UShort>::class.java
    is IntegerColumnType -> Array<Integer>::class.java
    is UIntegerColumnType -> Array<UInt>::class.java
    is LongColumnType -> Array<Long>::class.java
    is ULongColumnType -> Array<ULong>::class.java
    is FloatColumnType -> Array<Float>::class.java
    is DoubleColumnType -> Array<Double>::class.java
    is DecimalColumnType -> Array<java.math.BigDecimal>::class.java
    is BasicBinaryColumnType, is BlobColumnType -> Array<ByteArray>::class.java
    is UUIDColumnType -> Array<java.util.UUID>::class.java
    is CharacterColumnType -> Array<Char>::class.java
    is BooleanColumnType -> Array<Boolean>::class.java
    else -> Array<Any>::class.java
}
