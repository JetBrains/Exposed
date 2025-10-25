package org.jetbrains.exposed.v1.core

import org.jetbrains.exposed.v1.core.dao.id.CompositeID
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.statements.api.ExposedBlob
import java.math.BigDecimal

/**
 * Represents the specified [value] as a query parameter, using the specified [columnType] to convert the value.
 */
class QueryParameter<T>(
    /** Returns the value being used as a query parameter. */
    val value: T,
    /** Returns the column type of this expression. */
    override val columnType: IColumnType<T & Any>
) : ExpressionWithColumnType<T>() {
    internal val compositeValue: CompositeID? = (value as? EntityID<*>)?.value as? CompositeID

    override fun toQueryBuilder(queryBuilder: QueryBuilder) {
        queryBuilder {
            compositeValue?.let {
                it.values.entries.appendTo { (column, value) ->
                    registerArgument(column.columnType, value)
                }
            } ?: registerArgument(columnType, value)
        }
    }
}

/** Returns the specified [value] as a query parameter with the same type as [column]. */
fun <T : Any> idParam(value: EntityID<T>, column: Column<EntityID<T>>): Expression<EntityID<T>> =
    QueryParameter(value, column.columnType)

/** Returns the specified [value] as a boolean query parameter. */
fun booleanParam(value: Boolean): Expression<Boolean> = QueryParameter(value, BooleanColumnType.INSTANCE)

/** Returns the specified [value] as a byte query parameter. */
fun byteParam(value: Byte): Expression<Byte> = QueryParameter(value, ByteColumnType())

/** Returns the specified [value] as a unsigned byte query parameter. */
fun ubyteParam(value: UByte): Expression<UByte> = QueryParameter(value, UByteColumnType())

/** Returns the specified [value] as a short query parameter. */
fun shortParam(value: Short): Expression<Short> = QueryParameter(value, ShortColumnType())

/** Returns the specified [value] as a unsigned short query parameter. */
fun ushortParam(value: UShort): Expression<UShort> = QueryParameter(value, UShortColumnType())

/** Returns the specified [value] as an int query parameter. */
fun intParam(value: Int): Expression<Int> = QueryParameter(value, IntegerColumnType())

/** Returns the specified [value] as a unsigned int query parameter. */
fun uintParam(value: UInt): Expression<UInt> = QueryParameter(value, UIntegerColumnType())

/** Returns the specified [value] as a long query parameter. */
fun longParam(value: Long): Expression<Long> = QueryParameter(value, LongColumnType())

/** Returns the specified [value] as a unsigned long query parameter. */
fun ulongParam(value: ULong): Expression<ULong> = QueryParameter(value, ULongColumnType())

/** Returns the specified [value] as a float query parameter. */
fun floatParam(value: Float): Expression<Float> = QueryParameter(value, FloatColumnType())

/** Returns the specified [value] as a double query parameter. */
fun doubleParam(value: Double): Expression<Double> = QueryParameter(value, DoubleColumnType())

/** Returns the specified [value] as a string query parameter. */
fun stringParam(value: String): Expression<String> = QueryParameter(value, TextColumnType())

/** Returns the specified [value] as a decimal query parameter. */
fun decimalParam(value: BigDecimal): Expression<BigDecimal> = QueryParameter(value, DecimalColumnType(value.precision(), value.scale()))

/**
 * Returns the specified [value] as a blob query parameter.
 *
 * Set [useObjectIdentifier] to `true` if the parameter should be processed using an OID column instead of a
 * BYTEA column. This is only supported by PostgreSQL databases.
 */
fun blobParam(value: ExposedBlob, useObjectIdentifier: Boolean = false): Expression<ExposedBlob> =
    QueryParameter(value, BlobColumnType(useObjectIdentifier))

/**
 * Returns the specified [value] as an array query parameter, with elements parsed by the [delegateType] if provided.
 *
 * **Note** If [delegateType] is left `null`, the associated column type will be resolved according to the
 * internal mapping of the element's type in [resolveColumnType].
 *
 * @throws IllegalStateException If no column type mapping is found and a [delegateType] is not provided.
 */
inline fun <reified T : Any> arrayParam(value: List<T>, delegateType: ColumnType<T>? = null): Expression<List<T>> =
    arrayParam(value, 1, delegateType)

/**
 * Returns the specified [value] as an array query parameter, with elements parsed by the [delegateType] if provided.
 *
 * **Note** If [delegateType] is left `null`, the associated column type will be resolved according to the
 * internal mapping of the element's type in [resolveColumnType].
 *
 * **Note:** Because arrays can have varying dimensions, you must specify the type of elements
 * and the number of dimensions when using array literals.
 * For example, use `arrayParam<Int, List<List<Int>>>(list, dimensions = 2)`.
 *
 * @throws IllegalStateException If no column type mapping is found and a [delegateType] is not provided.
 */
inline fun <reified T : Any, R : List<Any>> arrayParam(value: R, dimensions: Int, delegateType: ColumnType<T>? = null): Expression<R> {
    @OptIn(InternalApi::class)
    return QueryParameter(value, ArrayColumnType(delegateType ?: resolveColumnType(T::class), dimensions = dimensions))
}

/** Returns the specified [value] as a query parameter of type [T]. */
@Suppress("UNCHECKED_CAST")
fun <T, S : T?> ExpressionWithColumnType<in S>.wrap(value: T): QueryParameter<T> =
    QueryParameter(value, columnType as IColumnType<T & Any>)
