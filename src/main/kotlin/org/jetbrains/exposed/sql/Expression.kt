package org.jetbrains.exposed.sql

import java.util.*

class QueryBuilder(val prepared: Boolean) {
    val args = ArrayList<Pair<IColumnType, Any?>>()

    fun <T> registerArgument(sqlType: IColumnType, argument: T) = registerArguments(sqlType, listOf(argument)).single()

    fun <T> registerArguments(sqlType: IColumnType, arguments: Iterable<T>): List<String> {
        val argumentsAndStrings = arguments.map { it to sqlType.valueToString(it) }.sortedBy { it.second }

        return argumentsAndStrings.map {
            if (prepared) {
                args.add(sqlType to it.first)
                "?"
            } else {
                it.second
            }
        }
    }
}

abstract class Expression<out T> {
    private val _hashCode by lazy {
        toString().hashCode()
    }

    abstract fun toSQL(queryBuilder: QueryBuilder): String

    override fun equals(other: Any?): Boolean {
        return (other as? Expression<*>)?.toString() == toString()
    }

    override fun hashCode(): Int {
        return _hashCode
    }

    override fun toString(): String {
        return toSQL(QueryBuilder(false))
    }

    companion object {
        inline fun <T> build(builder: SqlExpressionBuilder.()->Expression<T>): Expression<T> {
            return SqlExpressionBuilder.builder()
        }
    }
}

abstract class ExpressionWithColumnType<out T> : Expression<T>() {
    // used for operations with literals
    abstract val columnType: IColumnType
}
