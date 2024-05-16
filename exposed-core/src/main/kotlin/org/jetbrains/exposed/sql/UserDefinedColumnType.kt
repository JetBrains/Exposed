package org.jetbrains.exposed.sql

import oracle.jdbc.OracleConnection
import oracle.jdbc.internal.OracleArray
import org.jetbrains.exposed.sql.statements.api.PreparedStatementApi
import org.postgresql.util.PGobject
import kotlin.reflect.KClass

abstract class UserDefinedColumnType<T>(val name: String, nullable: Boolean = false) : ColumnType<T>(nullable), DdlAware

inline fun <reified T : Enum<T>> pgEnumerationType(name: String) = PGEnumColumnType(name, T::class)

class PGEnumColumnType<T : Enum<T>>(name: String, private val klass: KClass<T>) : UserDefinedColumnType<T>(name) {
    class PGEnum<T : Enum<T>>(enumTypeName: String, enumValue: T?) : PGobject() {
        init {
            value = enumValue?.name
            type = enumTypeName
        }
    }

    private val values = klass.java.enumConstants.map { it.name }
    override fun sqlType(): String = name

    override fun valueFromDB(value: Any): T? {
        return when (value) {
            is String -> klass.java.enumConstants.find { it.name == value }
            else -> error("TODO error 1849967")
        }
    }

    override fun valueToDB(value: T?): Any? {
        return value?.let { PGEnum(sqlType(), value) }
    }

    override fun nonNullValueToString(value: T): String {
        return "'$value'::${sqlType()}"
    }

    override fun createStatement(): List<String> {
        return listOf("CREATE TYPE $name AS ENUM (${values.joinToString(", ") { "'$it'" }})")
    }

    override fun modifyStatement(): List<String> {
        TODO("Not yet implemented")
    }

    override fun dropStatement(): List<String> {
        return listOf("DROP TYPE $name")
    }
}

class PGRangeColumnType<T>(name: String, val delegate: ColumnType<T>, val subtypeDiff: String? = null) : UserDefinedColumnType<Pair<T, T>>(name) {

    class PGRange<T>(name: String, value: Pair<T, T>) : PGobject() {
        init {
            this.value = "[${value.first}, ${value.second}]"
            type = name
        }
    }

    override fun sqlType() = name

    override fun valueFromDB(value: Any): Pair<T, T>? {
        return when (value) {
            is PGobject -> {
                val valueText = value.value ?: return null
                val parts = valueText.slice(1..<valueText.length - 1).split(",")
                if (parts.size != 2) error("Range value can not be parsed. Wrong amount of parts.")
                parts.map { delegate.valueFromDB(it) }.let { it[0] as T to it[1] as T }
            }

            else -> error("Range value can not be parsed. Value is not String.")
        }
    }

    override fun valueToDB(value: Pair<T, T>?): Any? {
        if (value == null) return null
        return PGRange(sqlType(), value)
    }

    override fun nonNullValueToString(value: Pair<T, T>): String {
        return "'[${value.first}, ${value.second}]'::${sqlType()}"
    }

    override fun createStatement(): List<String> {
        val args = listOfNotNull(
            "subtype = ${delegate.sqlType()}",
            subtypeDiff?.let { "subtype_diff = $it" }
        ).joinToString(", ")
        return listOf("CREATE TYPE $name AS RANGE ($args)")
    }

    override fun modifyStatement(): List<String> {
        TODO("Not yet implemented")
    }

    override fun dropStatement(): List<String> {
        return listOf("DROP TYPE $name")
    }
}

class OracleArrayType<T>(name: String, val length: Int, val delegate: ColumnType<T>) : UserDefinedColumnType<List<T>>(name) {
    override fun sqlType() = name

    override fun valueFromDB(value: Any): List<T> = when (value) {
        is OracleArray -> (value.array as Array<Any>).map { delegate.valueFromDB(it) as T }
        else -> value as? List<T> ?: error("Unexpected value $value of type ${value::class.qualifiedName}")
    }

    override fun notNullValueToDB(value: List<T>): Any = value.map { e -> e?.let { delegate.notNullValueToDB(it) } }.toTypedArray()

    override fun setParameter(stmt: PreparedStatementApi, index: Int, value: Any?) {
        stmt[index] = stmt.statement.connection.unwrap(OracleConnection::class.java)
            .createOracleArray(sqlType(), value)
    }

    override fun nonNullValueToString(value: List<T>): String {
        return value.joinToString(separator = ", ", prefix = "(", postfix = ")") { delegate.valueToString(it) }
    }

    override fun createStatement(): List<String> {
        return listOf("CREATE TYPE ${sqlType()} AS VARRAY($length) OF ${delegate.sqlType()} ;")
    }

    override fun modifyStatement(): List<String> {
        error("Not supported. ")
    }

    override fun dropStatement(): List<String> {
        return listOf("DROP TYPE ${sqlType()}")
    }
}
