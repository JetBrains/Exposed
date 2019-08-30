package org.jetbrains.exposed.sql

import org.jetbrains.exposed.dao.EntityID
import org.jetbrains.exposed.dao.IdTable
import org.jetbrains.exposed.sql.statements.DefaultValueMarker
import org.jetbrains.exposed.sql.statements.api.ExposedBlob
import org.jetbrains.exposed.sql.statements.api.PreparedStatementApi
import org.jetbrains.exposed.sql.vendors.currentDialect
import java.io.InputStream
import java.math.BigDecimal
import java.math.RoundingMode
import java.nio.ByteBuffer
import java.sql.Blob
import java.sql.ResultSet
import java.util.*
import kotlin.reflect.KClass

interface IColumnType {
    var nullable: Boolean
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

    fun setParameter(stmt: PreparedStatementApi, index: Int, value: Any?) {
        stmt[index] = value
    }
}

abstract class ColumnType(override var nullable: Boolean = false) : IColumnType {
    override fun toString(): String = sqlType()
}

class AutoIncColumnType(val delegate: ColumnType, private val _autoincSeq: String) : IColumnType by delegate {

    val autoincSeq : String? get() = if (currentDialect.needsSequenceToAutoInc) _autoincSeq else null

    private fun resolveAutIncType(columnType: IColumnType) : String = when (columnType) {
        is EntityIDColumnType<*> -> resolveAutIncType(columnType.idColumn.columnType)
        is IntegerColumnType -> currentDialect.dataTypeProvider.integerAutoincType()
        is LongColumnType -> currentDialect.dataTypeProvider.longAutoincType()
        else -> error("Unsupported type $delegate for auto-increment")
    }

    override fun sqlType(): String = resolveAutIncType(delegate)
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

class ShortColumnType : ColumnType() {
    override fun sqlType(): String = "SMALLINT"

    override fun valueFromDB(value: Any): Any = when(value) {
        is Short -> value
        is Number -> value.toShort()
        else -> error("Unexpected value of type Int: $value of ${value::class.qualifiedName}")
    }
}

class IntegerColumnType : ColumnType() {
    override fun sqlType(): String = currentDialect.dataTypeProvider.integerType()

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
}

class EnumerationColumnType<T:Enum<T>>(val klass: KClass<T>): ColumnType() {
    override fun sqlType(): String  = currentDialect.dataTypeProvider.integerType()

    override fun notNullValueToDB(value: Any): Any = when(value) {
        is Int -> value
        is Enum<*> -> value.ordinal
        else -> error("$value of ${value::class.qualifiedName} is not valid for enum ${klass.simpleName}")
    }

    override fun valueFromDB(value: Any): Any = when (value) {
        is Number -> klass.java.enumConstants!![value.toInt()]
        is Enum<*> -> value
        else -> error("$value of ${value::class.qualifiedName} is not valid for enum ${klass.simpleName}")
    }
}

class EnumerationNameColumnType<T:Enum<T>>(val klass: KClass<T>, colLength: Int): VarCharColumnType(colLength) {
    override fun notNullValueToDB(value: Any): Any = when (value) {
        is String -> value
        is Enum<*> -> value.name
        else -> error("$value of ${value::class.qualifiedName} is not valid for enum ${klass.qualifiedName}")
    }

    override fun valueFromDB(value: Any): Any = when (value) {
        is String -> klass.java.enumConstants!!.first { it.name == value }
        is Enum<*> -> value
        else -> error("$value of ${value::class.qualifiedName} is not valid for enum ${klass.qualifiedName}")
    }
}

abstract class StringColumnType(val collate: String? = null) : ColumnType() {
    protected fun escape(value: String): String {
        return value.map { charactersToEscape[it] ?: it }.joinToString("")
    }

    override fun nonNullValueToString(value: Any): String = buildString {
        append('\'')
        append(escape(value.toString()))
        append('\'')
    }

    override fun valueFromDB(value: Any) = when(value) {
        is java.sql.Clob -> value.characterStream.readText()
        is ByteArray -> String(value)
        else -> value
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

open class VarCharColumnType(val colLength: Int = 255, collate: String? = null) : StringColumnType(collate)  {
    override fun sqlType(): String = buildString {
        append("VARCHAR($colLength)")

        if (collate != null) {
            append(" COLLATE ${escape(collate)}")
        }
    }
}

open class TextColumnType(collate: String? = null) : StringColumnType(collate) {
    override fun sqlType(): String = buildString {
        append(currentDialect.dataTypeProvider.textType())

        if (collate != null) {
            append(" COLLATE ${escape(collate)}")
        }
    }
}

class BinaryColumnType(val length: Int) : ColumnType() {
    override fun sqlType(): String  = currentDialect.dataTypeProvider.binaryType(length)

    override fun valueFromDB(value: Any): Any {
        if (value is Blob) {
            return value.binaryStream.readBytes()
        }
        return value
    }

    override fun nonNullValueToString(value: Any): String = when(value) {
        is ByteArray -> value.toString(Charsets.UTF_8)
        else -> "$value"
    }
}

class BlobColumnType : ColumnType() {
    override fun sqlType(): String  = currentDialect.dataTypeProvider.blobType()

    override fun nonNullValueToString(value: Any): String = "?"

    override fun readObject(rs: ResultSet, index: Int): Any? {
        return if (currentDialect.dataTypeProvider.blobAsStream)
            rs.getBytes(index)?.let { ExposedBlob(it) }
        else
            rs.getBlob(index)?.let { ExposedBlob(it.binaryStream.readBytes()) }
    }

    override fun valueFromDB(value: Any): Any = when (value) {
        is ExposedBlob -> value
        is Blob -> ExposedBlob(value.binaryStream.readBytes())
        is InputStream -> ExposedBlob(value.readBytes())
        is ByteArray -> ExposedBlob(value)
        else -> error("Unknown type for blob column :${value::class}")
    }

    override fun setParameter(stmt: PreparedStatementApi, index: Int, value: Any?) {
        val toSetValue = (value as? ExposedBlob)?.bytes?.inputStream() ?: value
        when {
            currentDialect.dataTypeProvider.blobAsStream && toSetValue is InputStream ->
                stmt.setInputStream(index, toSetValue)
            toSetValue == null -> stmt.setInputStream(index, toSetValue)
            else -> super.setParameter(stmt, index, toSetValue)
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

    private fun valueToUUID(value: Any): UUID = when (value) {
        is UUID -> value
        is String -> UUID.fromString(value)
        is ByteArray -> ByteBuffer.wrap(value).let { UUID(it.long, it.long) }
        else -> error("Unexpected value of type UUID: ${value.javaClass.canonicalName}")
    }

    override fun nonNullValueToString(value: Any) = "'${valueToUUID(value)}'"

    override fun valueFromDB(value: Any): Any = when {
        value is UUID -> value
        value is ByteArray -> ByteBuffer.wrap(value).let { b -> UUID(b.long, b.long) }
        value is String && value.matches(uuidRegexp) -> UUID.fromString(value)
        value is String -> ByteBuffer.wrap(value.toByteArray()).let { b -> UUID(b.long, b.long) }
        else -> error("Unexpected value of type UUID: $value of ${value::class.qualifiedName}")
    }

    companion object {
        private val uuidRegexp = "[0-9A-F]{8}-[0-9A-F]{4}-[1-5][0-9A-F]{3}-[89AB][0-9A-F]{3}-[0-9A-F]{12}".toRegex(RegexOption.IGNORE_CASE)
    }
}

/** Marker interface for date/datetime related column types **/
interface IDateColumnType