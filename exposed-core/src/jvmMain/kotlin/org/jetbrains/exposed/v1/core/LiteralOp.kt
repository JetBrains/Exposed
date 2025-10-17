package org.jetbrains.exposed.v1.core

import java.math.BigDecimal

/**
 * Represents the specified [value] as an SQL literal, using the specified [columnType] to convert the value.
 */
class LiteralOp<T>(
    override val columnType: IColumnType<T & Any>,
    /** Returns the value being used as a literal. */
    val value: T
) : ExpressionWithColumnType<T>() {
    override fun toQueryBuilder(queryBuilder: QueryBuilder): Unit = queryBuilder { +columnType.valueToString(value) }
}

/** Returns the specified [value] as a boolean literal. */
fun booleanLiteral(value: Boolean): LiteralOp<Boolean> = LiteralOp(BooleanColumnType.INSTANCE, value)

/** Returns the specified [value] as a byte literal. */
fun byteLiteral(value: Byte): LiteralOp<Byte> = LiteralOp(ByteColumnType(), value)

/** Returns the specified [value] as a unsigned byte literal. */
fun ubyteLiteral(value: UByte): LiteralOp<UByte> = LiteralOp(UByteColumnType(), value)

/** Returns the specified [value] as a short literal. */
fun shortLiteral(value: Short): LiteralOp<Short> = LiteralOp(ShortColumnType(), value)

/** Returns the specified [value] as a unsigned short literal. */
fun ushortLiteral(value: UShort): LiteralOp<UShort> = LiteralOp(UShortColumnType(), value)

/** Returns the specified [value] as an int literal. */
fun intLiteral(value: Int): LiteralOp<Int> = LiteralOp(IntegerColumnType(), value)

/** Returns the specified [value] as a unsigned int literal. */
fun uintLiteral(value: UInt): LiteralOp<UInt> = LiteralOp(UIntegerColumnType(), value)

/** Returns the specified [value] as a long literal. */
fun longLiteral(value: Long): LiteralOp<Long> = LiteralOp(LongColumnType(), value)

/** Returns the specified [value] as a unsigned long literal. */
fun ulongLiteral(value: ULong): LiteralOp<ULong> = LiteralOp(ULongColumnType(), value)

/** Returns the specified [value] as a float literal. */
fun floatLiteral(value: Float): LiteralOp<Float> = LiteralOp(FloatColumnType(), value)

/** Returns the specified [value] as a double literal. */
fun doubleLiteral(value: Double): LiteralOp<Double> = LiteralOp(DoubleColumnType(), value)

/** Returns the specified [value] as a string literal. */
fun stringLiteral(value: String): LiteralOp<String> = LiteralOp(TextColumnType(), value)

/** Returns the specified [value] as a decimal literal. */
fun decimalLiteral(value: BigDecimal): LiteralOp<BigDecimal> = LiteralOp(DecimalColumnType(value.precision(), value.scale()), value)

/**
 * Returns the specified [value] as an array literal, with elements parsed by the [delegateType] if provided.
 *
 * **Note** If [delegateType] is left `null`, the associated column type will be resolved according to the
 * internal mapping of the element's type in [resolveColumnType].
 *
 * @throws IllegalStateException If no column type mapping is found and a [delegateType] is not provided.
 */
inline fun <reified T : Any> arrayLiteral(value: List<T>, delegateType: ColumnType<T>? = null): LiteralOp<List<T>> =
    arrayLiteral(value, 1, delegateType)

/**
 * Returns the specified [value] as an array literal, with elements parsed by the [delegateType] if provided.
 *
 * **Note** If [delegateType] is left `null`, the associated column type will be resolved according to the
 * internal mapping of the element's type in [resolveColumnType].
 *
 * **Note:** Because arrays can have varying dimensions, you must specify the type of elements
 * and the number of dimensions when using array literals.
 * For example, use `arrayLiteral<Int, List<List<Int>>>(list, dimensions = 2)`.
 *
 * @throws IllegalStateException If no column type mapping is found and a [delegateType] is not provided.
 */
inline fun <reified T : Any, R : List<Any>> arrayLiteral(value: R, dimensions: Int, delegateType: ColumnType<T>? = null): LiteralOp<R> {
    @OptIn(InternalApi::class)
    return LiteralOp(ArrayColumnType(delegateType ?: resolveColumnType(T::class), dimensions = dimensions), value)
}

/** Returns the specified [value] as a literal of type [T]. */
@Suppress("UNCHECKED_CAST", "ComplexMethod")
fun <T, S : T?> ExpressionWithColumnType<S>.asLiteral(value: T): LiteralOp<T> = when {
    value is ByteArray && columnType is BasicBinaryColumnType -> stringLiteral(value.toString(Charsets.UTF_8))
    columnType is ColumnWithTransform<*, *> -> (columnType as ColumnWithTransform<Any, Any>)
        .let { LiteralOp(it.originalColumnType, it.unwrapRecursive(value)) }
    else -> LiteralOp(columnType as IColumnType<T & Any>, value)
} as LiteralOp<T>
