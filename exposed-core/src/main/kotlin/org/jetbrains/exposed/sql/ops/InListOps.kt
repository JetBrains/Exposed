package org.jetbrains.exposed.sql.ops

import org.jetbrains.exposed.sql.ComplexExpression
import org.jetbrains.exposed.sql.ExpressionWithColumnType
import org.jetbrains.exposed.sql.Op
import org.jetbrains.exposed.sql.QueryBuilder

/**
 * Represents an SQL operator that checks if [expr] is equals to any element from [list].
 */
abstract class InListOrNotInListBaseOp<V> (
    /** Returns the expression compared to each element of the list. */
    val expr: Any,
    /** Returns the query to check against. */
    val list: Iterable<V>,
    /** Returns `true` if the check is inverted, `false` otherwise. */
    val isInList: Boolean = true
) : Op<Boolean>(), ComplexExpression {

    protected abstract val columnTypes: List<ExpressionWithColumnType<*>>
    protected abstract fun extractValues(value: V): List<Any?>

    override fun toQueryBuilder(queryBuilder: QueryBuilder): Unit = queryBuilder {
        val iterator = list.iterator()
        if (!iterator.hasNext()) {
            if (isInList) {
                +FALSE
            } else {
                +TRUE
            }
        } else {
            val singleColumn = columnTypes.singleOrNull()
            if (singleColumn != null) {
                append(singleColumn)
            } else {
                columnTypes.appendTo(prefix = "(", postfix = ")") { +it }
            }

            val firstValue = iterator.next()

            if (!iterator.hasNext()) {
                when {
                    isInList -> append(" = ")
                    else -> append(" != ")
                }
                registerValues(extractValues(firstValue))
            } else {
                when {
                    isInList -> append(" IN (")
                    else -> append(" NOT IN (")
                }
                registerValues(extractValues(firstValue))
                iterator.forEach { value ->
                    append(", ")
                    registerValues(extractValues(value))
                }
                append(')')
            }
        }
    }

    private fun QueryBuilder.registerValues(values: List<Any?>) {
        val singleColumn = columnTypes.singleOrNull()
        if (singleColumn != null) {
            registerArgument(singleColumn.columnType, values.single())
        } else {
            append("(")
            columnTypes.forEachIndexed { index, columnExpression ->
                if (index != 0) append(", ")
                registerArgument(columnExpression.columnType, values[index])
            }
            append(")")
        }
    }
}

class SingleValueInListOp<T>(
    expr: ExpressionWithColumnType<out T>,
    list: Iterable<T>,
    isInList: Boolean = true
) : InListOrNotInListBaseOp<T>(expr, list, isInList) {
    override val columnTypes: List<ExpressionWithColumnType<*>> = listOf(expr)

    override fun extractValues(value: T): List<Any?> = listOf(value)
}

class PairInListOp<T1, T2>(
    expr: Pair<ExpressionWithColumnType<T1>, ExpressionWithColumnType<T2>>,
    list: Iterable<Pair<T1, T2>>,
    isInList: Boolean = true
) : InListOrNotInListBaseOp<Pair<T1, T2>>(expr, list, isInList) {
    override val columnTypes: List<ExpressionWithColumnType<*>> = listOf(expr.first, expr.second)

    override fun extractValues(value: Pair<T1, T2>): List<Any?> = listOf(value.first, value.second)
}

class TripleInListOp<T1, T2, T3>(
    expr: Triple<ExpressionWithColumnType<T1>, ExpressionWithColumnType<T2>, ExpressionWithColumnType<T3>>,
    list: Iterable<Triple<T1, T2, T3>>,
    isInList: Boolean = true
) : InListOrNotInListBaseOp<Triple<T1, T2, T3>>(expr, list, isInList) {
    override val columnTypes: List<ExpressionWithColumnType<*>> = listOf(expr.first, expr.second, expr.third)

    override fun extractValues(value: Triple<T1, T2, T3>): List<Any?> = listOf(value.first, value.second, value.third)
}
