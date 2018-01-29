@file:Suppress("INVISIBLE_REFERENCE", "INVISIBLE_MEMBER")
package org.jetbrains.exposed.sql

import org.jetbrains.exposed.dao.EntityID
import org.jetbrains.exposed.dao.IdTable
import org.jetbrains.exposed.sql.vendors.FunctionProvider
import org.jetbrains.exposed.sql.vendors.currentDialect
import org.joda.time.DateTime
import java.math.BigDecimal

fun Column<*>.count() : Function<Int> = Count(this)

fun <T: DateTime?> Expression<T>.date() = Date(this)

fun <T: DateTime?> Expression<T>.month() = Month(this)

fun Column<*>.countDistinct() : Function<Int> = Count(this, true)

fun<T:Comparable<T>, S:T?> ExpressionWithColumnType<in S>.min()  : ExpressionWithColumnType<T?> = Min<T, S>(this, this.columnType)

fun<T:Comparable<T>, S:T?> ExpressionWithColumnType<in S>.max() : ExpressionWithColumnType<T?> = Max<T, S>(this, this.columnType)

fun<T:Comparable<T>, S:T?> ExpressionWithColumnType<in S>.avg(scale: Int = 2)  : ExpressionWithColumnType<BigDecimal?> = Avg<T, S>(this, scale)

fun<T:Any?> Column<T>.stdDevPop(scale: Int = 2) = StdDevPop(this, scale)

fun<T:Any?> Column<T>.stdDevSamp(scale: Int = 2) = StdDevSamp(this, scale)

fun<T:Any?> Column<T>.varPop(scale: Int = 2) = VarPop(this, scale)

fun<T:Any?> Column<T>.varSamp(scale: Int = 2) = VarSamp(this, scale)

fun<T:Any?> Column<T>.sum() = Sum(this, this.columnType)

fun<R:Any> Expression<*>.castTo(columnType: IColumnType) = Cast<R>(this, columnType)

fun<T:String?> Expression<T>.substring(start: Int, length: Int) : Function<T> =
        Substring(this, LiteralOp(IntegerColumnType(), start), LiteralOp(IntegerColumnType(), length))

fun<T:String?> Expression<T>.trim() : Function<T> = Trim(this)

fun<T:String?> Expression<T>.lowerCase() : Function<T> = LowerCase(this)
fun<T:String?> Expression<T>.upperCase() : Function<T> = UpperCase(this)

fun <T:Any?> Column<T>.groupConcat(separator: String? = null, distinct: Boolean = false, vararg orderBy: Pair<Expression<*>,Boolean>): GroupConcat =
        GroupConcat(this, separator, distinct, *orderBy)

object SqlExpressionBuilder {
    fun <T, S:T?, E:ExpressionWithColumnType<S>, R:T> coalesce(expr: E, alternate: ExpressionWithColumnType<out T>) : ExpressionWithColumnType<R> =
            Coalesce(expr, alternate)

    fun case(value: Expression<*>? = null) = Case(value)

    fun<T, S:T?> ExpressionWithColumnType<in S>.wrap(value: T): Expression<T> = QueryParameter(value, columnType)

    infix fun<T> ExpressionWithColumnType<T>.eq(t: T) : Op<Boolean> {
        if (t == null) {
            return isNull()
        }
        return EqOp(this, wrap(t))
    }

    infix fun <T:Comparable<T>> Column<EntityID<T>>.eq(t: T?) : Op<Boolean> {
        if (t == null) {
            return isNull()
        }
        return EqOp(this, wrap(t))
    }

    infix fun<T, S1: T?, S2: T?> Expression<S1>.eq(other: Expression<S2>) : Op<Boolean> = EqOp (this, other)

    infix fun<T> ExpressionWithColumnType<T>.neq(other: T): Op<Boolean> {
        if (other == null) {
            return isNotNull()
        }

        return NeqOp(this, wrap(other))
    }

    fun<T, S: T?> ExpressionWithColumnType<S>.neq(other: Expression<S>) : Op<Boolean> = NeqOp (this, other)

    fun<T> ExpressionWithColumnType<T>.isNull(): Op<Boolean> = IsNullOp(this)

    fun<T> ExpressionWithColumnType<T>.isNotNull(): Op<Boolean> = IsNotNullOp(this)

    infix fun<T:Comparable<T>, S: T?> ExpressionWithColumnType<in S>.less(t: T) : Op<Boolean> = LessOp(this, wrap(t))

    fun<T:Comparable<T>, S: T?> ExpressionWithColumnType<in S>.less(other: Expression<S>) = LessOp (this, other)

    infix fun<T:Comparable<T>, S: T?> ExpressionWithColumnType<in S>.lessEq(t: T) : Op<Boolean> = LessEqOp(this, wrap(t))

