package kotlin.sql

open class ColumnType(var nullable: Boolean = false)

class IntegerColumnType(var autoinc: Boolean = false): ColumnType()
class DateColumnType(): ColumnType()

class StringColumnType(val length: Int = 0): ColumnType()
