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
import java.sql.Date
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.util.*
import javax.sql.rowset.serial.SerialBlob

abstract class ColumnType(var nullable: Boolean = false, var autoinc: Boolean = false) {
    abstract fun sqlType(): String

    open fun valueFromDB(value: Any): Any  = value

    fun valueToString(value: Any?) : String {
        return when (value) {
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
    }

    fun valueToDB(value: Any?): Any? = if (value != null) notNullValueToDB(value) else null

    open fun notNullValueToDB(value: Any): Any  = value

    protected open fun nonNullValueToString(value: Any) : String {
        return notNullValueToDB(value).toString()
    }

    open fun readObject(rs: ResultSet, index: Int) = rs.getObject(index)

    open fun setParameter(stmt: PreparedStatement, index: Int, value: Any?) {
        stmt.setObject(index, value)
    }

    override fun toString(): String {
        return sqlType()
    }
}

class EntityIDColumnType<T:Any>(val idColumn: Column<T>, autoinc: Boolean = idColumn.columnType.autoinc): ColumnType(autoinc = autoinc) {

    init {
        assert(idColumn.table is IdTable<*>){"EntityId supported only for IdTables"}
    }

    override fun sqlType(): String = idColumn.columnType.sqlType()

    override fun notNullValueToDB(value: Any): Any {
        return idColumn.columnType.notNullValueToDB(when (value) {
            is EntityID<*> -> value.value
            else -> value
        })
    }

    override fun valueFromDB(value: Any): Any {
        @Suppress("UNCHECKED_CAST")
        return when (value) {
            is EntityID<*> -> EntityID(value.value as T, idColumn.table as IdTable<T>)
            else -> EntityID(idColumn.columnType.valueFromDB(value) as T, idColumn.table as IdTable<T>)
        }
    }
}

class CharacterColumnType() : ColumnType() {
    override fun sqlType(): String  = "CHAR"

    override fun valueFromDB(value: Any): Any {
        return when(value) {
            is Char -> value
            is Number -> value.toChar()
            else -> error("Unexpected value of type Char: $value")
        }
    }
}

class IntegerColumnType(autoinc: Boolean = false): ColumnType(autoinc = autoinc) {
    override fun sqlType(): String  = if (autoinc) currentDialect.dataTypeProvider.shortAutoincType() else "INT"

    override fun valueFromDB(value: Any): Any {
        return when(value) {
            is Int -> value
            is Number -> value.toInt()
            else -> error("Unexpected value of type Int: $value")
        }
    }
}

class LongColumnType(autoinc: Boolean = false): ColumnType(autoinc = autoinc) {
    override fun sqlType(): String  = if (autoinc) currentDialect.dataTypeProvider.longAutoincType() else "BIGINT"

    override fun valueFromDB(value: Any): Any {
        return when(value) {
            is Long -> value
            is Number -> value.toLong()
            else -> error("Unexpected value of type Long: $value")
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
            is Int -> BigDecimal(valueFromDB)
            is Long -> BigDecimal.valueOf(valueFromDB)
            else -> valueFromDB
        }
    }
}

class EnumerationColumnType<T:Enum<T>>(val klass: Class<T>): ColumnType() {
    override fun sqlType(): String  = "INT"

    override fun notNullValueToDB(value: Any): Any {
        return when (value) {
            is Int -> value
            is Enum<*> -> value.ordinal
            else -> error("$value is not valid for enum ${klass.name}")
        }
    }

    @Suppress("UNCHECKED_CAST")
    override fun valueFromDB(value: Any): Any {
        if (value is Enum<*>)
            return value as Enum<T>
        return klass.enumConstants!![value as Int]
    }
}

private val DEFAULT_DATE_TIME_STRING_FORMATTER = DateTimeFormat.forPattern("YYYY-MM-dd HH:mm:ss.SSS").withLocale(Locale.ROOT)
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
            else -> error("Unexpected value: $value")
        }

        if (time) {
            val zonedTime = dateTime.toDateTime(DateTimeZone.UTC)
            return "'${DEFAULT_DATE_TIME_STRING_FORMATTER.print(zonedTime)}'"
        } else {
            val date = Date (dateTime.millis)
            return "'$date'"
        }
    }

    override fun valueFromDB(value: Any): Any = when(value) {
        is java.sql.Date ->  DateTime(value.time)
        is java.sql.Timestamp -> DateTime(value.time)
        is Long -> DateTime(value)
        is String -> when {
            currentDialect == SQLiteDialect && time -> SQLITE_DATE_TIME_STRING_FORMATTER.parseDateTime(value)
            currentDialect == SQLiteDialect -> SQLITE_DATE_STRING_FORMATTER.parseDateTime(value)
            else -> value
        }
        else -> value
    }

    override fun notNullValueToDB(value: Any): Any {
        if (value is DateTime) {
            val millis = value.millis
            if (time) {
                return java.sql.Timestamp(millis)
            }
            else {
                return java.sql.Date(millis)
            }
        }
        return value
    }
}

