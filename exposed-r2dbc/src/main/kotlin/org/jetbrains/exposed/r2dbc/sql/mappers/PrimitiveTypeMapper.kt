package org.jetbrains.exposed.r2dbc.sql.mappers

import io.r2dbc.spi.Statement
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.vendors.DatabaseDialect
import kotlin.reflect.KClass

/**
 * Mapper for primitive types (Int, Long, Float, Double, etc.).
 */
class PrimitiveTypeMapper : TypeMapper {
    override val columnTypes: List<KClass<out IColumnType<*>>>
        get() = listOf(
            ByteColumnType::class,
            UByteColumnType::class,
            ShortColumnType::class,
            UShortColumnType::class,
            IntegerColumnType::class,
            UIntegerColumnType::class,
            LongColumnType::class,
            ULongColumnType::class,
            FloatColumnType::class,
            DoubleColumnType::class,
            DecimalColumnType::class,
            BooleanColumnType::class,
            CharacterColumnType::class,
            UUIDColumnType::class
        )

    override fun setValue(
        statement: Statement,
        dialect: DatabaseDialect,
        mapperRegistry: TypeMapperRegistry,
        columnType: IColumnType<*>,
        value: Any?,
        index: Int
    ): Boolean {
        if (value != null) {
            statement.bind(index - 1, value)
            return true
        }

        val columnValueType = when (columnType) {
            is ByteColumnType -> Byte::class.java
            is UByteColumnType -> UByte::class.java
            is ShortColumnType -> Short::class.java
            is UShortColumnType -> UShort::class.java
            is IntegerColumnType -> Integer::class.java
            is UIntegerColumnType -> UInt::class.java
            is LongColumnType -> Long::class.java
            is ULongColumnType -> ULong::class.java
            is FloatColumnType -> Float::class.java
            is DoubleColumnType -> Double::class.java
            is DecimalColumnType -> java.math.BigDecimal::class.java
            is UUIDColumnType -> java.util.UUID::class.java
            is CharacterColumnType -> Char::class.java
            is BooleanColumnType -> Boolean::class.java
            else -> return false
        }
        statement.bindNull(index - 1, columnValueType)
        return true
    }
}
