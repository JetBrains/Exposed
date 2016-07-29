package org.jetbrains.exposed.sql

import org.jetbrains.exposed.dao.EntityID
import org.jetbrains.exposed.dao.IdTable
import org.jetbrains.exposed.sql.vendors.PostgreSQLDialect
import org.jetbrains.exposed.sql.vendors.currentDialect
import org.joda.time.DateTime
import org.joda.time.DateTimeZone
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

            is List<*> -> {
                value.map {valueToString(it)}.joinToString(",")
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
    override fun sqlType(): String  = if (autoinc) currentDialect.shortAutoincType() else "INT"

    override fun valueFromDB(value: Any): Any {
        return when(value) {
            is Int -> value
            is Number -> value.toInt()
            else -> error("Unexpected value of type Int: $value")
        }
    }
}

class LongColumnType(autoinc: Boolean = false): ColumnType(autoinc = autoinc) {
    override fun sqlType(): String  = if (autoinc) currentDialect.longAutoincType() else "BIGINT"

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
    override fun valueFromDB(value: Any): Any = super.valueFromDB(value).let { (it as? BigDecimal)?.setScale(scale, RoundingMode.HALF_EVEN) ?: it }
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

class DateColumnType(val time: Boolean): ColumnType() {
    override fun sqlType(): String  = if (time) currentDialect.dateTimeType() else "DATE"

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
            return "'${zonedTime.toString("YYYY-MM-dd HH:mm:ss.SSS", Locale.ROOT)}'"
        } else {
            val date = Date (dateTime.millis)
            return "'${date.toString()}'"
        }
    }

    override fun valueFromDB(value: Any): Any {
        if (value is java.sql.Date) {
            return DateTime(value)
        }

        if (value is java.sql.Timestamp) {
            return DateTime(value.time)
        }

        return value
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
    override fun sqlType(): String  = currentDialect.binaryType(length)
}

class BlobColumnType(): ColumnType() {
    override fun sqlType(): String  = currentDialect.blobType()

    override fun nonNullValueToString(value: Any): String {
        return "?"
    }

    override fun readObject(rs: ResultSet, index: Int): Any? {
        if (currentDialect == PostgreSQLDialect)
            return SerialBlob(rs.getBytes(index))
        else
            return rs.getBlob(index)
    }

    override fun setParameter(stmt: PreparedStatement, index: Int, value: Any?) {
        if (currentDialect == PostgreSQLDialect && value is InputStream) {
            stmt.setBinaryStream(index, value, value.available())
        } else {
            super.setParameter(stmt, index, value)
        }
    }

    override fun notNullValueToDB(value: Any): Any {
        if (currentDialect == PostgreSQLDialect)
            return (value as Blob).binaryStream
        else
            return value
    }
}

class BooleanColumnType() : ColumnType() {
    override fun sqlType(): String  = "BOOLEAN"

    override fun nonNullValueToString(value: Any): String = (value as Boolean).toString()
}

class UUIDColumnType() : ColumnType(autoinc = false) {
    override fun sqlType(): String = currentDialect.uuidType()

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