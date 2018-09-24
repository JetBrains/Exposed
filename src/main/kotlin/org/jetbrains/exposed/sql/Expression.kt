package org.jetbrains.exposed.sql

import org.jetbrains.exposed.sql.statements.DefaultValueMarker
import java.util.*

class QueryBuilder(val prepared: Boolean) {
    val args = ArrayList<Pair<IColumnType, Any?>>()

    fun <T> registerArgument(column: Column<*>, argument: T) : String = when (argument) {
        is Expression<*> -> argument.toSQL(this)
        DefaultValueMarker -> column.dbDefaultValue!!.toSQL(this)
        else -> registerArgument(column.columnType, argument)
    }
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

abstract class Expression<T> {
    private val _hashCode by lazy {
        toString().hashCode()
    }

    abstract fun toSQL(queryBuilder: QueryBuilder): String

    override fun equals(other: Any?): Boolean = (other as? Expression<*>)?.toString() == toString()

    override fun hashCode(): Int = _hashCode

    override fun toString(): String = toSQL(QueryBuilder(false))

    companion object {
        inline fun <T, E: Expression<T>> build(builder: SqlExpressionBuilder.()->E): E =
                SqlExpressionBuilder.builder()
    }
}

abstract class ExpressionWithColumnType<T> : Expression<T>() {
    // used for operations with literals
    abstract val columnType: IColumnType
}
