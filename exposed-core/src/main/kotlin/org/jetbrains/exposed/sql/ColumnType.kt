package org.jetbrains.exposed.sql

import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.EntityIDFunctionProvider
import org.jetbrains.exposed.dao.id.IdTable
import org.jetbrains.exposed.sql.statements.DefaultValueMarker
import org.jetbrains.exposed.sql.statements.api.ExposedBlob
import org.jetbrains.exposed.sql.statements.api.PreparedStatementApi
import org.jetbrains.exposed.sql.vendors.*
import java.io.InputStream
import java.math.BigDecimal
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
interface IColumnType {
    /** Returns `true` if the column type is nullable, `false` otherwise. */
    var nullable: Boolean

    /** Returns the SQL type of this column. */
    fun sqlType(): String

    /**
     * Converts the specified [value] (from the database) to an object of the appropriated type, for this column type.
     * Default implementation returns the same instance.
     */
    fun valueFromDB(value: Any): Any = value

    /** Returns an object compatible with the database, from the specified [value], for this column type. */
    fun valueToDB(value: Any?): Any? = value?.let(::notNullValueToDB)

    /** Returns an object compatible with the database, from the specified **non-null** [value], for this column type. */
    fun notNullValueToDB(value: Any): Any = value

    /**
     * Returns the SQL representation of the specified [value], for this column type.
     * If the value is `null` and the column is not nullable, an exception will be thrown.
     * Used when generating an SQL statement and when logging that statement.
     */
    fun valueToString(value: Any?): String = when (value) {
        null -> {
            check(nullable) { "NULL in non-nullable column" }
            "NULL"
        }
        DefaultValueMarker -> "DEFAULT"
        is Iterable<*> -> value.joinToString(",", transform = ::valueToString)
        else -> nonNullValueToString(value)
    }

    /** Returns the SQL representation of the specified **non-null** [value], for this column type. */
    fun nonNullValueToString(value: Any): String = notNullValueToDB(value).toString()

    /**
     * Returns the String representation of the specified [value] when [value] is set as the default for
     * the column.
     * If the value is `null` and the column is not nullable, an exception will be thrown.
     * Used for metadata default value comparison.
     */
    fun valueAsDefaultString(value: Any?): String = when (value) {
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
    fun nonNullValueAsDefaultString(value: Any): String = nonNullValueToString(value)

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
    fun validateValueBeforeUpdate(value: Any?) {}
}

/**
 * Standard column type.
 */
abstract class ColumnType(override var nullable: Boolean = false) : IColumnType {
    override fun toString(): String = sqlType()
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ColumnType

        if (nullable != other.nullable) return false
        return true
    }

    override fun hashCode(): Int = 31 * javaClass.hashCode() + nullable.hashCode()
}

/**
 * Auto-increment column type.
 */
class AutoIncColumnType(
    /** Returns the base column type of this auto-increment column. */
    val delegate: ColumnType,
    private val _autoincSeq: String?,
    private val fallbackSeqName: String
) : IColumnType by delegate {

    private val nextValValue = run {
        val sequence = Sequence(_autoincSeq ?: fallbackSeqName)
        if (delegate is IntegerColumnType) sequence.nextIntVal() else sequence.nextLongVal()
    }

    /** The name of the sequence used to generate new values for this auto-increment column. */
    val autoincSeq: String?
        get() = _autoincSeq.takeIf { currentDialect.supportsCreateSequence }
            ?: fallbackSeqName.takeIf { currentDialect.needsSequenceToAutoInc }

    /** The SQL expression that advances the sequence of this auto-increment column. */
    val nextValExpression: NextVal<*>?
        get() = nextValValue.takeIf { autoincSeq != null }

    private fun resolveAutoIncType(columnType: IColumnType): String = when {
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
            other !is AutoIncColumnType -> false
            delegate != other.delegate -> false
            _autoincSeq != other._autoincSeq -> false
            fallbackSeqName != other.fallbackSeqName -> false
            else -> true
        }
    }

    override fun hashCode(): Int {
        var result = delegate.hashCode()
        result = 31 * result + (_autoincSeq?.hashCode() ?: 0)
        result = 31 * result + fallbackSeqName.hashCode()
        return result
    }
}

