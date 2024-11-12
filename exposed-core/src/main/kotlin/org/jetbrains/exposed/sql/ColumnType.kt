package org.jetbrains.exposed.sql

import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.EntityIDFunctionProvider
import org.jetbrains.exposed.dao.id.IdTable
import org.jetbrains.exposed.sql.statements.api.ExposedBlob
import org.jetbrains.exposed.sql.statements.api.PreparedStatementApi
import org.jetbrains.exposed.sql.vendors.*
import java.io.InputStream
import java.math.BigDecimal
import java.math.BigInteger
import java.math.MathContext
import java.math.RoundingMode
import java.nio.ByteBuffer
import java.sql.Blob
import java.sql.Clob
import java.sql.ResultSet
import java.sql.SQLException
import java.util.*
import kotlin.reflect.KClass
import kotlin.reflect.full.isSubclassOf

/**
 * Interface common to all column types.
 */
@Suppress("TooManyFunctions")
interface IColumnType<T> {
    /** Returns `true` if the column type is nullable, `false` otherwise. */
    var nullable: Boolean

    /** Returns the SQL type of this column. */
    fun sqlType(): String

    /**
     * Converts the specified [value] (from the database) to an object of the appropriated type, for this column type.
     * Default implementation returns the same instance.
     */
    fun valueFromDB(value: Any): T?

    /** Returns an object compatible with the database, from the specified [value], for this column type. */
    fun valueToDB(value: T?): Any? = value?.let(::notNullValueToDB)

    /** Returns an object compatible with the database, from the specified **non-null** [value], for this column type. */
    fun notNullValueToDB(value: T & Any): Any = value

    /**
     * Returns the SQL representation of the specified [value], for this column type.
     * If the value is `null` and the column is not nullable, an exception will be thrown.
     * Used when generating an SQL statement and when logging that statement.
     */
    fun valueToString(value: T?): String = when (value) {
        null -> {
            check(nullable) { "NULL in non-nullable column" }
            "NULL"
        }

        else -> nonNullValueToString(value)
    }

    /** Returns the SQL representation of the specified **non-null** [value], for this column type. */
    fun nonNullValueToString(value: T & Any): String = notNullValueToDB(value).toString()

    /**
     * Returns the String representation of the specified [value] when [value] is set as the default for
     * the column.
     * If the value is `null` and the column is not nullable, an exception will be thrown.
     * Used for metadata default value comparison.
     */
    fun valueAsDefaultString(value: T?): String = when (value) {
        null -> {
            check(nullable) { "NULL in non-nullable column" }
            "NULL"
        }

        else -> nonNullValueAsDefaultString(value)
    }

    /**
     * Returns the String representation of the specified **non-null** [value] when [value] is set as the default for
     * the column.
     */
    fun nonNullValueAsDefaultString(value: T & Any): String = nonNullValueToString(value)

    /** Returns the object at the specified [index] in the [rs]. */
    fun readObject(rs: ResultSet, index: Int): Any? = rs.getObject(index)

    /** Sets the [value] at the specified [index] into the [stmt]. */
    fun setParameter(stmt: PreparedStatementApi, index: Int, value: Any?) {
        if (value == null || value is Op.NULL) {
            stmt.setNull(index, this)
        } else {
            stmt[index] = value
        }
    }

    /**
     * Function checks that provided value is suites the column type and throws [IllegalArgumentException] otherwise.
     * [value] can be of any type (including [Expression])
     * */
    @Throws(IllegalArgumentException::class)
    fun validateValueBeforeUpdate(value: T?) {
    }

    /**
     * Defines the appearance of parameter markers in prepared SQL statements.
     *
     * The default parameter marker is `?`, but it can be overridden in specific cases.
     *
     * For example, H2 uses `? FORMAT JSON` for JSON columns,
     * in Postgres a parameter marker can be explicitly cast to a specific type, etc.
     */
    fun parameterMarker(value: T?) = "?"
}

/**
 * Standard column type.
 */
abstract class ColumnType<T>(override var nullable: Boolean = false) : IColumnType<T> {
    override fun toString(): String = sqlType()
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ColumnType<*>

        return nullable == other.nullable
    }

    override fun hashCode(): Int = 31 * javaClass.hashCode() + nullable.hashCode()
}

/**
 * Auto-increment column type.
 */
class AutoIncColumnType<T>(
    /** Returns the base column type of this auto-increment column. */
    val delegate: ColumnType<T>,
    private val _autoincSeq: String?,
    private val fallbackSeqName: String
) : IColumnType<T> by delegate {

    private var _sequence: Sequence? = null

    /** The sequence used to generate new values for this auto-increment column. */
    val sequence: Sequence?
        get() = _sequence ?: autoincSeq?.let {
            Sequence(
                it,
                startWith = 1,
                minValue = 1,
                maxValue = Long.MAX_VALUE
            )
        }

    constructor(delegate: ColumnType<T>, sequence: Sequence) : this(delegate, sequence.name, sequence.name) {
        _sequence = sequence
    }

    /** The name of the sequence used to generate new values for this auto-increment column. */
    val autoincSeq: String?
        get() = _autoincSeq.takeIf { currentDialect.supportsCreateSequence }
            ?: fallbackSeqName.takeIf { currentDialect.needsSequenceToAutoInc }

    /** The SQL expression that advances the sequence of this auto-increment column. */
    val nextValExpression: NextVal<*>?
        get() = autoincSeq?.let {
            if (delegate is IntegerColumnType) sequence?.nextIntVal() else sequence?.nextLongVal()
        }

    private fun resolveAutoIncType(columnType: IColumnType<*>): String = when {
        columnType is EntityIDColumnType<*> -> resolveAutoIncType(columnType.idColumn.columnType)
        columnType is IntegerColumnType && autoincSeq != null -> currentDialect.dataTypeProvider.integerType()
        columnType is IntegerColumnType -> currentDialect.dataTypeProvider.integerAutoincType()
        columnType is LongColumnType && autoincSeq != null -> currentDialect.dataTypeProvider.longType()
        columnType is LongColumnType -> currentDialect.dataTypeProvider.longAutoincType()
        else -> guessAutoIncTypeBy(columnType.sqlType())
    } ?: error("Unsupported type $delegate for auto-increment")

    private fun guessAutoIncTypeBy(sqlType: String): String? = when (sqlType) {
        currentDialect.dataTypeProvider.longType() -> currentDialect.dataTypeProvider.longAutoincType()
        currentDialect.dataTypeProvider.integerType() -> currentDialect.dataTypeProvider.integerAutoincType()
        currentDialect.dataTypeProvider.ulongType() -> currentDialect.dataTypeProvider.ulongAutoincType()
        currentDialect.dataTypeProvider.uintegerType() -> currentDialect.dataTypeProvider.uintegerAutoincType()
        else -> null
    }

    override fun sqlType(): String = resolveAutoIncType(delegate)

    override fun equals(other: Any?): Boolean {
        return when {
            other == null -> false
            this === other -> true
            this::class != other::class -> false
            other !is AutoIncColumnType<*> -> false
            delegate != other.delegate -> false
            _autoincSeq != other._autoincSeq -> false
            fallbackSeqName != other.fallbackSeqName -> false
            sequence != other.sequence -> false
            else -> true
        }
    }

    override fun hashCode(): Int {
        var result = delegate.hashCode()
        result = 31 * result + (_autoincSeq?.hashCode() ?: 0)
        result = 31 * result + fallbackSeqName.hashCode()
        result = 31 * result + (_sequence?.hashCode() ?: 0)
        return result
    }
}

