package org.jetbrains.exposed.v1.r2dbc.mappers

import io.r2dbc.spi.Statement
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.core.vendors.DatabaseDialect
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
            is ByteColumnType -> java.lang.Byte::class.java
            is UByteColumnType -> java.lang.Short::class.java
            is ShortColumnType -> java.lang.Short::class.java
            is UShortColumnType -> java.lang.Integer::class.java
            is IntegerColumnType -> java.lang.Integer::class.java
            is UIntegerColumnType -> java.lang.Long::class.java
            is LongColumnType -> java.lang.Long::class.java
            is ULongColumnType -> java.lang.Long::class.java
            is FloatColumnType -> java.lang.Float::class.java
            is DoubleColumnType -> java.lang.Double::class.java
            is DecimalColumnType -> java.math.BigDecimal::class.java
            is UUIDColumnType -> java.util.UUID::class.java
            is CharacterColumnType -> java.lang.String::class.java
            is BooleanColumnType -> java.lang.Boolean::class.java
            else -> return false
        }
        statement.bindNull(index - 1, columnValueType)
        return true
    }
}
