package org.jetbrains.exposed.v1.core.statements.api

import org.jetbrains.exposed.v1.core.*
import java.io.InputStream
import java.math.BigDecimal
import java.math.MathContext
import java.util.*

/** Represents a precompiled SQL statement. */
interface PreparedStatementApi {
    /**
     * Sets the value for each column or expression in [args] into the appropriate statement parameter and
     * returns the number of parameters filled.
     */
    fun fillParameters(args: Iterable<Pair<IColumnType<*>, Any?>>): Int {
        args.forEachIndexed { index, (c, v) ->
            c.setParameter(this, index + 1, (c as IColumnType<Any>).valueToDB(v))
        }

        return args.count() + 1
    }

    @Deprecated(
        message = "This operator function will be removed in release 1.0.0. " +
            "Replace with the method `set(index, value, this)` that accepts a third argument for the IColumnType of the parameter value being bound.",
        level = DeprecationLevel.ERROR
    )
    operator fun set(index: Int, value: Any) {
        set(index, value, VarCharColumnType())
    }

    /**
     * Sets the statement parameter at the [index] position to the provided non-null [value],
     * with a type determined by its associated [columnType].
     */
    fun set(index: Int, value: Any, columnType: IColumnType<*>)

    /** Sets the statement parameter at the [index] position to SQL NULL, if allowed wih the specified [columnType]. */
    fun setNull(index: Int, columnType: IColumnType<*>)

    /**
     * Sets the statement parameter at the [index] position to the provided [inputStream],
     * either directly as a BLOB if `setAsBlobObject` is `true` or as determined by the driver.
     */
    fun setInputStream(index: Int, inputStream: InputStream, setAsBlobObject: Boolean)

    @Deprecated(
        message = "This function will be removed in release 1.0.0. " +
            "Replace with the method `setArray(index, this, array)` that accepts an ArrayColumnType as the second argument instead of a string type representation.",
        level = DeprecationLevel.ERROR
    )
    fun setArray(index: Int, type: String, array: Array<*>)

    /**
     * Sets the statement parameter at the [index] position to the provided [array],
     * with a type determined by its associated array column [type].
     */
    fun setArray(index: Int, type: ArrayColumnType<*, *>, array: Array<*>)

    @Suppress("MagicNumber")
    @InternalApi
    fun getArrayColumnType(type: String): ArrayColumnType<*, *> {
        if (type == "CHAR") {
            return ArrayColumnType<Char, List<Char>>(CharacterColumnType())
        }

        if (type.startsWith("DECIMAL")) {
            val specs = type.substringAfter("DECIMAL").trim('(', ')')
                .takeUnless { it.isEmpty() }
                ?.split(", ")
                ?.map { it.toIntOrNull() }
            // same default values used in exposed-core DecimalColumnType()
            val precision = specs?.firstOrNull() ?: MathContext.DECIMAL64.precision
            val scale = specs?.lastOrNull() ?: 20
            return ArrayColumnType<BigDecimal, List<BigDecimal>>(DecimalColumnType(precision, scale))
        }

        val dialect = org.jetbrains.exposed.v1.core.vendors.currentDialect
        return when (type) {
            dialect.dataTypeProvider.byteType() -> ArrayColumnType<Byte, List<Byte>>(ByteColumnType())
            dialect.dataTypeProvider.ubyteType() -> ArrayColumnType<UByte, List<UByte>>(UByteColumnType())
            dialect.dataTypeProvider.shortType() -> ArrayColumnType<Short, List<Short>>(ShortColumnType())
            dialect.dataTypeProvider.ushortType() -> ArrayColumnType<UShort, List<UShort>>(UShortColumnType())
            dialect.dataTypeProvider.integerType() -> ArrayColumnType<Int, List<Int>>(IntegerColumnType())
            dialect.dataTypeProvider.uintegerType() -> ArrayColumnType<UInt, List<UInt>>(UIntegerColumnType())
            dialect.dataTypeProvider.longType() -> ArrayColumnType<Long, List<Long>>(LongColumnType())
            dialect.dataTypeProvider.ulongType() -> ArrayColumnType<ULong, List<ULong>>(ULongColumnType())
            dialect.dataTypeProvider.floatType() -> ArrayColumnType<Float, List<Float>>(FloatColumnType())
            dialect.dataTypeProvider.doubleType() -> ArrayColumnType<Double, List<Double>>(DoubleColumnType())
            dialect.dataTypeProvider.binaryType() -> ArrayColumnType<ByteArray, List<ByteArray>>(BasicBinaryColumnType())
            dialect.dataTypeProvider.booleanType() -> ArrayColumnType<Boolean, List<Boolean>>(BooleanColumnType())
            dialect.dataTypeProvider.uuidType() -> ArrayColumnType<UUID, List<UUID>>(UUIDColumnType())
            else -> ArrayColumnType<String, List<String>>(VarCharColumnType())
        }
    }
}
