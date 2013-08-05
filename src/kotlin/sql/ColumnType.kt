package kotlin.sql

open class ColumnType(var nullable: Boolean = false)

class IntegerColumnType(var autoinc: Boolean = false): ColumnType()
class EnumerationColumnType<T:Enum<T>>(val klass: Class<T>): ColumnType()
class DateColumnType(): ColumnType()

class StringColumnType(val length: Int = 0): ColumnType()
