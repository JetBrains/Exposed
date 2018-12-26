@file:Suppress("EqualsOrHashCode")

package org.jetbrains.exposed.sql

import org.jetbrains.exposed.dao.EntityID
import org.jetbrains.exposed.dao.IdTable
import org.jetbrains.exposed.sql.statements.DefaultValueMarker
import org.jetbrains.exposed.sql.vendors.SQLiteDialect
import org.jetbrains.exposed.sql.vendors.currentDialect
import org.joda.time.DateTime
import org.joda.time.DateTimeZone
import org.joda.time.format.DateTimeFormat
import org.joda.time.format.ISODateTimeFormat
import java.io.InputStream
import java.math.BigDecimal
import java.math.RoundingMode
import java.nio.ByteBuffer
import java.sql.Blob
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.Types
import java.util.*
import javax.sql.rowset.serial.SerialBlob

interface IColumnType {
    val nullable: Boolean
    fun sqlType(): String

    fun valueFromDB(value: Any): Any  = value

    fun valueToString(value: Any?) : String = when (value) {
        null -> {
            if (!nullable) error("NULL in non-nullable column")
            "NULL"
        }

        DefaultValueMarker -> "DEFAULT"

        is Iterable<*> -> {
            value.joinToString(","){ valueToString(it) }
        }

        else ->  {
            nonNullValueToString (value)
        }
    }

    fun valueToDB(value: Any?): Any? = value?.let { notNullValueToDB(it) }

    fun notNullValueToDB(value: Any): Any  = value

    fun nonNullValueToString(value: Any) : String = notNullValueToDB(value).toString()

    fun readObject(rs: ResultSet, index: Int): Any? = rs.getObject(index)

    fun setParameter(stmt: PreparedStatement, index: Int, value: Any?) {
        stmt.setObject(index, value)
    }
}

abstract class ColumnType(_nullable:Boolean = false) : IColumnType {
    override var nullable: Boolean = _nullable
        internal set
    override fun toString(): String = sqlType()

    /**
     * Function used in equals check
     * `other` is always the exact class type as current ColumnType instance at the moment of call.
     */
    protected open fun additionalEqualsCheck(other: ColumnType) = true

    /**
     * Result of that call will be stored as hashCode value
     */
    protected open fun calcHashCode() : Int =  41 * this::class.qualifiedName.hashCode() + nullable.hashCode()
    private var _hashCode: Int? = null

    override fun equals(other: Any?): Boolean = when {
        this === other -> true
        other !is ColumnType -> false
        other::class.qualifiedName != this::class.qualifiedName -> false
        other.nullable != nullable -> false
        !additionalEqualsCheck(other) -> false
        else -> true
    }

    override fun hashCode() : Int {
        return _hashCode ?: calcHashCode().apply { _hashCode = this }
    }
}

class AutoIncColumnType(val delegate: ColumnType, private val _autoincSeq: String) : IColumnType by delegate {

    val autoincSeq : String? get() = if (currentDialect.needsSequenceToAutoInc) _autoincSeq else null

    private fun resolveAutIncType(columnType: IColumnType) : String = when (columnType) {
        is EntityIDColumnType<*> -> resolveAutIncType(columnType.idColumn.columnType)
        is IntegerColumnType -> currentDialect.dataTypeProvider.shortAutoincType()
        is LongColumnType -> currentDialect.dataTypeProvider.longAutoincType()
        else -> error("Unsupported type $delegate for auto-increment")
    }

    override fun sqlType(): String = resolveAutIncType(delegate)

    override fun equals(other: Any?) = when {
        this === other -> true
        other !is AutoIncColumnType -> false
        other._autoincSeq != _autoincSeq -> false
        other.delegate != delegate -> false
        else -> true
    }

    override fun hashCode() = delegate.hashCode() * 31 + _autoincSeq.hashCode()
}

val IColumnType.isAutoInc: Boolean get() = this is AutoIncColumnType || (this is EntityIDColumnType<*> && idColumn.columnType.isAutoInc)
val Column<*>.autoIncSeqName : String? get() {
        return (columnType as? AutoIncColumnType)?.autoincSeq
            ?: (columnType as? EntityIDColumnType<*>)?.idColumn?.autoIncSeqName
}

class EntityIDColumnType<T:Comparable<T>>(val idColumn: Column<T>) : ColumnType(false) {

    init {
        assert(idColumn.table is IdTable<*>){"EntityId supported only for IdTables"}
    }

    override fun sqlType(): String = idColumn.columnType.sqlType()

