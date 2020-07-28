package org.jetbrains.exposed.sql

import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.EntityIDFunctionProvider
import org.jetbrains.exposed.dao.id.IdTable
import org.jetbrains.exposed.sql.statements.DefaultValueMarker
import org.jetbrains.exposed.sql.statements.api.ExposedBlob
import org.jetbrains.exposed.sql.statements.api.PreparedStatementApi
import org.jetbrains.exposed.sql.vendors.currentDialect
import java.io.InputStream
import java.math.BigDecimal
import java.math.RoundingMode
import java.nio.ByteBuffer
import java.sql.Blob
import java.sql.Clob
import java.sql.ResultSet
import java.util.*
import kotlin.reflect.KClass

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

    /** Returns the object at the specified [index] in the [rs]. */
    fun readObject(rs: ResultSet, index: Int): Any? = rs.getObject(index)

    /** Sets the [value] at the specified [index] into the [stmt]. */
    fun setParameter(stmt: PreparedStatementApi, index: Int, value: Any?) {
        stmt[index] = value
    }
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
    _autoincSeq: String
) : IColumnType by delegate {

    /** Returns the name of the sequence used to generate new values for this auto-increment column. */
    val autoincSeq: String? = _autoincSeq
        get() = if (currentDialect.needsSequenceToAutoInc) field else null

    private fun resolveAutoIncType(columnType: IColumnType): String = when (columnType) {
        is EntityIDColumnType<*> -> resolveAutoIncType(columnType.idColumn.columnType)
        is IntegerColumnType -> currentDialect.dataTypeProvider.integerAutoincType()
        is LongColumnType -> currentDialect.dataTypeProvider.longAutoincType()
        else -> guessAutoIncTypeBy(columnType.sqlType())
    } ?: error("Unsupported type $delegate for auto-increment")

    private fun guessAutoIncTypeBy(sqlType: String): String? = when (sqlType) {
        currentDialect.dataTypeProvider.longType() -> currentDialect.dataTypeProvider.longAutoincType()
        currentDialect.dataTypeProvider.integerType() -> currentDialect.dataTypeProvider.integerAutoincType()
        else -> null
    }

    override fun sqlType(): String = resolveAutoIncType(delegate)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as AutoIncColumnType

        if (delegate != other.delegate) return false

        return true
    }
}

/** Returns `true` if this is an auto-increment column, `false` otherwise. */
val IColumnType.isAutoInc: Boolean get() = this is AutoIncColumnType || (this is EntityIDColumnType<*> && idColumn.columnType.isAutoInc)
/** Returns the name of the auto-increment sequence of this column. */
val Column<*>.autoIncSeqName: String?
    get() = (columnType as? AutoIncColumnType)?.autoincSeq ?: (columnType as? EntityIDColumnType<*>)?.idColumn?.autoIncSeqName

class EntityIDColumnType<T : Comparable<T>>(val idColumn: Column<T>) : ColumnType() {

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

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as EntityIDColumnType<*>

        if (idColumn != other.idColumn) return false

        return true
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
 */
@ExperimentalUnsignedTypes
class UByteColumnType : ColumnType() {
    override fun sqlType(): String = currentDialect.dataTypeProvider.ubyteType()

    override fun valueFromDB(value: Any): UByte {
        return when (value) {
            is UByte -> value
            is Byte -> value.takeIf { it >= 0 }?.toUByte()
            is Number -> value.toByte().takeIf { it >= 0 }?.toUByte()
            is String -> value.toUByte()
            else -> error("Unexpected value of type Byte: $value of ${value::class.qualifiedName}")
        } ?: error("negative value but type is UByte: $value")
    }

    override fun setParameter(stmt: PreparedStatementApi, index: Int, value: Any?) {
        val v = if (value is UByte) value.toByte() else value
        super.setParameter(stmt, index, v)
    }

