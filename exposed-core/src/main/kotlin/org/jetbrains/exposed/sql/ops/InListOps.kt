package org.jetbrains.exposed.sql.ops

import org.jetbrains.exposed.sql.ComplexExpression
import org.jetbrains.exposed.sql.ExpressionWithColumnType
import org.jetbrains.exposed.sql.Op
import org.jetbrains.exposed.sql.QueryBuilder
import org.jetbrains.exposed.sql.vendors.OracleDialect
import org.jetbrains.exposed.sql.vendors.currentDialectIfAvailable

/**
 * Represents an SQL operator that checks if [expr] is equals to any element from [list].
 */
abstract class InListOrNotInListBaseOp<V>(
    /** Returns the expression compared to each element of the list. */
    open val expr: Any,
    /** Returns the query to check against. */
    val list: Iterable<V>,
    /** Returns `false` if the check is inverted, `true` otherwise. */
    val isInList: Boolean = true
) : Op<Boolean>(), ComplexExpression {

    protected abstract val columnTypes: List<ExpressionWithColumnType<*>>

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

            if (!iterator.hasNext() && currentDialectIfAvailable !is OracleDialect) {
                when {
                    isInList -> append(" = ")
                    else -> append(" != ")
                }
                registerValues(firstValue)
            } else {
                when {
                    isInList -> append(" IN (")
                    else -> append(" NOT IN (")
                }
                registerValues(firstValue)
                iterator.forEach { value ->
                    append(", ")
                    registerValues(value)
                }
                append(')')
            }
        }
    }

    protected abstract fun QueryBuilder.registerValues(values: V)
}

/**
 * Represents an SQL operator that checks if a single-value [expr] is equal to any element from [list].
 *
 * To inverse the operator and check if [expr] is **not** in [list], set [isInList] to `false`.
 */
class SingleValueInListOp<T>(
    override val expr: ExpressionWithColumnType<out T>,
    list: Iterable<T>,
    isInList: Boolean = true
) : InListOrNotInListBaseOp<T>(expr, list, isInList) {
    override val columnTypes: List<ExpressionWithColumnType<*>> = listOf(expr)

    override fun QueryBuilder.registerValues(values: T) {
        registerArgument(expr.columnType, values)
    }
}

/**
 * Represents an SQL operator that checks if both values of a `Pair` [expr] match any element from [list].
 *
 * To inverse the operator and check if the `Pair` is **not** in [list], set [isInList] to `false`.
 */
class PairInListOp<T1, T2>(
    override val expr: Pair<ExpressionWithColumnType<T1>, ExpressionWithColumnType<T2>>,
    list: Iterable<Pair<T1, T2>>,
    isInList: Boolean = true
) : InListOrNotInListBaseOp<Pair<T1, T2>>(expr, list, isInList) {
    override val columnTypes: List<ExpressionWithColumnType<*>> = listOf(expr.first, expr.second)

    override fun QueryBuilder.registerValues(values: Pair<T1, T2>) {
        append("(")
        registerArgument(expr.first.columnType, values.first)
        append(", ")
        registerArgument(expr.second.columnType, values.second)
        append(")")
    }
}

/**
 * Represents an SQL operator that checks if all values of a `Triple` [expr] match any element from [list].
 *
 * To inverse the operator and check if the `Triple` is **not** in [list], set [isInList] to `false`.
 */
class TripleInListOp<T1, T2, T3>(
    override val expr: Triple<ExpressionWithColumnType<T1>, ExpressionWithColumnType<T2>, ExpressionWithColumnType<T3>>,
    list: Iterable<Triple<T1, T2, T3>>,
    isInList: Boolean = true
) : InListOrNotInListBaseOp<Triple<T1, T2, T3>>(expr, list, isInList) {
    override val columnTypes: List<ExpressionWithColumnType<*>> = listOf(expr.first, expr.second, expr.third)

    override fun QueryBuilder.registerValues(values: Triple<T1, T2, T3>) {
        append("(")
        registerArgument(expr.first.columnType, values.first)
        append(", ")
        registerArgument(expr.second.columnType, values.second)
        append(", ")
        registerArgument(expr.third.columnType, values.third)
        append(")")
    }
}