    override fun notNullValueToDB(value: Any): Any =
        idColumn.columnType.notNullValueToDB(when (value) {
            is EntityID<*> -> value.value
            else -> value
        })

    override fun nonNullValueToString(value: Any): String =  when (value) {
        is EntityID<*> -> idColumn.columnType.nonNullValueToString(value.value)
        else -> idColumn.columnType.nonNullValueToString(value)
    }

    @Suppress("UNCHECKED_CAST")
    override fun valueFromDB(value: Any): Any = when (value) {
        is EntityID<*> -> EntityID(value.value as T, idColumn.table as IdTable<T>)
        else -> EntityID(idColumn.columnType.valueFromDB(value) as T, idColumn.table as IdTable<T>)
    }

    override fun additionalEqualsCheck(other: ColumnType) = idColumn == (other as EntityIDColumnType<*>).idColumn
    override fun calcHashCode(): Int = super.calcHashCode() * 41 + idColumn.hashCode()
}

class CharacterColumnType : ColumnType() {
    override fun sqlType(): String  = "CHAR"

    override fun valueFromDB(value: Any): Any = when(value) {
        is Char -> value
        is Number -> value.toInt().toChar()
        is String -> value.single()
        else -> error("Unexpected value of type Char: $value of ${value::class.qualifiedName}")
    }

    override fun notNullValueToDB(value: Any): Any = valueFromDB(value).toString()

    override fun nonNullValueToString(value: Any): String = "'$value'"
}

class IntegerColumnType : ColumnType() {
    override fun sqlType(): String = currentDialect.dataTypeProvider.shortType()

    override fun valueFromDB(value: Any): Any = when(value) {
        is Int -> value
        is Number -> value.toInt()
        else -> error("Unexpected value of type Int: $value of ${value::class.qualifiedName}")
    }
}

class LongColumnType : ColumnType() {
    override fun sqlType(): String = currentDialect.dataTypeProvider.longType()

    override fun valueFromDB(value: Any): Any = when(value) {
        is Long -> value
        is Number -> value.toLong()
        else -> error("Unexpected value of type Long: $value of ${value::class.qualifiedName}")
    }
}

class FloatColumnType: ColumnType() {
    override fun sqlType(): String  = currentDialect.dataTypeProvider.floatType()

    override fun valueFromDB(value: Any): Any {
        val valueFromDB = super.valueFromDB(value)
        return when (valueFromDB) {
            is Number -> valueFromDB.toFloat()
            else -> valueFromDB
        }
    }
}

class DoubleColumnType: ColumnType() {
    override fun sqlType(): String  = currentDialect.dataTypeProvider.doubleType()

    override fun valueFromDB(value: Any): Any {
        val valueFromDB = super.valueFromDB(value)
        return when (valueFromDB) {
            is Number -> valueFromDB.toDouble()
            else -> valueFromDB
        }
    }
}


class DecimalColumnType(val precision: Int, val scale: Int): ColumnType() {
    override fun sqlType(): String  = "DECIMAL($precision, $scale)"
    override fun valueFromDB(value: Any): Any {
        val valueFromDB = super.valueFromDB(value)
        return when (valueFromDB) {
            is BigDecimal -> valueFromDB.setScale(scale, RoundingMode.HALF_EVEN)
            is Double -> BigDecimal.valueOf(valueFromDB).setScale(scale, RoundingMode.HALF_EVEN)
            is Float -> BigDecimal(java.lang.Float.toString(valueFromDB)).setScale(scale, RoundingMode.HALF_EVEN)
            is Int -> BigDecimal(valueFromDB)
            is Long -> BigDecimal.valueOf(valueFromDB)
            else -> valueFromDB
        }
    }

    override fun additionalEqualsCheck(other: ColumnType): Boolean {
        require(other is DecimalColumnType)
        return precision == other.precision && scale == other.scale
    }
    override fun calcHashCode(): Int = super.calcHashCode() * 41 + precision.hashCode() * 41 + scale.hashCode()

}

class EnumerationColumnType<T:Enum<T>>(val klass: Class<T>): ColumnType() {
    override fun sqlType(): String  = currentDialect.dataTypeProvider.shortType()

    override fun notNullValueToDB(value: Any): Any = when(value) {
        is Int -> value
        is Enum<*> -> value.ordinal
        else -> error("$value of ${value::class.qualifiedName} is not valid for enum ${klass.name}")
    }