    override fun notNullValueToDB(value: Any): Any {
        val v = if (value is UByte) value.toByte() else value
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
 */
@ExperimentalUnsignedTypes
class UShortColumnType : ColumnType() {
    override fun sqlType(): String = currentDialect.dataTypeProvider.ushortType()
    override fun valueFromDB(value: Any): UShort {
        return when (value) {
            is UShort -> value
            is Short -> value.takeIf { it >= 0 }?.toUShort()
            is Number -> value.toShort().takeIf { it >= 0 }?.toUShort()
            is String -> value.toUShort()
            else -> error("Unexpected value of type Short: $value of ${value::class.qualifiedName}")
        } ?: error("negative value but type is UShort: $value")
    }

    override fun setParameter(stmt: PreparedStatementApi, index: Int, value: Any?) {
        val v = if (value is UShort) value.toShort() else value
        super.setParameter(stmt, index, v)
    }

    override fun notNullValueToDB(value: Any): Any {
        val v = if (value is UShort) value.toShort() else value
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
 */
@ExperimentalUnsignedTypes
class UIntegerColumnType : ColumnType() {
    override fun sqlType(): String = currentDialect.dataTypeProvider.uintegerType()
    override fun valueFromDB(value: Any): UInt {
        return when (value) {
            is UInt -> value
            is Int -> value.takeIf { it >= 0 }?.toUInt()
            is Number -> value.toInt().takeIf { it >= 0 }?.toUInt()
            is String -> value.toUInt()
            else -> error("Unexpected value of type Int: $value of ${value::class.qualifiedName}")
        } ?: error("negative value but type is UInt: $value")
    }

    override fun setParameter(stmt: PreparedStatementApi, index: Int, value: Any?) {
        val v = if (value is UInt) value.toInt() else value
        super.setParameter(stmt, index, v)
    }

    override fun notNullValueToDB(value: Any): Any {
        val v = if (value is UInt) value.toInt() else value
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
@ExperimentalUnsignedTypes
class ULongColumnType : ColumnType() {
    override fun sqlType(): String = currentDialect.dataTypeProvider.ulongType()
    override fun valueFromDB(value: Any): ULong {
        return when (value) {
            is ULong -> value
            is Long -> value.takeIf { it >= 0 }?.toULong()
            is Number -> value.toLong().takeIf { it >= 0 }?.toULong()
            is String -> value.toULong()
            else -> error("Unexpected value of type Long: $value of ${value::class.qualifiedName}")
        } ?: error("negative value but type is ULong: $value")
    }

    override fun setParameter(stmt: PreparedStatementApi, index: Int, value: Any?) {
        val v = if (value is ULong) value.toLong() else value
        super.setParameter(stmt, index, v)
    }

    override fun notNullValueToDB(value: Any): Any {
        val v = if (value is ULong) value.toLong() else value
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
    override fun valueFromDB(value: Any): BigDecimal = when (value) {
        is BigDecimal -> value
        is Double -> value.toBigDecimal()
        is Float -> value.toBigDecimal()
        is Long -> value.toBigDecimal()
        is Int -> value.toBigDecimal()
        else -> error("Unexpected value of type Double: $value of ${value::class.qualifiedName}")
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


}

// Character columns

/**
 * Character column for storing single characters.
 */
class CharacterColumnType : ColumnType() {
    override fun sqlType(): String = "CHAR"
    override fun valueFromDB(value: Any): Char = when (value) {
        is Char -> value
        is Number -> value.toChar()
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
            append(" COLLATE ${escape(collate)}")
        }
    }

    override fun notNullValueToDB(value: Any): Any {
        require(value is String && value.codePointCount(0, value.length) <= colLength) {
            "Value '$value' can't be stored to database column because exceeds length ($colLength)"
        }
        return value
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
    override fun sqlType(): String = buildString {
        append("VARCHAR($colLength)")
        if (collate != null) {
            append(" COLLATE ${escape(collate)}")
        }
    }

    override fun notNullValueToDB(value: Any): Any {
        require(value is String && value.codePointCount(0, value.length) <= colLength) {
            "Value '$value' can't be stored to database column because exceeds length ($colLength)"
        }
        return value
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
 * [eagerLoading] means what content will be loaded immediately when data loaded from database.
 */
open class TextColumnType(collate: String? = null, val eagerLoading: Boolean = false) : StringColumnType(collate) {
    override fun sqlType(): String = buildString {
        append(currentDialect.dataTypeProvider.textType())
        if (collate != null) {
            append(" COLLATE ${escape(collate)}")
        }
    }

    override fun readObject(rs: ResultSet, index: Int): Any? {
        val value = super.readObject(rs, index)
        return if (eagerLoading && value != null)
            valueFromDB(value)
        else
            value
    }
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
class BinaryColumnType(
    /** Returns the length of the column- */
    val length: Int
) : BasicBinaryColumnType() {
    override fun sqlType(): String = currentDialect.dataTypeProvider.binaryType(length)

    override fun notNullValueToDB(value: Any): Any {
        require(value is ByteArray && value.size <= length) {
            "Value '$value' can't be stored to database column because exceeds length ($length)"
        }
        return value
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
class BlobColumnType : ColumnType() {
    override fun sqlType(): String = currentDialect.dataTypeProvider.blobType()

    override fun valueFromDB(value: Any): ExposedBlob = when (value) {
        is ExposedBlob -> value
        is Blob -> ExposedBlob(value.binaryStream.use { it.readBytes() })
        is InputStream -> ExposedBlob(value.use { it.readBytes() })
        is ByteArray -> ExposedBlob(value)
        else -> error("Unexpected value of type Blob: $value of ${value::class.qualifiedName}")
    }

    override fun notNullValueToDB(value: Any): Any {
        return if (currentDialect.dataTypeProvider.blobAsStream && value is Blob) {
            value.binaryStream
        } else {
            value
        }
    }

    override fun nonNullValueToString(value: Any): String = "?"

    override fun readObject(rs: ResultSet, index: Int): Any? {
        return if (currentDialect.dataTypeProvider.blobAsStream) {
            rs.getBytes(index)?.let(::ExposedBlob)
        } else {
            rs.getBlob(index)?.binaryStream?.use { ExposedBlob(it.readBytes()) }
        }
    }

    override fun setParameter(stmt: PreparedStatementApi, index: Int, value: Any?) {
        val toSetValue = (value as? ExposedBlob)?.bytes?.inputStream() ?: value
        when {
            currentDialect.dataTypeProvider.blobAsStream && toSetValue is InputStream -> stmt.setInputStream(index, toSetValue)
            toSetValue == null -> stmt.setInputStream(index, toSetValue)
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

    companion object {
        private val uuidRegexp = Regex("[0-9A-F]{8}-[0-9A-F]{4}-[0-9A-F]{4}-[0-9A-F]{4}-[0-9A-F]{12}", RegexOption.IGNORE_CASE)
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

    override fun nonNullValueToString(value: Any): String = currentDialect.dataTypeProvider.booleanToStatementString(value as Boolean)
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

    @Suppress("UNCHECKED_CAST")
    override fun valueFromDB(value: Any): T = when (value) {
        is Number -> klass.java.enumConstants!![value.toInt()]
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
    val klass: KClass<T>, colLength: Int
) : VarCharColumnType(colLength) {
    @Suppress("UNCHECKED_CAST")
    override fun valueFromDB(value: Any): T = when (value) {
        is String -> klass.java.enumConstants!!.first { it.name == value }
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

// Date/Time columns

/**
 * Marker interface for date/datetime related column types.
 **/
interface IDateColumnType