class StringColumnType(val length: Int = 65535, val collate: String? = null): ColumnType() {
    override fun sqlType(): String  {
        val ddl = StringBuilder()

        ddl.append(when (length) {
            in 1..255 -> "VARCHAR($length)"
            else -> "TEXT"
        })

        if (collate != null) {
            ddl.append(" COLLATE $collate")
        }

        return ddl.toString()
    }

    val charactersToEscape = hashMapOf(
            '\'' to "\'\'",
//            '\"' to "\"\"", // no need to escape double quote as we put string in single quotes
            '\r' to "\\r",
            '\n' to "\\n")

    override fun nonNullValueToString(value: Any): String {
        val beforeEscaping = value.toString()
        val sb = StringBuilder(beforeEscaping.length +2)
        sb.append('\'')
        for (c in beforeEscaping) {
            if (charactersToEscape.containsKey(c))
                sb.append(charactersToEscape[c])
            else
                sb.append(c)
        }
        sb.append('\'')
        return sb.toString()
    }

    override fun valueFromDB(value: Any): Any {
        if (value is java.sql.Clob) {
            return value.characterStream.readText()
        }
        return value
    }
}

class BinaryColumnType(val length: Int) : ColumnType() {
    override fun sqlType(): String  = currentDialect.dataTypeProvider.binaryType(length)
}

class BlobColumnType(): ColumnType() {
    override fun sqlType(): String  = currentDialect.dataTypeProvider.blobType()

    override fun nonNullValueToString(value: Any): String {
        return "?"
    }

    override fun readObject(rs: ResultSet, index: Int): Any? {
        if (currentDialect.dataTypeProvider.blobAsStream)
            return SerialBlob(rs.getBytes(index))
        else
            return rs.getBlob(index)
    }

    override fun setParameter(stmt: PreparedStatement, index: Int, value: Any?) {
        if (currentDialect.dataTypeProvider.blobAsStream && value is InputStream) {
            stmt.setBinaryStream(index, value, value.available())
        } else {
            super.setParameter(stmt, index, value)
        }
    }

    override fun notNullValueToDB(value: Any): Any {
        if (currentDialect.dataTypeProvider.blobAsStream)
            return (value as Blob).binaryStream
        else
            return value
    }
}

class BooleanColumnType() : ColumnType() {
    override fun sqlType(): String  = "BOOLEAN"

    override fun valueFromDB(value: Any) = when (value) {
        is Number -> value.toLong() != 0L
        is String -> value.toBoolean()
        else -> value.toString().toBoolean()
    }

    override fun nonNullValueToString(value: Any) = currentDialect.dataTypeProvider.booleanToStatementString(value as Boolean)
}

class UUIDColumnType() : ColumnType(autoinc = false) {
    override fun sqlType(): String = currentDialect.dataTypeProvider.uuidType()

    override fun notNullValueToDB(value: Any): Any = when (value) {
        is UUID -> ByteBuffer.allocate(16).putLong(value.mostSignificantBits).putLong(value.leastSignificantBits).array()
        is String -> value.toByteArray()
        is ByteArray -> value
        else -> error("Unexpected value of type UUID: ${value.javaClass.canonicalName}")
    }

    override fun valueFromDB(value: Any): Any = when(value) {
        is UUID -> value
        is ByteArray -> ByteBuffer.wrap(value).let { b -> UUID(b.long, b.long) }
        is String -> ByteBuffer.wrap(value.toByteArray()).let { b -> UUID(b.long, b.long) }
        else -> error("Unexpected value of type UUID: $value")
    }

}