    override fun valueFromDB(value: Any): Any = when (value) {
        is Number -> klass.enumConstants!![value.toInt()]
        is Enum<*> -> value
        else -> error("$value of ${value::class.qualifiedName} is not valid for enum ${klass.name}")
    }

    @Suppress("UNCHECKED_CAST")
    override fun additionalEqualsCheck(other: ColumnType) = klass == (other as EnumerationColumnType<T>).klass
    override fun calcHashCode(): Int = super.calcHashCode() * 41 + klass.hashCode()
}

class EnumerationNameColumnType<T:Enum<T>>(val klass: Class<T>, colLength: Int): VarCharColumnType(colLength) {
    override fun notNullValueToDB(value: Any): Any = when (value) {
        is String -> value
        is Enum<*> -> value.name
        else -> error("$value of ${value::class.qualifiedName} is not valid for enum ${klass.name}")
    }

    override fun valueFromDB(value: Any): Any = when (value) {
        is String ->  klass.enumConstants!!.first { it.name == value }
        is Enum<*> -> value
        else -> error("$value of ${value::class.qualifiedName} is not valid for enum ${klass.name}")
    }

    @Suppress("UNCHECKED_CAST")
    override fun additionalEqualsCheck(other: ColumnType) =
        super.additionalEqualsCheck(other) && klass.name == (other as EnumerationNameColumnType<*>).klass.name

    override fun calcHashCode(): Int = super.calcHashCode() * 41 + klass.hashCode()
}

private val DEFAULT_DATE_STRING_FORMATTER = DateTimeFormat.forPattern("YYYY-MM-dd").withLocale(Locale.ROOT)
private val DEFAULT_DATE_TIME_STRING_FORMATTER = DateTimeFormat.forPattern("YYYY-MM-dd HH:mm:ss.SSSSSS").withLocale(Locale.ROOT)
private val SQLITE_DATE_TIME_STRING_FORMATTER = DateTimeFormat.forPattern("YYYY-MM-dd HH:mm:ss")
private val SQLITE_DATE_STRING_FORMATTER = ISODateTimeFormat.yearMonthDay()

class DateColumnType(val time: Boolean): ColumnType() {
    override fun sqlType(): String  = if (time) currentDialect.dataTypeProvider.dateTimeType() else "DATE"

    override fun nonNullValueToString(value: Any): String {
        if (value is String) return value

        val dateTime = when (value) {
            is DateTime -> value
            is java.sql.Date -> DateTime(value.time)
            is java.sql.Timestamp -> DateTime(value.time)
            else -> error("Unexpected value: $value of ${value::class.qualifiedName}")
        }

        return if (time)
            "'${DEFAULT_DATE_TIME_STRING_FORMATTER.print(dateTime.toDateTime(DateTimeZone.getDefault()))}'"
        else
            "'${DEFAULT_DATE_STRING_FORMATTER.print(dateTime)}'"
    }

    override fun valueFromDB(value: Any): Any = when(value) {
        is DateTime -> value
        is java.sql.Date ->  DateTime(value.time)
        is java.sql.Timestamp -> DateTime(value.time)
        is Int -> DateTime(value.toLong())
        is Long -> DateTime(value)
        is String -> when {
            currentDialect is SQLiteDialect && time -> SQLITE_DATE_TIME_STRING_FORMATTER.parseDateTime(value)
            currentDialect is SQLiteDialect -> SQLITE_DATE_STRING_FORMATTER.parseDateTime(value)
            else -> value
        }
        // REVIEW
        else -> DEFAULT_DATE_TIME_STRING_FORMATTER.parseDateTime(value.toString())
    }

    override fun notNullValueToDB(value: Any): Any {
        if (value is DateTime) {
            val millis = value.millis
            if (time) {
                return java.sql.Timestamp(millis)
            } else {
                return java.sql.Date(millis)
            }
        }
        return value
    }

    override fun additionalEqualsCheck(other: ColumnType) = time == (other as DateColumnType).time

    override fun calcHashCode(): Int = super.calcHashCode() * 41 + time.hashCode()
}

abstract class StringColumnType(val collate: String? = null) : ColumnType() {
    private val charactersToEscape = mapOf(
            '\'' to "\'\'",
//            '\"' to "\"\"", // no need to escape double quote as we put string in single quotes
            '\r' to "\\r",
            '\n' to "\\n")

    override fun nonNullValueToString(value: Any): String = buildString {
        append('\'')
        value.toString().forEach {
            append(charactersToEscape[it] ?: it)
        }
        append('\'')
    }

