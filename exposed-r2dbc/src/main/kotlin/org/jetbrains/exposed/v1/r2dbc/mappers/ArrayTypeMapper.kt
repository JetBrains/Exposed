package org.jetbrains.exposed.v1.r2dbc.mappers

import io.r2dbc.spi.Parameters
import io.r2dbc.spi.R2dbcType
import io.r2dbc.spi.Statement
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.core.vendors.DatabaseDialect
import org.jetbrains.exposed.v1.core.vendors.PostgreSQLDialect
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.UUID
import kotlin.reflect.KClass

/**
 * Mapper for array types.
 */
class ArrayTypeMapper : TypeMapper {
    @Suppress("MagicNumber")
    override val priority = 0.2

    override val columnTypes: List<KClass<out IColumnType<*>>>
        get() = listOf(ArrayColumnType::class)

    override val dialects: List<KClass<out DatabaseDialect>>
        get() = listOf() // Support all dialects

    override fun setValue(
        statement: Statement,
        dialect: DatabaseDialect,
        typeMapping: R2dbcTypeMapping,
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

        // Special handling for arrays containing date/time types
        if (columnType.delegate is IDateColumnType && dialect !is PostgreSQLDialect) {
            // Convert java.sql.Date and java.sql.Timestamp to LocalDate/LocalDateTime/String
            // as R2DBC drivers may not support these types directly in arrays
            val convertedArray = value.map { element ->
                when (element) {
                    is java.sql.Date -> element.toLocalDate()
                    is java.sql.Timestamp -> element.toLocalDateTime()
                    else -> element
                }
            }.toTypedArray()

            val convertedValue = Parameters.`in`(R2dbcType.COLLECTION, convertedArray)
            statement.bind(index - 1, convertedValue)
            return true
        }

        if (dialect !is PostgreSQLDialect) {
            val convertedValue = Parameters.`in`(R2dbcType.COLLECTION, value)
            statement.bind(index - 1, convertedValue)
            return true
        }

        val dimension = columnType.dimensions
        val result = when (dimension) {
            1 -> mapPgArray(dialect, typeMapping, columnType, value)
            else -> error("Unsupported array dimension: $dimension. https://github.com/pgjdbc/r2dbc-postgresql#data-type-mapping")
        }
        statement.bind(index - 1, result)
        return true
    }

    @Suppress("UNCHECKED_CAST")
    private fun mapPgArray(
        dialect: DatabaseDialect,
        typeMapping: R2dbcTypeMapping,
        columnType: ArrayColumnType<*, *>,
        value: Array<*>
    ): Any {
        val list = value.toList()
        val itemType = columnType.delegate

        // Map each element using the typeMapping
        val mappedList = list.map { element ->
            if (element == null) return@map null

            // Create a temporary statement to map the element
            val tempStatement = TempStatement()
            val tempIndex = 1

            // Try to map the element using the typeMapping
            if (typeMapping.setValue(tempStatement, dialect, itemType, element, tempIndex)) {
                tempStatement.getValue(tempIndex - 1)
            } else {
                element
            }
        }

        // For PostgreSQL, we need to explicitly create arrays of primitive types
        return when {
            columnType.delegate is BooleanColumnType -> (mappedList as List<Boolean>).toTypedArray()
            columnType.delegate is ByteColumnType -> (mappedList as List<Byte>).toTypedArray()
            columnType.delegate is UByteColumnType -> (mappedList as List<UByte>).toTypedArray()
            columnType.delegate is ShortColumnType -> (mappedList as List<Short>).toTypedArray()
            columnType.delegate is UShortColumnType -> (mappedList as List<UShort>).toTypedArray()
            columnType.delegate is IntegerColumnType -> (mappedList as List<Int>).toTypedArray()
            columnType.delegate is UIntegerColumnType -> (mappedList as List<UInt>).toTypedArray()
            columnType.delegate is LongColumnType -> (mappedList as List<Long>).toTypedArray()
            columnType.delegate is ULongColumnType -> (mappedList as List<ULong>).toTypedArray()
            columnType.delegate is FloatColumnType -> (mappedList as List<Float>).toTypedArray()
            columnType.delegate is DoubleColumnType -> (mappedList as List<Double>).toTypedArray()
            columnType.delegate is BinaryColumnType -> (mappedList as List<ByteArray>).toTypedArray()
            columnType.delegate is TextColumnType -> (mappedList as List<String>).toTypedArray()
            columnType.delegate is DecimalColumnType -> (mappedList as List<java.math.BigDecimal>).toTypedArray()
            columnType.delegate is UUIDColumnType -> (mappedList as List<UUID>).toTypedArray()
            columnType.delegate is IDateColumnType -> {
                // For date/time types, we need to handle them specially
                // The hasTimePart property tells us whether it's a DATE or DATETIME column
                val hasTimePart = (columnType.delegate as IDateColumnType).hasTimePart

                // For PostgreSQL, we need to ensure the strings are in the correct format
                // for PostgreSQL date arrays
                val stringList = mappedList.map { value ->
                    if (value == null) return@map null

                    // Use ISO format for dates and datetimes
                    if (hasTimePart) {
                        // For DATETIME columns, use ISO datetime format
                        // PostgreSQL expects timestamps in the format 'YYYY-MM-DD HH:MM:SS'
                        val dateStr = value.toString()
                        if (dateStr.contains('T')) {
                            // Convert ISO 8601 format to PostgreSQL timestamp format
                            dateStr.replace('T', ' ').substringBefore('.')
                        } else {
                            dateStr
                        }
                    } else {
                        // For DATE columns, use ISO date format (without time part)
                        // PostgreSQL expects dates in the format 'YYYY-MM-DD'
                        val dateStr = value.toString()
                        if (dateStr.contains('T')) {
                            dateStr.substringBefore('T')
                        } else {
                            dateStr
                        }
                    }
                }

                stringList.toTypedArray()
            }
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
 * Extension function to convert java.sql.Date to LocalDate
 */
private fun java.sql.Date.toLocalDate(): LocalDate = toInstant().atZone(ZoneId.systemDefault()).toLocalDate()

/**
 * Extension function to convert java.sql.Timestamp to LocalDateTime
 */
private fun java.sql.Timestamp.toLocalDateTime(): LocalDateTime = toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime()

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
    is IDateColumnType -> {
        // For date/time types, use Date or Timestamp arrays depending on whether the column type has a time part
        if ((delegate as IDateColumnType).hasTimePart) {
            Array<java.sql.Timestamp>::class.java
        } else {
            Array<java.sql.Date>::class.java
        }
    }
    else -> Array<Any>::class.java
}
