package kotlin.sql

import org.joda.time.DateTime
import java.sql.PreparedStatement
import java.util.Locale
import java.sql.Date

open class ColumnType(var nullable: Boolean = false) {
    public fun valueToString(value: Any?) : String {
        return if (value == null) {
            if (!nullable) error("NULL in non-nullable column")
            "NULL"
        } else {
            nonNullValueToString (value)
        }
    }

    protected open fun nonNullValueToString(value: Any) : String {
        return "$value"
    }

    public open fun setParameter(stmt: PreparedStatement, index: Int, value: Any) {
        stmt.setObject(index, value)
    }
}

data class IntegerColumnType(var autoinc: Boolean = false): ColumnType()
data class LongColumnType(var autoinc: Boolean = false): ColumnType()
data class DecimalColumnType(val scale: Int, val precision: Int): ColumnType()
data class EnumerationColumnType<T:Enum<T>>(val klass: Class<T>): ColumnType() {
    protected override fun nonNullValueToString(value: Any): String {
        return when (value) {
            is Int -> "$value"
            is Enum<*> -> "${value.ordinal()}"
            else -> error("$value is not valid for enum ${klass.getName()}")
        }
    }
}

data class DateColumnType(val time: Boolean): ColumnType() {
    protected override fun nonNullValueToString(value: Any): String {
        val dateTime = value as DateTime
        if (time) {
            val zonedTime = dateTime.toDateTime(Database.timeZone)
            return "'${zonedTime.toString("YYYY-MM-dd HH:mm:ss.SSS", Locale.ROOT)}'"
        } else {
            val date = Date (dateTime.getMillis())
            return "'${date.toString()}'"
        }
    }

    override fun setParameter(stmt: PreparedStatement, index: Int, value: Any) {
        val millis = (value as DateTime).getMillis()
        if (time) {
            stmt.setTimestamp(index, java.sql.Timestamp(millis))
        }
        else {
            stmt.setDate(index, java.sql.Date(millis))
        }
    }
}

data class StringColumnType(val length: Int = 0, val collate: String? = null): ColumnType() {
    val charactersToEscape = hashMapOf(
            '\'' to "\'\'",
//            '\"' to "\"\"", // no need to escape double quote as we put string in single quotes
            '\r' to "\\r",
            '\n' to "\\n")

    protected override fun nonNullValueToString(value: Any): String {
        val beforeEscaping = value.toString()
        val sb = StringBuilder(beforeEscaping.length+2)
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
}

data class BlobColumnType(): ColumnType() {
    override fun nonNullValueToString(value: Any): String {
        return "?"
    }
}

data class BooleanColumnType() : ColumnType() {
    override fun nonNullValueToString(value: Any): String {
        return if (value as Boolean) "1" else "0"
    }
}