/** Returns `true` if this is an auto-increment column, `false` otherwise. */
val IColumnType.isAutoInc: Boolean
    get() = this is AutoIncColumnType || (this is EntityIDColumnType<*> && idColumn.columnType.isAutoInc)

/** Returns the name of the auto-increment sequence of this column. */
val Column<*>.autoIncColumnType: AutoIncColumnType?
    get() = (columnType as? AutoIncColumnType)
        ?: (columnType as? EntityIDColumnType<*>)?.idColumn?.columnType as? AutoIncColumnType

internal fun IColumnType.rawSqlType(): IColumnType = when {
    this is AutoIncColumnType -> delegate
    this is EntityIDColumnType<*> && idColumn.columnType is AutoIncColumnType -> idColumn.columnType.delegate
    else -> this
}

/**
 * Identity column type for storing unique [EntityID] values.
 */
class EntityIDColumnType<T : Comparable<T>>(
    /** The underlying wrapped column storing the identity values. */
    val idColumn: Column<T>
) : ColumnType() {

    init {
        require(idColumn.table is IdTable<*>) { "EntityId supported only for IdTables" }
    }

    override fun sqlType(): String = idColumn.columnType.sqlType()

    override fun notNullValueToDB(value: Any): Any = idColumn.columnType.notNullValueToDB(
        when (value) {
            is EntityID<*> -> value.value
            else -> value
        }
    )

    override fun nonNullValueToString(value: Any): String = idColumn.columnType.nonNullValueToString(
        when (value) {
            is EntityID<*> -> value.value
            else -> value
        }
    )

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
        if (this === other) return true

        return when (other) {
            is EntityIDColumnType<*> -> idColumn == other.idColumn
            is IColumnType -> idColumn.columnType == other
            else -> false
        }
    }

    override fun hashCode(): Int = 31 * super.hashCode() + idColumn.hashCode()
}

// Numeric columns

/**
 * Numeric column for storing 1-byte integers.
 */
class ByteColumnType : ColumnType() {
    override fun sqlType(): String = currentDialect.dataTypeProvider.byteType()

    override fun valueFromDB(value: Any): Byte = when (value) {
        is Byte -> value
        is Number -> value.toByte()
        is String -> value.toByte()
        else -> error("Unexpected value of type Byte: $value of ${value::class.qualifiedName}")
    }
}

/**
 * Numeric column for storing unsigned 1-byte integers.
 *
 * **Note:** If the database being used is not MySQL, MariaDB, or SQL Server, this column will represent the
 * database's 2-byte integer type with a check constraint that ensures storage of only values
 * between 0 and [UByte.MAX_VALUE] inclusive.
 */
class UByteColumnType : ColumnType() {
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

    override fun notNullValueToDB(value: Any): Any {
        val v = when (value) {
            is UByte -> value.toShort()
            else -> value
        }
        return super.notNullValueToDB(v)
    }
}

/**
 * Numeric column for storing 2-byte integers.
 */
class ShortColumnType : ColumnType() {
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
class UShortColumnType : ColumnType() {
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

    override fun notNullValueToDB(value: Any): Any {
        val v = when (value) {
            is UShort -> value.toInt()
            else -> value
        }
        return super.notNullValueToDB(v)
    }
}

/**
 * Numeric column for storing 4-byte integers.
 */
class IntegerColumnType : ColumnType() {
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
class UIntegerColumnType : ColumnType() {
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

    override fun notNullValueToDB(value: Any): Any {
        val v = when (value) {
            is UInt -> value.toLong()
            else -> value
        }
        return super.notNullValueToDB(v)
    }
}

/**
 * Numeric column for storing 8-byte integers.
 */
class LongColumnType : ColumnType() {
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
class ULongColumnType : ColumnType() {
    override fun sqlType(): String = currentDialect.dataTypeProvider.ulongType()
    override fun valueFromDB(value: Any): ULong {
        return when (value) {
            is ULong -> value
            is Long -> value.takeIf { it >= 0 }?.toULong()
            is Number -> {
                if (currentDialect is MysqlDialect) {
                    value.toString().toBigInteger().takeIf {
                        it >= "0".toBigInteger() && it <= ULong.MAX_VALUE.toString().toBigInteger()
                    }?.toString()?.toULong()
                } else {
                    value.toLong().takeIf { it >= 0 }?.toULong()
                }
            }
            is String -> value.toULong()
            else -> error("Unexpected value of type Long: $value of ${value::class.qualifiedName}")
        } ?: error("Negative value but type is ULong: $value")
    }

