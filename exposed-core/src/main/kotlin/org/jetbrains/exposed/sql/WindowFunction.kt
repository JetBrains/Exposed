package org.jetbrains.exposed.sql

import org.jetbrains.exposed.sql.vendors.currentDialect
import java.math.BigDecimal

/** Interface for functions that can be used as window functions. */
interface WindowFunction<T> {
    /** Returns window function definition. */
    fun over(): WindowFunctionDefinition<T>

    /** Appends the SQL representation of this function to the specified [queryBuilder]. */
    fun toQueryBuilder(queryBuilder: QueryBuilder)
}

/** Represents an SQL window function with window definition. */
@Suppress("TooManyFunctions")
class WindowFunctionDefinition<T>(
    override val columnType: IColumnType<T & Any>,
    /** Returns the function that definition is used for. */
    private val function: WindowFunction<T>
) : ExpressionWithColumnType<T>() {
    /** Returns expressions in PARTITION BY clause. */
    private val partitionExpressions: List<Expression<*>> = mutableListOf()

    /** Returns expressions in ORDER BY clause. */
    private val orderByExpressions: List<Pair<Expression<*>, SortOrder>> = mutableListOf()

    /** Returns window frame clause. */
    private var frameClause: WindowFrameClause? = null

    /**
     * Groups the rows of the query by specified [expressions] into partitions,
     * which are processed separately by the window function.
     */
    fun partitionBy(vararg expressions: Expression<*>): WindowFunctionDefinition<T> = apply {
        (partitionExpressions as MutableList).addAll(expressions)
    }

    /**
     * Defines sorting order by [column] and [order] in which the rows of a partition
     * are processed by the window function.
     */
    fun orderBy(column: Expression<*>, order: SortOrder = SortOrder.ASC): WindowFunctionDefinition<T> =
        orderBy(column to order)

    /**
     * Defines sorting order by column and order pairs [order] in which the rows of a partition
     * are processed by the window function.
     */
    fun orderBy(vararg order: Pair<Expression<*>, SortOrder>): WindowFunctionDefinition<T> = apply {
        (orderByExpressions as MutableList).addAll(order)
    }

    /**
     * Defines the set of rows constituting the window frame, which is a subset of the current partition.
     * Window frame uses [WindowFrameUnit.ROWS] mode and specified [start] and [end] bounds.
     */
    fun rows(
        start: WindowFrameBound,
        end: WindowFrameBound
    ): WindowFunctionDefinition<T> = apply {
        frameClause = WindowFrameClause(WindowFrameUnit.ROWS, start, end)
    }

    /**
     * Defines the set of rows constituting the window frame, which is a subset of the current partition.
     * Window frame uses [WindowFrameUnit.ROWS] mode and specified [start] bound.
     */
    fun rows(
        start: CurrentOrPreceding
    ): WindowFunctionDefinition<T> = apply {
        frameClause = WindowFrameClause(WindowFrameUnit.ROWS, start, null)
    }

    /**
     * Defines the set of rows constituting the window frame, which is a subset of the current partition.
     * Window frame uses [WindowFrameUnit.RANGE] mode and specified [start] and [end] bounds.
     */
    fun range(
        start: WindowFrameBound,
        end: WindowFrameBound
    ): WindowFunctionDefinition<T> = apply {
        frameClause = WindowFrameClause(WindowFrameUnit.RANGE, start, end)
    }

    /**
     * Defines the set of rows constituting the window frame, which is a subset of the current partition.
     * Window frame uses [WindowFrameUnit.RANGE] mode and specified [start] bound.
     */
    fun range(
        start: CurrentOrPreceding
    ): WindowFunctionDefinition<T> = apply {
        frameClause = WindowFrameClause(WindowFrameUnit.RANGE, start, null)
    }

    /**
     * Defines the set of rows constituting the window frame, which is a subset of the current partition.
     * Window frame uses [WindowFrameUnit.GROUPS] mode and specified [start] and [end] bounds.
     */
    fun groups(
        start: WindowFrameBound,
        end: WindowFrameBound
    ): WindowFunctionDefinition<T> = apply {
        frameClause = WindowFrameClause(WindowFrameUnit.GROUPS, start, end)
    }

    /**
     * Defines the set of rows constituting the window frame, which is a subset of the current partition.
     * Window frame uses [WindowFrameUnit.GROUPS] mode and specified [start] bound.
     */
    fun groups(
        start: CurrentOrPreceding,
    ): WindowFunctionDefinition<T> = apply {
        frameClause = WindowFrameClause(WindowFrameUnit.GROUPS, start, null)
    }

    override fun toQueryBuilder(queryBuilder: QueryBuilder) = queryBuilder {
        function.toQueryBuilder(this)
        +" OVER("
        appendPartitionByClause()
        appendOrderByClause()
        frameClause?.let {
            +" "
            it.toQueryBuilder(this)
        }
        +")"
    }

    private fun QueryBuilder.appendPartitionByClause() {
        if (partitionExpressions.isNotEmpty()) {
            +"PARTITION BY "
            partitionExpressions.appendTo {
                +((it as? ExpressionWithColumnTypeAlias)?.aliasOnlyExpressionWithColumnType() ?: (it as? ExpressionAlias)?.aliasOnlyExpression() ?: it)
            }
        }
    }

    private fun QueryBuilder.appendOrderByClause() {
        if (orderByExpressions.isNotEmpty()) {
            +" ORDER BY "
            orderByExpressions.appendTo { (expression, sortOrder) ->
                currentDialect.dataTypeProvider.precessOrderByClause(this, expression, sortOrder)
            }
        }
    }
}