/** Returns `true` if this is an auto-increment column, `false` otherwise. */
val IColumnType<*>.isAutoInc: Boolean
    get() = this is AutoIncColumnType || (this is EntityIDColumnType<*> && idColumn.columnType.isAutoInc)

/** Returns this column's type cast as [AutoIncColumnType] or `null` if the cast fails. */
val Column<*>.autoIncColumnType: AutoIncColumnType<*>?
    get() = (columnType as? AutoIncColumnType)
        ?: (columnType as? EntityIDColumnType<*>)?.idColumn?.columnType as? AutoIncColumnType

internal fun IColumnType<*>.rawSqlType(): IColumnType<*> = when {
    this is AutoIncColumnType -> delegate
    this is EntityIDColumnType<*> && idColumn.columnType is AutoIncColumnType -> idColumn.columnType.delegate
    else -> this
}

/** Returns whether this column is registered to an [IdTable] and is that table's `id` column. */
internal fun Column<*>.isEntityIdentifier(): Boolean {
    if (columnType !is EntityIDColumnType<*>) return false

    val tableToCheck = ((table as? Alias<*>)?.delegate ?: table) as? IdTable<*>
    return tableToCheck?.id == columnType.idColumn
}

/**
 * Identity column type for storing unique [EntityID] values.
 */
class EntityIDColumnType<T : Any>(
    /** The underlying wrapped column storing the identity values. */
    val idColumn: Column<T>
) : ColumnType<EntityID<T>>() {

    init {
        require(idColumn.table is IdTable<*>) { "EntityId supported only for IdTables" }
    }

    override fun sqlType(): String = idColumn.columnType.sqlType()

    override fun notNullValueToDB(value: EntityID<T>): Any = idColumn.columnType.notNullValueToDB(value.value)

    override fun nonNullValueToString(value: EntityID<T>): String = idColumn.columnType.nonNullValueToString(value.value)

    @Suppress("UNCHECKED_CAST")
    override fun valueFromDB(value: Any): EntityID<T> = EntityIDFunctionProvider.createEntityID(
        when (value) {
            is EntityID<*> -> value.value as T
            else -> idColumn.columnType.valueFromDB(value) as T
        },
        idColumn.table as IdTable<T>
    )

    override fun readObject(rs: ResultSet, index: Int): Any? = idColumn.columnType.readObject(rs, index)

    override fun equals(other: Any?): Boolean {
        if (other !is EntityIDColumnType<*>) return false

        return idColumn == other.idColumn
    }

    override fun hashCode(): Int = 31 * super.hashCode() + idColumn.hashCode()
}

/**
 * An interface defining the transformation between a source column type and a target type.
 *
 * @param Wrapped The type of the column values after transformation
 * @param Unwrapped The type of the column values without transformation
 */
interface ColumnTransformer<Unwrapped, Wrapped> {
    /**
     * Returns the underlying column value without a transformation applied ([Wrapped] -> [Unwrapped]).
     */
    fun unwrap(value: Wrapped): Unwrapped

    /**
     * Applies transformation to the underlying column value ([Unwrapped] -> [Wrapped])
     */
    fun wrap(value: Unwrapped): Wrapped
}

fun <Unwrapped, Wrapped> columnTransformer(
    unwrap: (value: Wrapped) -> Unwrapped,
    wrap: (value: Unwrapped) -> Wrapped
): ColumnTransformer<Unwrapped, Wrapped> {
    return object : ColumnTransformer<Unwrapped, Wrapped> {
        override fun unwrap(value: Wrapped): Unwrapped = unwrap(value)
        override fun wrap(value: Unwrapped): Wrapped = wrap(value)
    }
}

/**
 * A class that handles the transformation between a source column type and a target type.
 *
 * [ColumnWithTransform] extends [ColumnType] and can be used to define columns.
 *
 * It does not apply transformations to `null` values. `null` values are passed directly
 * to the delegated column type. If you need to transform `null` values, use [NullableColumnWithTransform] instead.
 *
 * @param Wrapped The type to which the column value of type [Unwrapped] is transformed
 * @param Unwrapped The type of the column
 * @param delegate The original column's [IColumnType]
 * @param transformer Instance of [ColumnTransformer] that makes actual data transformation
 */
