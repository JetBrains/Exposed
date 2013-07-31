package kotlin.sql

open class ColumnType(var nullable: Boolean = false)

open class IntegerColumnType(var autoinc: Boolean = false): ColumnType()

class StringColumnType(val length: Int = 0): ColumnType()