/** Represents an SQL window function frame clause */
class WindowFrameClause(
    /** Returns frame unit (also called mode). */
    private val unit: WindowFrameUnit,
    /** Returns frame start bound. */
    private val start: WindowFrameBound,
    /** Returns frame end bound. */
    private val end: WindowFrameBound? = null
) {
    /** Appends the SQL representation of this window function clause to the specified [queryBuilder]. */
    fun toQueryBuilder(queryBuilder: QueryBuilder) = queryBuilder {
        append(unit.name, " ")

        if (end != null) {
            +"BETWEEN "
            start.toQueryBuilder(this)
            +" AND "
            end.toQueryBuilder(this)
        } else {
            start.toQueryBuilder(this)
        }
    }
}

/** Represents an SQL window function frame unit (also called mode). */
enum class WindowFrameUnit {
    /** A frame unit based on a fixed amount of rows before and/or after the current row. */
    ROWS,

    /** A frame unit consisting of a logical range of rows, based on their value compared to the current row value. */
    RANGE,

    /** A frame unit based on a logical range of rows around the current row, but with a distinct value count. */
    GROUPS
}

/** Represents an SQL window function frame start and end bound. */
sealed interface WindowFrameBound {
    companion object {
        /** Returns UNBOUNDED PRECEDING window function frame bound */
        fun unboundedPreceding(): UnboundedPrecedingWindowFrameBound {
            return UnboundedPrecedingWindowFrameBound()
        }

        /** Returns UNBOUNDED FOLLOWING window function frame bound */
        fun unboundedFollowing(): UnboundedFollowingWindowFrameBound {
            return UnboundedFollowingWindowFrameBound()
        }

        /** Returns [offset] PRECEDING window function frame bound */
        fun offsetPreceding(offset: Expression<Int>): OffsetPrecedingWindowFrameBound {
            return OffsetPrecedingWindowFrameBound(offset)
        }

        /** Returns [offset] PRECEDING window function frame bound */
        fun offsetPreceding(offset: Int): OffsetPrecedingWindowFrameBound {
            return OffsetPrecedingWindowFrameBound(intLiteral(offset))
        }

        /** Returns [offset] FOLLOWING window function frame bound */
        fun offsetFollowing(offset: Expression<Int>): OffsetFollowingWindowFrameBound {
            return OffsetFollowingWindowFrameBound(offset)
        }

        /** Returns [offset] FOLLOWING window function frame bound */
        fun offsetFollowing(offset: Int): OffsetFollowingWindowFrameBound {
            return OffsetFollowingWindowFrameBound(intLiteral(offset))
        }

        /** Returns CURRENT ROW window function frame bound */
        fun currentRow(): CurrentRowWindowFrameBound {
            return CurrentRowWindowFrameBound
        }
    }

    /** Appends the SQL representation of this window function clause to the specified [queryBuilder]. */
    fun toQueryBuilder(queryBuilder: QueryBuilder)
}

/** Represents an SQL window function frame bound that is CURRENT ROW or one of PRECEDING forms. */
interface CurrentOrPreceding : WindowFrameBound

/** Represents an SQL window function frame bound that is CURRENT ROW or one of FOLLOWING forms. */
interface CurrentOrFollowing : WindowFrameBound

/**
 * Represents UNBOUNDED PRECEDING or FOLLOWING window function frame bound.
 * [direction] specifies whether first or last partition row will be used.
 */