open class ColumnWithTransform<Unwrapped, Wrapped>(
    val delegate: IColumnType<Unwrapped & Any>,
    val transformer: ColumnTransformer<Unwrapped, Wrapped>
) : ColumnType<Wrapped & Any>() {

    /**
     * Recursively unwraps the given value by applying the delegate's transformation.
     *
     * This method will recursively call unwrap on the inner delegate if the delegate
     * is also an instance of [ColumnWithTransform]. This is useful for handling nested
     * transformations.
     *
     * @param value The value to unwrap. Could be null.
     * @return The unwrapped value. Returns the value transformed by the transformer if it's not null.
     */
    open fun unwrapRecursive(value: Wrapped?): Any? {
        return if (delegate is ColumnWithTransform<*, *>) {
            (delegate as ColumnWithTransform<Any, Unwrapped>).unwrapRecursive(transformer.unwrap(value as Wrapped))
        } else {
            value?.let { transformer.unwrap(value) }
        }
    }

    /**
     * Gets the original column type that this column with transformation wraps around.
     *
     * This property will recursively unwrap the delegate column type if the delegate
     * is also an instance of [ColumnWithTransform]. This ensures that you get the
     * original column type, regardless of the number of nested transformations.
     *
     * @return The original column's [IColumnType].
     */
    val originalColumnType: IColumnType<Any>
        get() = when {
            delegate is ColumnWithTransform<*, *> -> delegate.originalColumnType
            else -> delegate as IColumnType<Any>
        }

    override fun sqlType(): String = delegate.sqlType()

    override var nullable: Boolean
        get() = delegate.nullable
        set(value) {
            delegate.nullable = value
        }

    override fun valueFromDB(value: Any): Wrapped? {
        return delegate.valueFromDB(value)?.let { transformer.wrap(it) }
    }

    override fun valueToDB(value: Wrapped?): Any? {
        return delegate.valueToDB(value?.let { transformer.unwrap(it) })
    }

    override fun notNullValueToDB(value: Wrapped & Any): Any {
        return delegate.notNullValueToDB(transformer.unwrap(value)!!)
    }

    override fun setParameter(stmt: PreparedStatementApi, index: Int, value: Any?) {
        return delegate.setParameter(stmt, index, value)
    }
}

/**
 * A class that handles the transformation between a source column type and a target type,
 * but also supports transformations involving `null` values.
 *
 * [NullableColumnWithTransform] extends [ColumnType] and can be used to define columns
 * that can transform `null` to `non-null` values and vice versa.
 *
 * @param Wrapped The type to which the column value of type [Unwrapped] is transformed
 * @param Unwrapped The type of the column
 * @param delegate The original column's [IColumnType]
 * @param transformer Instance of [ColumnTransformer] that makes actual data transformation
 */
open class NullableColumnWithTransform<Unwrapped, Wrapped>(
    delegate: IColumnType<Unwrapped & Any>,
    transformer: ColumnTransformer<Unwrapped, Wrapped>
) : ColumnWithTransform<Unwrapped, Wrapped>(delegate, transformer) {
    /**
     * Recursively unwraps the given value by applying the delegate's transformation.
     *
     * This method will recursively call unwrap on the inner delegate if the delegate
     * is also an instance of [ColumnWithTransform]. This is useful for handling nested
     * transformations. Unlike [ColumnWithTransform.unwrapRecursive], this method allows
     * transformation involving `null` values.
     *
     * @param value The value to unwrap. Could be `null`.
     * @return The unwrapped value. Returns the value transformed by the transformer, which
     * could be `null` if the transformer design allows it.
     */
    override fun unwrapRecursive(value: Wrapped?): Any? {
        return if (delegate is ColumnWithTransform<*, *>) {
            (delegate as ColumnWithTransform<Any, Unwrapped>).unwrapRecursive(transformer.unwrap(value as Wrapped))
        } else {
            transformer.unwrap(value as Wrapped)
        }
    }

    override fun valueFromDB(value: Any): Wrapped? {
        return transformer.wrap(delegate.valueFromDB(value) as Unwrapped)
    }

    override fun valueToDB(value: Wrapped?): Any? {
        // All the values go through the transformer since it can map null values to non-null values
        return delegate.valueToDB(transformer.unwrap(value as Wrapped))
    }

    override fun valueToString(value: Wrapped?): String {
        return delegate.valueToString(transformer.unwrap(value as Wrapped))
    }
}

// Numeric columns

/**
 * Numeric column for storing 1-byte integers.
 */
class ByteColumnType : ColumnType<Byte>() {
    override fun sqlType(): String = currentDialect.dataTypeProvider.byteType()

    override fun valueFromDB(value: Any): Byte = when (value) {
        is Byte -> value
        is Number -> value.toByte()
        is String -> value.toByte()
        else -> error("Unexpected value of type Byte: $value of ${value::class.qualifiedName}")
    }

    override fun valueToDB(value: Byte?): Any? {
        return if (currentDialect is SQLServerDialect) {
            // Workaround for SQL Server JDBC driver mysterious error for in-range values if there's a CHECK constraint
            value?.toShort()
        } else {
            super.valueToDB(value)
        }
    }
}

/**
 * Numeric column for storing unsigned 1-byte integers.
 *
 * **Note:** If the database being used is not MySQL, MariaDB, or SQL Server, this column will represent the
 * database's 2-byte integer type with a check constraint that ensures storage of only values
 * between 0 and [UByte.MAX_VALUE] inclusive.
 */
class UByteColumnType : ColumnType<UByte>() {
    override fun sqlType(): String = currentDialect.dataTypeProvider.ubyteType()

    override fun valueFromDB(value: Any): UByte {
        return when (value) {
            is UByte -> value
            is Byte -> value.toUByte()
            is Number -> value.toShort().toUByte()
            is String -> value.toUByte()
            else -> error("Unexpected value of type Byte: $value of ${value::class.qualifiedName}")
        }
    }

    override fun setParameter(stmt: PreparedStatementApi, index: Int, value: Any?) {
        val v = when (value) {
            is UByte -> value.toShort()
            else -> value
        }
        super.setParameter(stmt, index, v)
    }

    override fun notNullValueToDB(value: UByte): Any = value.toShort()
}

