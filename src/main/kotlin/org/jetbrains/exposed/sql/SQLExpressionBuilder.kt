package org.jetbrains.exposed.sql

import org.jetbrains.exposed.sql.vendors.FunctionProvider
import org.jetbrains.exposed.sql.vendors.currentDialect
import org.joda.time.DateTime
import java.math.BigDecimal

fun Column<*>.count() = Count(this)

fun <T: DateTime?> Expression<T>.date() = Date(this)

fun <T: DateTime?> Expression<T>.month() = Month(this)

fun Column<*>.countDistinct() = Count(this, true)

fun<T:Any?> Column<T>.min() = Min(this, this.columnType)

fun<T:Any?> Column<T>.max() = Max(this, this.columnType)

fun<T:Any?> Column<T>.avg(scale: Int = 2) = Avg(this, scale)

fun<T:Any?> Column<T>.stdDevPop(scale: Int = 2) = StdDevPop(this, scale)

fun<T:Any?> Column<T>.stdDevSamp(scale: Int = 2) = StdDevSamp(this, scale)

fun<T:Any?> Column<T>.varPop(scale: Int = 2) = VarPop(this, scale)

fun<T:Any?> Column<T>.varSamp(scale: Int = 2) = VarSamp(this, scale)

fun<T:Any?> Column<T>.sum() = Sum(this, this.columnType)

fun<T:String?> Expression<T>.substring(start: Int, length: Int): Substring {
    return Substring(this, LiteralOp(IntegerColumnType(), start), LiteralOp(IntegerColumnType(), length))
}

fun<T:String?> Expression<T>.trim() =Trim(this)

fun <T:String?> Expression<T>.lowerCase() = LowerCase(this)
fun <T:String?> Expression<T>.upperCase() = UpperCase(this)

fun <T:Any?> Column<T>.groupConcat(separator: String? = null, distinct: Boolean = false, vararg orderBy: Pair<Expression<*>,Boolean>): GroupConcat {
    return GroupConcat(this, separator, distinct, *orderBy)
}

object SqlExpressionBuilder {
    fun <T:Any> coalesce(expr: ExpressionWithColumnType<out T?>, alternate: ExpressionWithColumnType<out T>): ExpressionWithColumnType<T> {
        return Coalesce(expr, alternate)
    }

    fun case(value: Expression<*>? = null) = Case(value)

    fun<T> ExpressionWithColumnType<T>.wrap(value: T): Expression<T> = QueryParameter(value, columnType)

    infix fun<T> ExpressionWithColumnType<T>.eq(t: T) : Op<Boolean> {
        if (t == null) {
            return isNull()
        }
        return EqOp(this, wrap(t))
    }

    fun<T, S: T> Expression<T>.eq(other: Expression<S>) : Op<Boolean> = EqOp (this, other)

    infix fun<T, S: T> ExpressionWithColumnType<T>.eq(other: Expression<S>) : Op<Boolean> = EqOp (this, other)

    infix fun<T> ExpressionWithColumnType<T>.neq(other: T): Op<Boolean> {
        if (other == null) {
            return isNotNull()
        }

        return NeqOp(this, wrap(other))
    }

    fun<T, S: T> ExpressionWithColumnType<T>.neq(other: Expression<S>) : Op<Boolean> = NeqOp (this, other)

    fun<T> ExpressionWithColumnType<T>.isNull(): Op<Boolean> = IsNullOp(this)

    fun<T> ExpressionWithColumnType<T>.isNotNull(): Op<Boolean> = IsNotNullOp(this)

    infix fun<T> ExpressionWithColumnType<T>.less(t: T) : Op<Boolean> = LessOp(this, wrap(t))

    fun<T, S: T> ExpressionWithColumnType<T>.less(other: Expression<S>) : Op<Boolean> = LessOp (this, other)

    infix fun<T> ExpressionWithColumnType<T>.lessEq(t: T) : Op<Boolean> =LessEqOp(this, wrap(t))

    fun<T, S: T> ExpressionWithColumnType<T>.lessEq(other: Expression<S>) : Op<Boolean> = LessEqOp(this, other)

    infix fun<T> ExpressionWithColumnType<T>.greater(t: T) : Op<Boolean> = GreaterOp(this, wrap(t))

    fun<T, S: T> ExpressionWithColumnType<T>.greater(other: Expression<S>) : Op<Boolean> = GreaterOp (this, other)

    infix fun<T> ExpressionWithColumnType<T>.greaterEq(t: T) : Op<Boolean> = GreaterEqOp (this, wrap(t))

    fun<T, S: T> ExpressionWithColumnType<T>.greaterEq(other: Expression<S>) : Op<Boolean> = GreaterEqOp (this, other)

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

    infix fun<T> ExpressionWithColumnType<T>.inList(list: List<T>): Op<Boolean> = InListOrNotInListOp(this, list, isInList = true)

    infix fun<T> ExpressionWithColumnType<T>.notInList(list: List<T>): Op<Boolean> = InListOrNotInListOp(this, list, isInList = false)

    fun<T, S: Any> ExpressionWithColumnType<T>.asLiteral(value: S): LiteralOp<*> {
        return when (value) {
            is Boolean -> booleanLiteral(value)
            is Int -> intLiteral(value)
            is Long -> longLiteral(value)
            is String -> stringLiteral(value)
            is DateTime -> dateTimeLiteral(value)
            else -> LiteralOp<T>(columnType, value)
        }
    }

    fun<T, S: Any> ExpressionWithColumnType<T>.between(from: S, to: S): Op<Boolean> = Between(this, asLiteral(from), asLiteral(to))

    fun ExpressionWithColumnType<Int>.intToDecimal(): ExpressionWithColumnType<BigDecimal> = NoOpConversion(this, DecimalColumnType(15, 0))

    fun <T : String?> ExpressionWithColumnType<T>.match(pattern: String, mode: FunctionProvider.MatchMode?): Op<Boolean> {
        return with(currentDialect.functionProvider) {
            this@match.match(pattern, mode)
        }
    }

    infix fun <T : String?> ExpressionWithColumnType<T>.match(pattern: String): Op<Boolean> = match(pattern, null)
}
