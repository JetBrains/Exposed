package kotlin.sql

open class ColumnType(var nullable: Boolean = false) {
    public fun valueToString(value: Any?) : String {
        return if (value == null) {
            if (!nullable) throw RuntimeException("NULL in non-nullable column")
            "NULL"
        } else {
            nonNullValueToString (value!!)
        }
    }

    protected open fun nonNullValueToString(value: Any) : String {
        return "$value"
    }
}

class IntegerColumnType(var autoinc: Boolean = false): ColumnType()
class EnumerationColumnType<T:Enum<T>>(val klass: Class<T>): ColumnType() {
    protected override fun nonNullValueToString(value: Any): String {
        return when (value) {
            is Int -> "$value"
            is T -> "${value.ordinal()}"
            else -> throw RuntimeException("$value is not valid for enum ${javaClass<T>().getName()}")
        }
    }
}

class DateColumnType(): ColumnType() {
    protected override fun nonNullValueToString(value: Any): String {
        return "'$value'"
    }
}

class StringColumnType(val length: Int = 0): ColumnType() {
    protected override fun nonNullValueToString(value: Any): String {
        return "'$value'"
    }
}