/**
 * Numeric column for storing 2-byte integers.
 */
class ShortColumnType : ColumnType<Short>() {
    override fun sqlType(): String = currentDialect.dataTypeProvider.shortType()
    override fun valueFromDB(value: Any): Short = when (value) {
        is Short -> value
        is Number -> value.toShort()
        is String -> value.toShort()
        else -> error("Unexpected value of type Short: $value of ${value::class.qualifiedName}")
    }
}

/**
 * Numeric column for storing unsigned 2-byte integers.
 *
 * **Note:** If the database being used is not MySQL or MariaDB, this column will represent the database's 4-byte
 * integer type with a check constraint that ensures storage of only values between 0 and [UShort.MAX_VALUE] inclusive.
 */
class UShortColumnType : ColumnType<UShort>() {
    override fun sqlType(): String = currentDialect.dataTypeProvider.ushortType()
    override fun valueFromDB(value: Any): UShort {
        return when (value) {
            is UShort -> value
            is Short -> value.toUShort()
            is Number -> value.toInt().toUShort()
            is String -> value.toUShort()
            else -> error("Unexpected value of type Short: $value of ${value::class.qualifiedName}")
        }
    }

    override fun setParameter(stmt: PreparedStatementApi, index: Int, value: Any?) {
        val v = when (value) {
            is UShort -> value.toInt()
            else -> value
        }
        super.setParameter(stmt, index, v)
    }

    override fun notNullValueToDB(value: UShort): Any = value.toInt()
}

/**
 * Numeric column for storing 4-byte integers.
 */
class IntegerColumnType : ColumnType<Int>() {
    override fun sqlType(): String = currentDialect.dataTypeProvider.integerType()
    override fun valueFromDB(value: Any): Int = when (value) {
        is Int -> value
        is Number -> value.toInt()
        is String -> value.toInt()
        else -> error("Unexpected value of type Int: $value of ${value::class.qualifiedName}")
    }
}

/**
 * Numeric column for storing unsigned 4-byte integers.
 *
 * **Note:** If the database being used is not MySQL or MariaDB, this column will use the database's
 * 8-byte integer type with a check constraint that ensures storage of only values
 * between 0 and [UInt.MAX_VALUE] inclusive.
 */
class UIntegerColumnType : ColumnType<UInt>() {
    override fun sqlType(): String = currentDialect.dataTypeProvider.uintegerType()
    override fun valueFromDB(value: Any): UInt {
        return when (value) {
            is UInt -> value
            is Int -> value.toUInt()
            is Number -> value.toLong().toUInt()
            is String -> value.toUInt()
            else -> error("Unexpected value of type Int: $value of ${value::class.qualifiedName}")
        }
    }

    override fun setParameter(stmt: PreparedStatementApi, index: Int, value: Any?) {
        val v = when (value) {
            is UInt -> value.toLong()
            else -> value
        }
        super.setParameter(stmt, index, v)
    }

    override fun notNullValueToDB(value: UInt): Any = value.toLong()
}

/**
 * Numeric column for storing 8-byte integers.
 */
class LongColumnType : ColumnType<Long>() {
    override fun sqlType(): String = currentDialect.dataTypeProvider.longType()
    override fun valueFromDB(value: Any): Long = when (value) {
        is Long -> value
        is Number -> value.toLong()
        is String -> value.toLong()
        else -> error("Unexpected value of type Long: $value of ${value::class.qualifiedName}")
    }
}

/**
 * Numeric column for storing unsigned 8-byte integers.
 */
class ULongColumnType : ColumnType<ULong>() {
    override fun sqlType(): String = currentDialect.dataTypeProvider.ulongType()
    override fun valueFromDB(value: Any): ULong {
        return when (value) {
            is ULong -> value
            is Long -> value.takeIf { it >= 0 }?.toULong()
            is Double -> value.takeIf { it >= 0 }?.toULong() // For SQLite
            is Number -> {
                valueFromDB(value.toString())
            }

            is String -> {
                value.toBigInteger().takeIf {
                    it >= "0".toBigInteger() && it <= ULong.MAX_VALUE.toString().toBigInteger()
                }?.toString()?.toULong()
            }

            else -> error("Unexpected value of type Long: $value of ${value::class.qualifiedName}")
        } ?: error("Negative value but type is ULong: $value")
    }

    override fun notNullValueToDB(value: ULong): Any {
        val dialect = currentDialect
        return when {
            // PostgreSQLNG does not throw `out of range` error, so it's handled here to prevent storing invalid values
            dialect is PostgreSQLNGDialect -> {
                value.takeIf { it >= 0uL && it <= Long.MAX_VALUE.toULong() }?.toLong()
                    ?: error("Value out of range: $value")
            }

            dialect is PostgreSQLDialect ||
                (dialect is H2Dialect && dialect.h2Mode == H2Dialect.H2CompatibilityMode.PostgreSQL) -> {
                BigInteger(value.toString())
            }

            else -> value.toString()
        }
    }
}

/**
 * Numeric column for storing 4-byte (single precision) floating-point numbers.
 */
class FloatColumnType : ColumnType<Float>() {
    override fun sqlType(): String = currentDialect.dataTypeProvider.floatType()
    override fun valueFromDB(value: Any): Float = when (value) {
        is Float -> value
        is Number -> value.toFloat()
        is String -> value.toFloat()
        else -> error("Unexpected value of type Float: $value of ${value::class.qualifiedName}")
    }

    override fun nonNullValueAsDefaultString(value: Float): String {
        return value.toString().let {
            when {
                // MySQL returns floating-point numbers from metadata without a decimal part as integer strings, whereas other databases
                // append a trailing zero.
                // For example, the value 30f would be `"30"` in MySQL but `"30.0"` in other databases.
                currentDialect is MysqlDialect && it.endsWith(".0") -> it.replace(".0", "")
                else -> it
            }
        }
    }
}

/**
 * Numeric column for storing 8-byte (double precision) floating-point numbers.
 */