    fun<T:Comparable<T>, S: T?> ExpressionWithColumnType<in S>.lessEq(other: Expression<S>) : Op<Boolean> = LessEqOp(this, other)

    infix fun<T:Comparable<T>, S: T?> ExpressionWithColumnType<in S>.greater(t: T) : Op<Boolean> = GreaterOp(this, wrap(t))

    fun<T:Comparable<T>, S: T?> ExpressionWithColumnType<in S>.greater(other: Expression<S>) : Op<Boolean> = GreaterOp (this, other)

    infix fun<T:Comparable<T>, S: T?> ExpressionWithColumnType<in S>.greaterEq(t: T) : Op<Boolean> = GreaterEqOp (this, wrap(t))

    fun<T:Comparable<T>, S: T?> ExpressionWithColumnType<in S>.greaterEq(other: Expression<T>) : Op<Boolean> = GreaterEqOp (this, other)

    operator fun<T, S: T> ExpressionWithColumnType<T>.plus(other: Expression<S>) : ExpressionWithColumnType<T> = PlusOp (this, other, columnType)

    operator fun<T> ExpressionWithColumnType<T>.plus(t: T) : ExpressionWithColumnType<T> = PlusOp (this, wrap(t), columnType)

    operator fun<T, S: T> ExpressionWithColumnType<T>.minus(other: Expression<S>) : ExpressionWithColumnType<T> = MinusOp (this, other, columnType)

    operator fun<T> ExpressionWithColumnType<T>.minus(t: T) : ExpressionWithColumnType<T> = MinusOp (this, wrap(t), columnType)

    operator fun<T, S: T> ExpressionWithColumnType<T>.times(other: Expression<S>) : ExpressionWithColumnType<T> = TimesOp (this, other, columnType)

    operator fun<T> ExpressionWithColumnType<T>.times(t: T) : ExpressionWithColumnType<T> = TimesOp (this, wrap(t), columnType)

    operator fun<T, S: T> ExpressionWithColumnType<T>.div(other: Expression<S>) : ExpressionWithColumnType<T> = DivideOp (this, other, columnType)

    operator fun<T> ExpressionWithColumnType<T>.div(t: T) : ExpressionWithColumnType<T> = DivideOp (this, wrap(t), columnType)

    infix fun<T:String?> ExpressionWithColumnType<T>.like(pattern: String): Op<Boolean> = LikeOp(this, QueryParameter(pattern, columnType))

    infix fun<T:String?> ExpressionWithColumnType<T>.notLike(pattern: String): Op<Boolean> = NotLikeOp(this, QueryParameter(pattern, columnType))

    infix fun<T:String?> ExpressionWithColumnType<T>.regexp(pattern: String): Op<Boolean> = RegexpOp(this, QueryParameter(pattern, columnType))

    infix fun<T:String?> ExpressionWithColumnType<T>.notRegexp(pattern: String): Op<Boolean> = NotRegexpOp(this, QueryParameter(pattern, columnType))

    infix fun<T> ExpressionWithColumnType<T>.inList(list: Iterable<T>): Op<Boolean> = InListOrNotInListOp(this, list, isInList = true)

    @Suppress("UNCHECKED_CAST")
    @JvmName("inListIds")
    infix fun<T:Comparable<T>> Column<EntityID<T>>.inList(list: Iterable<T>): Op<Boolean> {
        val idTable = (columnType as EntityIDColumnType<T>).idColumn.table as IdTable<T>
        return inList(list.map { EntityID(it, idTable) })
    }

    infix fun<T> ExpressionWithColumnType<T>.notInList(list: Iterable<T>): Op<Boolean> = InListOrNotInListOp(this, list, isInList = false)

    @Suppress("UNCHECKED_CAST")
    fun<T, S: T?> ExpressionWithColumnType<S>.asLiteral(value: T) = when (value) {
        is Boolean -> booleanLiteral(value)
        is Int -> intLiteral(value)
        is Long -> longLiteral(value)
        is String -> stringLiteral(value)
        is DateTime -> if ((columnType as DateColumnType).time) dateTimeLiteral(value) else dateLiteral(value)
        else -> LiteralOp(columnType, value)
    } as LiteralOp<T>

    fun<T, S: T?> ExpressionWithColumnType<S>.between(from: T, to: T): Op<Boolean> = Between(this, asLiteral(from), asLiteral(to))

    fun ExpressionWithColumnType<Int>.intToDecimal(): ExpressionWithColumnType<BigDecimal> = NoOpConversion(this, DecimalColumnType(15, 0))

    fun <T : String?> ExpressionWithColumnType<T>.match(pattern: String, mode: FunctionProvider.MatchMode?): Op<Boolean> {
        return with(currentDialect.functionProvider) {
            this@match.match(pattern, mode)
        }
    }

    infix fun <T: String?> ExpressionWithColumnType<T>.match(pattern: String): Op<Boolean> = match(pattern, null)
}