open class UnboundedWindowFrameBound(
    private val direction: WindowFrameBoundDirection
) : WindowFrameBound {
    override fun toQueryBuilder(queryBuilder: QueryBuilder) = queryBuilder {
        append("UNBOUNDED ", direction.name)
    }
}

/** Represents UNBOUNDED PRECEDING window function frame bound. */
class UnboundedPrecedingWindowFrameBound :
    UnboundedWindowFrameBound(WindowFrameBoundDirection.PRECEDING),
    CurrentOrPreceding

/** Represents UNBOUNDED FOLLOWING window function frame bound. */
class UnboundedFollowingWindowFrameBound :
    UnboundedWindowFrameBound(WindowFrameBoundDirection.FOLLOWING),
    CurrentOrFollowing

/**
 * Represents an [offset] PRECEDING or FOLLOWING window function frame bound.
 * [direction] specifies whether previous or next partition rows will be used.
 */
open class OffsetWindowFrameBound(
    private val offset: Expression<Int>,
    private val direction: WindowFrameBoundDirection
) : WindowFrameBound {
    override fun toQueryBuilder(queryBuilder: QueryBuilder) = queryBuilder {
        append(offset, " ", direction.name)
    }
}

/** Represents [offset] PRECEDING window function frame bound. */
class OffsetPrecedingWindowFrameBound(
    offset: Expression<Int>
) : OffsetWindowFrameBound(offset, WindowFrameBoundDirection.PRECEDING), CurrentOrPreceding

/** Represents [offset] FOLLOWING window function frame bound. */
class OffsetFollowingWindowFrameBound(
    offset: Expression<Int>
) : OffsetWindowFrameBound(offset, WindowFrameBoundDirection.FOLLOWING), CurrentOrFollowing

/** Represents an CURRENT ROW window function frame bound. */
object CurrentRowWindowFrameBound : WindowFrameBound, CurrentOrPreceding, CurrentOrFollowing {
    override fun toQueryBuilder(queryBuilder: QueryBuilder) = queryBuilder {
        +"CURRENT ROW"
    }
}

/** Represents window function frame bound direction. */
enum class WindowFrameBoundDirection {
    /** Gets rows before the current row. */
    PRECEDING,

    /** Gets rows after the current row. */
    FOLLOWING
}

/** Represents an SQL function that returns the number of the current row within its partition, counting from 1. */
class RowNumber : WindowFunction<Long> {
    override fun toQueryBuilder(queryBuilder: QueryBuilder): Unit = queryBuilder {
        +"ROW_NUMBER()"
    }

    override fun over(): WindowFunctionDefinition<Long> {
        return WindowFunctionDefinition(LongColumnType(), this)
    }
}

/**
 * Represents an SQL function that returns the rank of the current row, with gaps; that is, the row_number
 * of the first row in its peer group.
 */
class Rank : WindowFunction<Long> {
    override fun toQueryBuilder(queryBuilder: QueryBuilder): Unit = queryBuilder {
        +"RANK()"
    }

    override fun over(): WindowFunctionDefinition<Long> {
        return WindowFunctionDefinition(LongColumnType(), this)
    }
}

/**
 * Represents an SQL function that returns the rank of the current row, without gaps; this function effectively
 * counts peer groups.
 */
class DenseRank : WindowFunction<Long> {
    override fun toQueryBuilder(queryBuilder: QueryBuilder): Unit = queryBuilder {
        +"DENSE_RANK()"
    }

    override fun over(): WindowFunctionDefinition<Long> {
        return WindowFunctionDefinition(LongColumnType(), this)
    }
}

/**
 * Represents an SQL function that returns the relative rank of the current row, that is (rank - 1) /
 * (total partition rows - 1). The value thus ranges from 0 to 1 inclusive.
 * [scale] represents decimal digits count in the fractional part of result.
 */
class PercentRank(private val scale: Int = 2) : WindowFunction<BigDecimal> {
    override fun toQueryBuilder(queryBuilder: QueryBuilder): Unit = queryBuilder {
        +"PERCENT_RANK()"
    }

    override fun over(): WindowFunctionDefinition<BigDecimal> {
        return WindowFunctionDefinition(DecimalColumnType(Int.MAX_VALUE, scale), this)
    }
}

/**
 * Represents an SQL function that Returns the cumulative distribution, that is (number of partition rows preceding
 * or peers with current row) / (total partition rows). The value thus ranges from 1/N to 1.
 * [scale] represents decimal digits count in the fractional part of result.
 */