class DoubleColumnType : ColumnType<Double>() {
    override fun sqlType(): String = currentDialect.dataTypeProvider.doubleType()
    override fun valueFromDB(value: Any): Double = when (value) {
        is Double -> value
        // Cast as string to prevent precision loss
        is Float -> value.toString().toDouble()
        is Number -> value.toDouble()
        is String -> value.toDouble()
        else -> error("Unexpected value of type Double: $value of ${value::class.qualifiedName}")
    }

    override fun nonNullValueAsDefaultString(value: Double): String {
        return value.toString().let {
            when {
                // MySQL returns floating-point numbers from metadata without a decimal part as integer strings, whereas other databases
                // append a trailing zero.
                // For example, the value 30f would be `"30"` in MySQL but `"30.0"` in other databases.
                currentDialect is MysqlDialect && it.endsWith(".0") -> it.replace(".0", "")
                else -> it
            }
        }
    }
}

/**
 * Numeric column for storing numbers with the specified [precision] and [scale].
 */
class DecimalColumnType(
    /** Total count of significant digits in the whole number. */
    val precision: Int,
    /** Count of decimal digits in the fractional part. */
    val scale: Int
) : ColumnType<BigDecimal>() {
    override fun sqlType(): String = "DECIMAL($precision, $scale)"

    override fun readObject(rs: ResultSet, index: Int): Any? {
        return rs.getObject(index)
    }

    override fun valueFromDB(value: Any): BigDecimal = when (value) {
        is BigDecimal -> value
        is Double -> {
            if (value.isNaN()) {
                throw SQLException("Unexpected value of type Double: NaN of ${value::class.qualifiedName}")
            } else {
                value.toBigDecimal()
            }
        }

        is Float -> {
            if (value.isNaN()) {
                error("Unexpected value of type Float: NaN of ${value::class.qualifiedName}")
            } else {
                value.toBigDecimal()
            }
        }

        is Long -> value.toBigDecimal()
        is Int -> value.toBigDecimal()
        is Short -> value.toLong().toBigDecimal()
        else -> error("Unexpected value of type Decimal: $value of ${value::class.qualifiedName}")
    }.setScale(scale, RoundingMode.HALF_EVEN)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        if (!super.equals(other)) return false

        other as DecimalColumnType

        if (precision != other.precision) return false
        if (scale != other.scale) return false

        return true
    }

    override fun hashCode(): Int {
        var result = super.hashCode()
        result = 31 * result + precision
        result = 31 * result + scale
        return result
    }

    companion object {
        internal val INSTANCE = DecimalColumnType(MathContext.DECIMAL64.precision, 20)
    }
}

// Character columns

/**
 * Character column for storing single characters.
 */
class CharacterColumnType : ColumnType<Char>() {
    override fun sqlType(): String = "CHAR"
    override fun valueFromDB(value: Any): Char = when (value) {
        is Char -> value
        is Number -> value.toInt().toChar()
        is String -> value.single()
        else -> error("Unexpected value of type Char: $value of ${value::class.qualifiedName}")
    }

    override fun notNullValueToDB(value: Char): Any = value.toString()

    override fun nonNullValueToString(value: Char): String = "'$value'"
}

/**
 * Base character column for storing strings using the specified text [collate] type.
 */
abstract class StringColumnType(
    /** Returns the collate type used in by this column. */
    val collate: String? = null
) : ColumnType<String>() {
    /** Returns the specified [value] with special characters escaped. */
    protected fun escape(value: String): String = value.map { charactersToEscape[it] ?: it }.joinToString("")

    /** Returns the specified [value] with special characters escaped and wrapped in quotations, if necessary. */
    protected fun escapeAndQuote(value: String): String = when (currentDialect) {
        is PostgreSQLDialect -> "\"${escape(value)}\""
        else -> escape(value)
    }

    override fun valueFromDB(value: Any): String = when (value) {
        is Clob -> value.characterStream.readText()
        is ByteArray -> String(value)
        else -> value.toString()
    }

    override fun nonNullValueToString(value: String): String = buildString {
        append('\'')
        append(escape(value))
        append('\'')
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        if (!super.equals(other)) return false

        other as StringColumnType

        return collate == other.collate
    }

    override fun hashCode(): Int {
        var result = super.hashCode()
        result = 31 * result + (collate?.hashCode() ?: 0)
        return result
    }

    companion object {
        private val charactersToEscape = mapOf(
            '\'' to "\'\'",
            '\r' to "\\r",
            '\n' to "\\n"
        )
    }
}

/**
 * Character column for storing strings with the exact [colLength] length using the specified [collate] type.
 */
