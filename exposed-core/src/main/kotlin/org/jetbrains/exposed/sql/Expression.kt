package org.jetbrains.exposed.sql

import org.jetbrains.exposed.sql.statements.DefaultValueMarker
import java.util.*

class QueryBuilder(val prepared: Boolean) {
    private val internalBuilder = StringBuilder()
    val args = ArrayList<Pair<IColumnType, Any?>>()

    fun <T> Iterable<T>.appendTo(separator: CharSequence = ", ", prefix: CharSequence = "", postfix: CharSequence = "", transform: (QueryBuilder.(T) -> Unit)) {
        internalBuilder.append(prefix)
        for ((count, element) in this.withIndex()) {
            if (count > 0) internalBuilder.append(separator)
            transform(element)
        }
        internalBuilder.append(postfix)
    }

    operator fun Expression<*>.unaryPlus() = this@QueryBuilder.also { toQueryBuilder(it) }

    fun append(vararg expr: Any) = apply {
        expr.forEach {
            when(it) {
                is Expression<*> -> +it
                is String -> +it
                is Char -> +it
                else -> throw IllegalArgumentException("Can't append $it as it has unknown type")
            }
        }
    }
    operator fun Char.unaryPlus() = this@QueryBuilder.also { internalBuilder.append(this) }
    operator fun String.unaryPlus() = this@QueryBuilder.also { internalBuilder.append(this) }

    operator fun invoke(body : QueryBuilder.()->Unit) = body()

    override fun toString(): String = internalBuilder.toString()

    fun <T> registerArgument(column: Column<*>, argument: T) {
        when (argument) {
            is Expression<*> -> +argument
            DefaultValueMarker -> +column.dbDefaultValue!!
            else -> registerArgument(column.columnType, argument)
        }
    }
    fun <T> registerArgument(sqlType: IColumnType, argument: T) = registerArguments(sqlType, listOf(argument))

    fun <T> registerArguments(sqlType: IColumnType, arguments: Iterable<T>) {
        fun toString(value: T) = when {
            prepared && value is String -> value
            else -> sqlType.valueToString(value)
        }
        val argumentsAndStrings = arguments.map { it to toString(it) }.sortedBy { it.second }

        argumentsAndStrings.appendTo {
            if (prepared) {
                args.add(sqlType to it.first)
                append("?")
            } else {
                append(it.second)
            }
        }
    }
}

fun <T> Iterable<T>.appendTo(builder: QueryBuilder, separator: CharSequence = ", ", prefix: CharSequence = "",
                             postfix: CharSequence = "", transform: (QueryBuilder.(T) -> Unit)) = builder.apply {
   this@appendTo.appendTo(separator, prefix, postfix, transform)
}

abstract class Expression<T> {
    private val _hashCode by lazy {
        toString().hashCode()
    }

    abstract fun toQueryBuilder(queryBuilder: QueryBuilder)

    override fun equals(other: Any?): Boolean = (other as? Expression<*>)?.toString() == toString()

    override fun hashCode(): Int = _hashCode

    override fun toString(): String = QueryBuilder(false).append(this).toString()

    companion object {
        inline fun <T, E: Expression<T>> build(builder: SqlExpressionBuilder.()->E): E =
                SqlExpressionBuilder.builder()
    }
}

abstract class ExpressionWithColumnType<T> : Expression<T>() {
    // used for operations with literals
    abstract val columnType: IColumnType
}