    override fun valueFromDB(value: Any) = when(value) {
        is java.sql.Clob -> value.characterStream.readText()
        is ByteArray -> String(value)
        else -> value
    }

    override fun additionalEqualsCheck(other: ColumnType) = collate == (other as StringColumnType).collate
    override fun calcHashCode(): Int = super.calcHashCode() * 41 + (collate?.hashCode() ?: 0)
}

open class VarCharColumnType(val colLength: Int = 255, collate: String? = null) : StringColumnType(collate) {
    override fun sqlType(): String = buildString {
        append("VARCHAR($colLength)")

        if (collate != null) {
            append(" COLLATE $collate")
        }
    }

    override fun additionalEqualsCheck(other: ColumnType) =
        super.additionalEqualsCheck(other) && colLength == (other as VarCharColumnType).colLength

    override fun calcHashCode(): Int = super.calcHashCode() * 41 + colLength.hashCode()
}

open class TextColumnType(collate: String? = null) : StringColumnType(collate) {
    override fun sqlType(): String = buildString {
        append(currentDialect.dataTypeProvider.textType())

        if (collate != null) {
            append(" COLLATE $collate")
        }
    }
}

class BinaryColumnType(val length: Int) : ColumnType() {
    override fun sqlType(): String  = currentDialect.dataTypeProvider.binaryType(length)

    // REVIEW
    override fun valueFromDB(value: Any): Any {
        if (value is java.sql.Blob) {
            return value.binaryStream.readBytes()
        }
        return value
    }

    override fun additionalEqualsCheck(other: ColumnType) = length == (other as BinaryColumnType).length
    override fun calcHashCode(): Int = super.calcHashCode() * 41 + length.hashCode()
}

class BlobColumnType : ColumnType() {
    override fun sqlType(): String  = currentDialect.dataTypeProvider.blobType()

    override fun nonNullValueToString(value: Any): String = "?"

    override fun readObject(rs: ResultSet, index: Int): Any? {
        return if (currentDialect.dataTypeProvider.blobAsStream)
            rs.getBytes(index)?.let { SerialBlob(it) }         
        else
            rs.getBlob(index)
    }

    override fun valueFromDB(value: Any): Any = when (value) {
        is Blob -> value
        is InputStream -> SerialBlob(value.readBytes())
        is ByteArray -> SerialBlob(value)
        else -> error("Unknown type for blob column :${value.javaClass}")
    }

    override fun setParameter(stmt: PreparedStatement, index: Int, value: Any?) {
        when {
            currentDialect.dataTypeProvider.blobAsStream && value is InputStream ->
                stmt.setBinaryStream(index, value, value.available())
            value == null -> stmt.setNull(index, Types.LONGVARBINARY)
            else -> super.setParameter(stmt, index, value)
        }
    }

    override fun notNullValueToDB(value: Any): Any {
        return if (currentDialect.dataTypeProvider.blobAsStream)
            (value as? Blob)?.binaryStream ?: value
        else
            value
    }
}

class BooleanColumnType : ColumnType() {
    override fun sqlType(): String  = currentDialect.dataTypeProvider.booleanType()

    override fun valueFromDB(value: Any) = when (value) {
        is Number -> value.toLong() != 0L
        is String -> currentDialect.dataTypeProvider.booleanFromStringToBoolean(value)
        else -> value.toString().toBoolean()
    }

    override fun nonNullValueToString(value: Any) = currentDialect.dataTypeProvider.booleanToStatementString(value as Boolean)
}

class UUIDColumnType : ColumnType() {
    override fun sqlType(): String = currentDialect.dataTypeProvider.uuidType()

    override fun notNullValueToDB(value: Any): Any = currentDialect.dataTypeProvider.uuidToDB(valueToUUID(value))

    private fun valueToUUID(value: Any): UUID {
        return when (value) {
            is UUID -> value
            is String -> UUID.fromString(value)
            is ByteArray -> ByteBuffer.wrap(value).let { UUID(it.long, it.long) }
            else -> error("Unexpected value of type UUID: ${value.javaClass.canonicalName}")
        }
    }

    override fun nonNullValueToString(value: Any) = "'${valueToUUID(value)}'"

    override fun valueFromDB(value: Any): Any = when(value) {
        is UUID -> value
        is ByteArray -> ByteBuffer.wrap(value).let { b -> UUID(b.long, b.long) }
        is String -> ByteBuffer.wrap(value.toByteArray()).let { b -> UUID(b.long, b.long) }
        else -> error("Unexpected value of type UUID: $value of ${value::class.qualifiedName}")
    }
}