class CumeDist(private val scale: Int = 2) : WindowFunction<BigDecimal> {
    override fun toQueryBuilder(queryBuilder: QueryBuilder): Unit = queryBuilder {
        +"CUME_DIST()"
    }

    override fun over(): WindowFunctionDefinition<BigDecimal> {
        return WindowFunctionDefinition(DecimalColumnType(Int.MAX_VALUE, scale), this)
    }
}

/** Returns an integer ranging from 1 to the argument value, dividing the partition as equally as possible. */
class Ntile(
    /** Returns number of buckets. */
    val numBuckets: ExpressionWithColumnType<Int>
) : WindowFunction<Int> {
    override fun toQueryBuilder(queryBuilder: QueryBuilder): Unit = queryBuilder {
        append("NTILE(", numBuckets, ")")
    }

    override fun over(): WindowFunctionDefinition<Int> {
        return WindowFunctionDefinition(IntegerColumnType(), this)
    }
}

/**
 * Represents an SQL function that returns value evaluated at the row that is [offset] rows before the current row
 * within the partition; if there is no such row, instead returns [defaultValue].
 */
class Lag<T>(
    /** Returns the expression from which the rows are counted. */
    val expr: ExpressionWithColumnType<T>,
    /** Returns number of rows before the current row. */
    val offset: ExpressionWithColumnType<Int> = intLiteral(1),
    /** Returns value that is used if no row found at such offset. */
    val defaultValue: ExpressionWithColumnType<T>? = null
) : WindowFunction<T?> {
    override fun toQueryBuilder(queryBuilder: QueryBuilder) = queryBuilder {
        append("LAG(", expr, ", ", offset)
        if (defaultValue != null) append(", ", defaultValue)
        append(")")
    }

    override fun over(): WindowFunctionDefinition<T?> {
        return WindowFunctionDefinition(expr.columnType, this)
    }
}

/**
 * Represents an SQL function that returns value evaluated at the row that is [offset] rows after the current row
 * within the partition; if there is no such row, instead returns [defaultValue].
 */
class Lead<T>(
    /** Returns the expression from which the rows are counted. */
    val expr: ExpressionWithColumnType<T>,
    /** Returns number of rows before the current row. */
    val offset: ExpressionWithColumnType<Int> = intLiteral(1),
    /** Returns value that is used if no row found at such offset. */
    val defaultValue: ExpressionWithColumnType<T>? = null
) : WindowFunction<T?> {
    override fun toQueryBuilder(queryBuilder: QueryBuilder) = queryBuilder {
        append("LEAD(", expr, ", ", offset)
        if (defaultValue != null) append(", ", defaultValue)
        append(")")
    }

    override fun over(): WindowFunctionDefinition<T?> {
        return WindowFunctionDefinition(expr.columnType, this)
    }
}

/** Represents an SQL function that returns [expr] evaluated at the row that is the first row of the window frame. */
class FirstValue<T>(
    /** Returns the expression to evaluate. */
    val expr: ExpressionWithColumnType<T>
) : WindowFunction<T> {
    override fun toQueryBuilder(queryBuilder: QueryBuilder) = queryBuilder {
        append("FIRST_VALUE(", expr, ")")
    }

    override fun over(): WindowFunctionDefinition<T> {
        return WindowFunctionDefinition(expr.columnType, this)
    }
}

/** Represents an SQL function that returns [expr] evaluated at the row that is the last row of the window frame. */
class LastValue<T>(
    /** Returns the expression to evaluate. */
    val expr: ExpressionWithColumnType<T>
) : WindowFunction<T> {
    override fun toQueryBuilder(queryBuilder: QueryBuilder) = queryBuilder {
        append("LAST_VALUE(", expr, ")")
    }

    override fun over(): WindowFunctionDefinition<T> {
        return WindowFunctionDefinition(expr.columnType, this)
    }
}

/**
 * Represents an SQL function that returns [expr] evaluated at the row that is the [n]'th row of the window frame
 * (counting from 1); null if no such row
 */
class NthValue<T>(
    /** Returns the expression to evaluate. */
    val expr: ExpressionWithColumnType<T>,
    /** Returns the row n to find. */
    val n: ExpressionWithColumnType<Int>
) : WindowFunction<T?> {
    override fun toQueryBuilder(queryBuilder: QueryBuilder) = queryBuilder {
        append("NTH_VALUE(", expr, ", ", n, ")")
    }

    override fun over(): WindowFunctionDefinition<T?> {
        return WindowFunctionDefinition(expr.columnType, this)
    }
}