    override fun setParameter(stmt: PreparedStatementApi, index: Int, value: Any?) {
        val v = when {
            value is ULong && currentDialect is MysqlDialect -> value.toString()
            value is ULong -> value.toLong()
            else -> value
        }
        super.setParameter(stmt, index, v)
    }

    override fun notNullValueToDB(value: Any): Any {
        val v = when {
            value is ULong && currentDialect is MysqlDialect -> value.toString()
            value is ULong -> value.toLong()
            else -> value
        }
        return super.notNullValueToDB(v)
    }
}

/**
 * Numeric column for storing 4-byte (single precision) floating-point numbers.
 */
class FloatColumnType : ColumnType() {
    override fun sqlType(): String = currentDialect.dataTypeProvider.floatType()
    override fun valueFromDB(value: Any): Float = when (value) {
        is Float -> value
        is Number -> value.toFloat()
        is String -> value.toFloat()
        else -> error("Unexpected value of type Float: $value of ${value::class.qualifiedName}")
    }
}

/**
 * Numeric column for storing 8-byte (double precision) floating-point numbers.
 */
class DoubleColumnType : ColumnType() {
    override fun sqlType(): String = currentDialect.dataTypeProvider.doubleType()
    override fun valueFromDB(value: Any): Double = when (value) {
        is Double -> value
        is Number -> value.toDouble()
        is String -> value.toDouble()
        else -> error("Unexpected value of type Double: $value of ${value::class.qualifiedName}")
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
) : ColumnType() {
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
class CharacterColumnType : ColumnType() {
    override fun sqlType(): String = "CHAR"
    override fun valueFromDB(value: Any): Char = when (value) {
        is Char -> value
        is Number -> value.toInt().toChar()
        is String -> value.single()
        else -> error("Unexpected value of type Char: $value of ${value::class.qualifiedName}")
    }

    override fun notNullValueToDB(value: Any): Any = value.toString()
    override fun nonNullValueToString(value: Any): String = "'$value'"
}

/**
 * Base character column for storing strings using the specified text [collate] type.
 */
abstract class StringColumnType(
    /** Returns the collate type used in by this column. */
    val collate: String? = null
) : ColumnType() {
    /** Returns the specified [value] with special characters escaped. */
    protected fun escape(value: String): String = value.map { charactersToEscape[it] ?: it }.joinToString("")

    /** Returns the specified [value] with special characters escaped and wrapped in quotations, if necessary. */
    protected fun escapeAndQuote(value: String): String = when (currentDialect) {
        is PostgreSQLDialect -> "\"${escape(value)}\""
        else -> escape(value)
    }

    override fun valueFromDB(value: Any): Any = when (value) {
        is Clob -> value.characterStream.readText()
        is ByteArray -> String(value)
        else -> value
    }

    override fun nonNullValueToString(value: Any): String = buildString {
        append('\'')
        append(escape(value.toString()))
        append('\'')
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        if (!super.equals(other)) return false

        other as StringColumnType

        if (collate != other.collate) return false

        return true
    }

    override fun hashCode(): Int {
        var result = super.hashCode()
        result = 31 * result + (collate?.hashCode() ?: 0)
        return result
    }

    companion object {
        private val charactersToEscape = mapOf(
            '\'' to "\'\'",
//            '\"' to "\"\"", // no need to escape double quote as we put string in single quotes
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

    override fun validateValueBeforeUpdate(value: Any?) {
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

    override fun validateValueBeforeUpdate(value: Any?) {
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

        if (colLength != other.colLength) return false

        return true
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
open class BasicBinaryColumnType : ColumnType() {
    override fun sqlType(): String = currentDialect.dataTypeProvider.binaryType()

    override fun readObject(rs: ResultSet, index: Int): Any? = rs.getBytes(index)

    override fun valueFromDB(value: Any): Any = when (value) {
        is Blob -> value.binaryStream.use { it.readBytes() }
        is InputStream -> value.use { it.readBytes() }
        else -> value
    }

    override fun nonNullValueToString(value: Any): String = when (value) {
        is ByteArray -> value.toString(Charsets.UTF_8)
        else -> value.toString()
    }
}

/**
 * Binary column for storing binary strings of a specific [length].
 */
open class BinaryColumnType(
    /** Returns the length of the column- */
    val length: Int
) : BasicBinaryColumnType() {
    override fun sqlType(): String = currentDialect.dataTypeProvider.binaryType(length)

    override fun validateValueBeforeUpdate(value: Any?) {
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

        if (length != other.length) return false

        return true
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
) : ColumnType() {
    override fun sqlType(): String = when {
        useObjectIdentifier && currentDialect is PostgreSQLDialect -> "oid"
        useObjectIdentifier -> error("Storing BLOBs using OID columns is only supported by PostgreSQL")
        else -> currentDialect.dataTypeProvider.blobType()
    }
    override fun valueFromDB(value: Any): ExposedBlob = when (value) {
        is ExposedBlob -> value
        is InputStream -> ExposedBlob(value)
        is ByteArray -> ExposedBlob(value)
        else -> error("Unexpected value of type Blob: $value of ${value::class.qualifiedName}")
    }

    override fun notNullValueToDB(value: Any): Any {
        return if (value is Blob) {
            value.binaryStream
        } else {
            value
        }
    }

    override fun nonNullValueToString(value: Any): String {
        if (value !is ExposedBlob) {
            error("Unexpected value of type Blob: $value of ${value::class.qualifiedName}")
        }

        return currentDialect.dataTypeProvider.hexToDb(value.hexString())
    }

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
class UUIDColumnType : ColumnType() {
    override fun sqlType(): String = currentDialect.dataTypeProvider.uuidType()

    override fun valueFromDB(value: Any): UUID = when {
        value is UUID -> value
        value is ByteArray -> ByteBuffer.wrap(value).let { b -> UUID(b.long, b.long) }
        value is String && value.matches(uuidRegexp) -> UUID.fromString(value)
        value is String -> ByteBuffer.wrap(value.toByteArray()).let { b -> UUID(b.long, b.long) }
        else -> error("Unexpected value of type UUID: $value of ${value::class.qualifiedName}")
    }

    override fun notNullValueToDB(value: Any): Any = currentDialect.dataTypeProvider.uuidToDB(valueToUUID(value))

    override fun nonNullValueToString(value: Any): String = "'${valueToUUID(value)}'"

    private fun valueToUUID(value: Any): UUID = when (value) {
        is UUID -> value
        is String -> UUID.fromString(value)
        is ByteArray -> ByteBuffer.wrap(value).let { UUID(it.long, it.long) }
        else -> error("Unexpected value of type UUID: ${value.javaClass.canonicalName}")
    }

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
class BooleanColumnType : ColumnType() {
    override fun sqlType(): String = currentDialect.dataTypeProvider.booleanType()

    override fun valueFromDB(value: Any): Boolean = when (value) {
        is Number -> value.toLong() != 0L
        is String -> currentDialect.dataTypeProvider.booleanFromStringToBoolean(value)
        else -> value.toString().toBoolean()
    }

    override fun nonNullValueToString(value: Any): String =
        currentDialect.dataTypeProvider.booleanToStatementString(value as Boolean)

    override fun notNullValueToDB(value: Any): Any = when {
        value is Boolean &&
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
) : ColumnType() {
    override fun sqlType(): String = currentDialect.dataTypeProvider.integerType()
    private val enumConstants by lazy { klass.java.enumConstants!! }

    @Suppress("UNCHECKED_CAST")
    override fun valueFromDB(value: Any): T = when (value) {
        is Number -> enumConstants[value.toInt()]
        is Enum<*> -> value as T
        else -> error("$value of ${value::class.qualifiedName} is not valid for enum ${klass.simpleName}")
    }

    override fun notNullValueToDB(value: Any): Int = when (value) {
        is Int -> value
        is Enum<*> -> value.ordinal
        else -> error("$value of ${value::class.qualifiedName} is not valid for enum ${klass.simpleName}")
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        if (!super.equals(other)) return false

        other as EnumerationColumnType<*>

        if (klass != other.klass) return false

        return true
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
    colLength: Int
) : VarCharColumnType(colLength) {
    private val enumConstants by lazy { klass.java.enumConstants!!.associateBy { it.name } }

    @Suppress("UNCHECKED_CAST")
    override fun valueFromDB(value: Any): T = when (value) {
        is String -> {
            enumConstants[value] ?: error("$value can't be associated with any from enum ${klass.qualifiedName}")
        }
        is Enum<*> -> value as T
        else -> error("$value of ${value::class.qualifiedName} is not valid for enum ${klass.qualifiedName}")
    }

    override fun notNullValueToDB(value: Any): Any = when (value) {
        is String -> super.notNullValueToDB(value)
        is Enum<*> -> super.notNullValueToDB(value.name)
        else -> error("$value of ${value::class.qualifiedName} is not valid for enum ${klass.qualifiedName}")
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        if (!super.equals(other)) return false

        other as EnumerationNameColumnType<*>

        if (klass != other.klass) return false

        return true
    }

    override fun hashCode(): Int {
        var result = super.hashCode()
        result = 31 * result + klass.hashCode()
        return result
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
) : StringColumnType() {
    override fun sqlType(): String = sql ?: error("Column $name should exist in database")

    @Suppress("UNCHECKED_CAST")
    override fun valueFromDB(value: Any): T = if (value::class.isSubclassOf(Enum::class)) value as T else fromDb(value)

    @Suppress("UNCHECKED_CAST")
    override fun notNullValueToDB(value: Any): Any = toDb(value as T)

    override fun nonNullValueToString(value: Any): String = super.nonNullValueToString(notNullValueToDB(value))
}

// Array columns

/**
 * Array column for storing a collection of elements.
 */
class ArrayColumnType(
    /** Returns the base column type of this array column's individual elements. */
    val delegate: ColumnType,
    /** Returns the maximum amount of allowed elements in this array column. */
    val maximumCardinality: Int? = null
) : ColumnType() {
    override fun sqlType(): String = buildString {
        append(delegate.sqlType())
        when {
            currentDialect is H2Dialect -> append(" ARRAY", maximumCardinality?.let { "[$it]" } ?: "")
            else -> append("[", maximumCardinality?.toString() ?: "", "]")
        }
    }

    /** The base SQL type of this array column's individual elements without extra column identifiers. */
    val delegateType: String
        get() = delegate.sqlType().substringBefore('(')

    override fun valueFromDB(value: Any): Any = when {
        value is java.sql.Array -> (value.array as Array<*>).map { e -> e?.let { delegate.valueFromDB(it) } }
        else -> value
    }

    override fun notNullValueToDB(value: Any): Any = when {
        value is List<*> -> value.map { e -> e?.let { delegate.notNullValueToDB(it) } }.toTypedArray()
        else -> value
    }

    override fun valueToString(value: Any?): String = when (value) {
        is List<*> -> nonNullValueToString(value)
        is Array<*> -> nonNullValueToString(value.toList())
        else -> super.valueToString(value)
    }

    override fun nonNullValueToString(value: Any): String = when {
        value is List<*> -> {
            val prefix = if (currentDialect is H2Dialect) "ARRAY [" else "ARRAY["
            value.joinToString(",", prefix, "]") { delegate.valueToString(it) }
        }
        else -> super.nonNullValueToString(value)
    }

    override fun readObject(rs: ResultSet, index: Int): Any? = rs.getArray(index)

    override fun setParameter(stmt: PreparedStatementApi, index: Int, value: Any?) {
        when {
            value is Array<*> -> stmt.setArray(index, delegateType, value)
            else -> super.setParameter(stmt, index, value)
        }
    }
}

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
    defaultType: ColumnType? = null
): ColumnType = when (klass) {
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
    else -> defaultType ?: error(
        "A column type could not be associated with ${klass.qualifiedName}. Provide an explicit column type argument."
    )
}