open class CharColumnType(
    /** Returns the maximum length of this column. */
    val colLength: Int = 255,
    collate: String? = null
) : StringColumnType(collate) {
    override fun sqlType(): String = buildString {
        append("CHAR($colLength)")
        if (collate != null) {
            append(" COLLATE ${escapeAndQuote(collate)}")
        }
    }

    override fun validateValueBeforeUpdate(value: String?) {
        if (value is String) {
            val valueLength = value.codePointCount(0, value.length)
            require(valueLength <= colLength) {
                "Value can't be stored to database column because exceeds length ($valueLength > $colLength)"
            }
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        if (!super.equals(other)) return false

        other as CharColumnType

        if (colLength != other.colLength) return false

        if (collate != other.collate) return false
        return true
    }

    override fun hashCode(): Int {
        var result = super.hashCode()
        result = 31 * result + colLength
        return result
    }
}

/**
 * Character column for storing strings with the specified maximum [colLength] using the specified [collate] type.
 */
open class VarCharColumnType(
    /** Returns the maximum length of this column. */
    val colLength: Int = 255,
    collate: String? = null
) : StringColumnType(collate) {
    open fun preciseType() = currentDialect.dataTypeProvider.varcharType(colLength)

    override fun sqlType(): String = buildString {
        append(preciseType())
        if (collate != null) {
            append(" COLLATE ${escapeAndQuote(collate)}")
        }
    }

    override fun validateValueBeforeUpdate(value: String?) {
        if (value is String) {
            val valueLength = value.codePointCount(0, value.length)
            require(valueLength <= colLength) {
                "Value can't be stored to database column because exceeds length ($valueLength > $colLength)"
            }
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        if (!super.equals(other)) return false

        other as VarCharColumnType

        return colLength == other.colLength
    }

    override fun hashCode(): Int {
        var result = super.hashCode()
        result = 31 * result + colLength
        return result
    }
}

/**
 * Character column for storing strings of arbitrary length using the specified [collate] type.
 */
open class TextColumnType(
    collate: String? = null,
    /** Whether content will be loaded immediately when data is retrieved from the database. */
    val eagerLoading: Boolean = false
) : StringColumnType(collate) {
    /** The exact SQL type representing this character type. */
    open fun preciseType() = currentDialect.dataTypeProvider.textType()

    override fun sqlType(): String = buildString {
        append(preciseType())
        if (collate != null) {
            append(" COLLATE ${escapeAndQuote(collate)}")
        }
    }

    override fun readObject(rs: ResultSet, index: Int): Any? {
        val value = super.readObject(rs, index)
        return if (eagerLoading && value != null) {
            valueFromDB(value)
        } else {
            value
        }
    }
}

open class MediumTextColumnType(
    collate: String? = null,
    eagerLoading: Boolean = false
) : TextColumnType(collate, eagerLoading) {
    override fun preciseType(): String = currentDialect.dataTypeProvider.mediumTextType()
}

open class LargeTextColumnType(
    collate: String? = null,
    eagerLoading: Boolean = false
) : TextColumnType(collate, eagerLoading) {
    override fun preciseType(): String = currentDialect.dataTypeProvider.largeTextType()
}

// Binary columns

/**
 * Binary column for storing binary strings of variable and _unlimited_ length.
 */
open class BasicBinaryColumnType : ColumnType<ByteArray>() {
    override fun sqlType(): String = currentDialect.dataTypeProvider.binaryType()

    override fun readObject(rs: ResultSet, index: Int): Any? = rs.getBytes(index)

    override fun valueFromDB(value: Any): ByteArray = when (value) {
        is Blob -> value.binaryStream.use { it.readBytes() }
        is InputStream -> value.use { it.readBytes() }
        is ByteArray -> value
        else -> error("Unexpected value $value of type ${value::class.qualifiedName}")
    }

    override fun nonNullValueToString(value: ByteArray): String = value.toString(Charsets.UTF_8)
}

/**
 * Binary column for storing binary strings of a specific [length].
 */
open class BinaryColumnType(
    /** Returns the length of the column- */
    val length: Int
) : BasicBinaryColumnType() {
    override fun sqlType(): String = currentDialect.dataTypeProvider.binaryType(length)

    override fun validateValueBeforeUpdate(value: ByteArray?) {
        if (value is ByteArray) {
            val valueLength = value.size
            require(valueLength <= length) {
                "Value can't be stored to database column because exceeds length ($valueLength > $length)"
            }
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        if (!super.equals(other)) return false

        other as BinaryColumnType

        return length == other.length
    }

    override fun hashCode(): Int {
        var result = super.hashCode()
        result = 31 * result + length
        return result
    }
}

/**
 * Binary column for storing BLOBs.
 */
class BlobColumnType(
    /** Returns whether an OID column should be used instead of BYTEA. This value only applies to PostgreSQL databases. */
    val useObjectIdentifier: Boolean = false
) : ColumnType<ExposedBlob>() {
    override fun sqlType(): String = when {
        useObjectIdentifier && currentDialect is PostgreSQLDialect -> "oid"
        useObjectIdentifier -> error("Storing BLOBs using OID columns is only supported by PostgreSQL")
        else -> currentDialect.dataTypeProvider.blobType()
    }

    override fun valueFromDB(value: Any): ExposedBlob = when (value) {
        is ExposedBlob -> value
        is InputStream -> ExposedBlob(value)
        is ByteArray -> ExposedBlob(value)
        is Blob -> ExposedBlob(value.binaryStream)
        else -> error("Unexpected value of type Blob: $value of ${value::class.qualifiedName}")
    }

    override fun nonNullValueToString(value: ExposedBlob): String = currentDialect.dataTypeProvider.hexToDb(value.hexString())

    override fun readObject(rs: ResultSet, index: Int) = when {
        currentDialect is SQLServerDialect -> rs.getBytes(index)?.let(::ExposedBlob)
        currentDialect is PostgreSQLDialect && useObjectIdentifier -> rs.getBlob(index)?.binaryStream?.let(::ExposedBlob)
        else -> rs.getBinaryStream(index)?.let(::ExposedBlob)
    }

    override fun setParameter(stmt: PreparedStatementApi, index: Int, value: Any?) {
        when (val toSetValue = (value as? ExposedBlob)?.inputStream ?: value) {
            is InputStream -> stmt.setInputStream(index, toSetValue, useObjectIdentifier)
            null, is Op.NULL -> stmt.setNull(index, this)
            else -> super.setParameter(stmt, index, toSetValue)
        }
    }
}

/**
 * Binary column for storing [UUID].
 */
class UUIDColumnType : ColumnType<UUID>() {
    override fun sqlType(): String = currentDialect.dataTypeProvider.uuidType()

    override fun valueFromDB(value: Any): UUID = when {
        value is UUID -> value
        value is ByteArray -> ByteBuffer.wrap(value).let { b -> UUID(b.long, b.long) }
        value is String && value.matches(uuidRegexp) -> UUID.fromString(value)
        value is String -> ByteBuffer.wrap(value.toByteArray()).let { b -> UUID(b.long, b.long) }
        else -> error("Unexpected value of type UUID: $value of ${value::class.qualifiedName}")
    }

    override fun notNullValueToDB(value: UUID): Any = currentDialect.dataTypeProvider.uuidToDB(value)

    override fun nonNullValueToString(value: UUID): String = "'$value'"

    override fun readObject(rs: ResultSet, index: Int): Any? = when (currentDialect) {
        is MariaDBDialect -> rs.getBytes(index)
        else -> super.readObject(rs, index)
    }

    companion object {
        private val uuidRegexp =
            Regex("[0-9A-F]{8}-[0-9A-F]{4}-[0-9A-F]{4}-[0-9A-F]{4}-[0-9A-F]{12}", RegexOption.IGNORE_CASE)
    }
}

// Boolean columns

/**
 * Boolean column for storing boolean values.
 */
class BooleanColumnType : ColumnType<Boolean>() {
    override fun sqlType(): String = currentDialect.dataTypeProvider.booleanType()

    override fun valueFromDB(value: Any): Boolean = when (value) {
        is Number -> value.toLong() != 0L
        is String -> currentDialect.dataTypeProvider.booleanFromStringToBoolean(value)
        else -> value.toString().toBoolean()
    }

    override fun nonNullValueToString(value: Boolean): String =
        currentDialect.dataTypeProvider.booleanToStatementString(value)

    override fun notNullValueToDB(value: Boolean): Any = when {
        (currentDialect is OracleDialect || currentDialect.h2Mode == H2Dialect.H2CompatibilityMode.Oracle) ->
            nonNullValueToString(value)

        else -> value
    }

    companion object {
        internal val INSTANCE = BooleanColumnType()
    }
}

// Enumeration columns

/**
 * Enumeration column for storing enums of type [klass] by their ordinal.
 */
class EnumerationColumnType<T : Enum<T>>(
    /** Returns the enum class used in this column type. */
    val klass: KClass<T>
) : ColumnType<T>() {
    override fun sqlType(): String = currentDialect.dataTypeProvider.integerType()
    private val enumConstants by lazy { klass.java.enumConstants!! }

    @Suppress("UNCHECKED_CAST")
    override fun valueFromDB(value: Any): T = when (value) {
        is Number -> enumConstants[value.toInt()]
        is Enum<*> -> value as T
        else -> error("$value of ${value::class.qualifiedName} is not valid for enum ${klass.simpleName}")
    }

    override fun notNullValueToDB(value: T): Int = value.ordinal

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        if (!super.equals(other)) return false

        other as EnumerationColumnType<*>

        return klass == other.klass
    }

    override fun hashCode(): Int {
        var result = super.hashCode()
        result = 31 * result + klass.hashCode()
        return result
    }
}

/**
 * Enumeration column for storing enums of type [klass] by their name.
 */
class EnumerationNameColumnType<T : Enum<T>>(
    /** Returns the enum class used in this column type. */
    val klass: KClass<T>,
    val colLength: Int
) : ColumnType<T>() {
    private val enumConstants by lazy { klass.java.enumConstants!!.associateBy { it.name } }

    override fun sqlType(): String = currentDialect.dataTypeProvider.varcharType(colLength)

    @Suppress("UNCHECKED_CAST")
    override fun valueFromDB(value: Any): T = when (value) {
        is String -> {
            enumConstants[value] ?: error("$value can't be associated with any from enum ${klass.qualifiedName}")
        }

        is Enum<*> -> value as T
        else -> error("$value of ${value::class.qualifiedName} is not valid for enum ${klass.qualifiedName}")
    }

    override fun notNullValueToDB(value: T): Any = value.name

    override fun nonNullValueToString(value: T): String = buildString {
        append('\'')
        append(escape(value.name))
        append('\'')
    }

    override fun validateValueBeforeUpdate(value: T?) {
        if (value != null) {
            val valueLength = value.name.codePointCount(0, value.name.length)
            require(valueLength <= colLength) {
                "Value can't be stored to database column because exceeds length ($valueLength > $colLength)"
            }
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        if (!super.equals(other)) return false

        other as EnumerationNameColumnType<*>

        return klass == other.klass
    }

    override fun hashCode(): Int {
        var result = super.hashCode()
        result = 31 * result + klass.hashCode()
        return result
    }

    private fun escape(value: String): String = value.map { charactersToEscape[it] ?: it }.joinToString("")

    companion object {
        private val charactersToEscape = mapOf(
            '\'' to "\'\'",
            '\r' to "\\r",
            '\n' to "\\n"
        )
    }
}

/**
 * Enumeration column for storing enums of type [T] using the custom SQL type [sql].
 */
class CustomEnumerationColumnType<T : Enum<T>>(
    /** Returns the name of this column type instance. */
    val name: String,
    /** Returns the SQL definition used for this column type. */
    val sql: String?,
    /** Returns the function that converts a value received from a database to an enumeration instance [T]. */
    val fromDb: (Any) -> T,
    /** Returns the function that converts an enumeration instance [T] to a value that will be stored to a database. */
    val toDb: (T) -> Any
) : ColumnType<T>() {
    override fun sqlType(): String = sql ?: error("Column $name should exist in database")

    @Suppress("UNCHECKED_CAST")
    override fun valueFromDB(value: Any): T = if (value::class.isSubclassOf(Enum::class)) value as T else fromDb(value)

    override fun notNullValueToDB(value: T): Any = toDb(value)

    override fun nonNullValueToString(value: T): String = buildString {
        append('\'')
        append(escape(notNullValueToDB(value).toString()))
        append('\'')
    }

    private fun escape(value: String): String = value.map { charactersToEscape[it] ?: it }.joinToString("")

    companion object {
        private val charactersToEscape = mapOf(
            '\'' to "\'\'",
            '\r' to "\\r",
            '\n' to "\\n"
        )
    }
}

// Array columns

/**
 * Multi-dimensional array column type for storing a collection of nested elements.
 *
 * @property delegate The base column type associated with this array column's individual elements.
 * @property dimensions The number of dimensions of the multi-dimensional array.
 * @property maximumCardinality The maximum cardinality (number of allowed elements) for each dimension of the array.
 *
 * **Note:** The maximum cardinality is considered for each dimension, but it is ignored by the PostgreSQL database.
 */
class ArrayColumnType<T, R : List<Any?>>(
    val delegate: IColumnType<T & Any>,
    val maximumCardinality: List<Int>? = null,
    val dimensions: Int = 1
) : ColumnType<R>() {
    /**
     * Constructor with maximum cardinality for a single dimension.
     *
     * @param delegate The base column type associated with this array column's individual elements.
     * @param maximumCardinality The maximum cardinality (number of allowed elements) for the array.
     */
    constructor(delegate: ColumnType<T & Any>, maximumCardinality: Int? = null) : this(delegate, maximumCardinality?.let { listOf(it) })

    /**
     * The SQL type definition of the delegate column type without any potential array dimensions.
     */
    val delegateType: String
        get() = delegate.sqlType().substringBefore('(')

    override fun sqlType(): String = buildString {
        if (maximumCardinality != null) {
            require(maximumCardinality.size == dimensions) {
                "The size of cardinality list must be equal to the amount of array dimensions. " +
                    "Dimensions: $dimensions, cardinality size: ${maximumCardinality.size}"
            }
        }
        append(delegate.sqlType())
        when {
            currentDialect is H2Dialect -> {
                require(dimensions == 1) {
                    "H2 does not support multidimensional arrays. " +
                        "`dimensions` parameter for H2 database must be 1"
                }
                append(" ARRAY", maximumCardinality?.let { "[${it.first()}]" } ?: "")
            }

            else -> append(maximumCardinality?.let { cardinality -> cardinality.joinToString("") { "[$it]" } } ?: "[]".repeat(dimensions))
        }
    }

    override fun notNullValueToDB(value: R): Any {
        return recursiveNotNullValueToDB(value, dimensions)
    }

    private fun recursiveNotNullValueToDB(value: Any, level: Int): Array<Any?> = when {
        level > 1 -> (value as List<Any>).map { recursiveNotNullValueToDB(it, level - 1) }.toTypedArray()
        else -> (value as List<T>).map { it?.let { delegate.notNullValueToDB(it) } }.toTypedArray()
    }

    @Suppress("UNCHECKED_CAST")
    override fun valueFromDB(value: Any): R? {
        return when (value) {
            is Array<*> -> recursiveValueFromDB(value, dimensions) as R?
            is java.sql.Array -> recursiveValueFromDB(value.array, dimensions) as R?
            else -> value as R?
        }
    }

    private fun recursiveValueFromDB(value: Any?, level: Int): List<Any?> = when {
        level > 1 -> (value as Array<Any?>).map { recursiveValueFromDB(it, level - 1) }
        else -> (value as Array<Any?>).map { it?.let { delegate.valueFromDB(it) } }
    }

    override fun readObject(rs: ResultSet, index: Int): Any? {
        return rs.getArray(index)?.array
    }

    override fun setParameter(stmt: PreparedStatementApi, index: Int, value: Any?) {
        when {
            value is Array<*> && isArrayOfByteArrays(value) ->
                stmt.setArray(index, delegateType, Array(value.size) { value[it] as ByteArray })

            value is Array<*> -> stmt.setArray(index, delegateType, value)
            else -> super.setParameter(stmt, index, value)
        }
    }

    override fun nonNullValueToString(value: R): String {
        return arrayLiteralPrefix() + recursiveNonNullValueToString(value, dimensions)
    }

    private fun recursiveNonNullValueToString(value: Any?, level: Int): String = when {
        level > 1 -> (value as List<Any?>).joinToString(",", "[", "]") { recursiveNonNullValueToString(it, level - 1) }
        else -> (value as List<T & Any>).joinToString(",", "[", "]") { delegate.nonNullValueToString(it) }
    }

    override fun nonNullValueAsDefaultString(value: R): String {
        return arrayLiteralPrefix() + recursiveNonNullValueAsDefaultString(value, dimensions)
    }

    private fun recursiveNonNullValueAsDefaultString(value: Any?, level: Int): String = when {
        level > 1 -> (value as List<Any?>).joinToString(",", "[", "]") { recursiveNonNullValueAsDefaultString(it, level - 1) }
        else -> (value as List<T & Any>).joinToString(",", "[", "]") { delegate.nonNullValueAsDefaultString(it) }
    }

    private fun arrayLiteralPrefix(): String {
        return when {
            currentDialect is H2Dialect -> "ARRAY "
            else -> "ARRAY"
        }
    }
}

private fun isArrayOfByteArrays(value: Array<*>) =
    value.all { it is ByteArray }

// Date/Time columns

/**
 * Marker interface for date/datetime related column types.
 **/
interface IDateColumnType {
    val hasTimePart: Boolean
}

// JSON/JSONB columns

/**
 * Marker interface for json/jsonb related column types.
 */
interface JsonColumnMarker {
    val usesBinaryFormat: Boolean
}

/**
 * Returns the [ColumnType] commonly associated with storing values of type [T], or the [defaultType] if a mapping
 * does not exist for type [T].
 *
 * @throws IllegalStateException If no column type mapping is found and a [defaultType] is not provided.
 */
@InternalApi
fun <T : Any> resolveColumnType(
    klass: KClass<T>,
    defaultType: ColumnType<*>? = null
): ColumnType<T> {
    val type = when (klass) {
        Boolean::class -> BooleanColumnType()
        Byte::class -> ByteColumnType()
        UByte::class -> UByteColumnType()
        Short::class -> ShortColumnType()
        UShort::class -> UShortColumnType()
        Int::class -> IntegerColumnType()
        UInt::class -> UIntegerColumnType()
        Long::class -> LongColumnType()
        ULong::class -> ULongColumnType()
        Float::class -> FloatColumnType()
        Double::class -> DoubleColumnType()
        String::class -> TextColumnType()
        Char::class -> CharacterColumnType()
        ByteArray::class -> BasicBinaryColumnType()
        BigDecimal::class -> DecimalColumnType.INSTANCE
        UUID::class -> UUIDColumnType()
        else -> defaultType
    } as? ColumnType<T>

    return type ?: error(
        "A column type could not be associated with ${klass.qualifiedName}. Provide an explicit column type argument."
    )
}
