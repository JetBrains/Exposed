@file:Suppress("INVISIBLE_REFERENCE", "INVISIBLE_MEMBER")
package org.jetbrains.exposed.sql

import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.EntityIDFunctionProvider
import org.jetbrains.exposed.dao.id.IdTable
import org.jetbrains.exposed.sql.vendors.FunctionProvider
import org.jetbrains.exposed.sql.vendors.currentDialect
import java.math.BigDecimal

fun ExpressionWithColumnType<*>.count() : Function<Int> = Count(this)

fun Column<*>.countDistinct() : Function<Int> = Count(this, true)

fun<T:Comparable<T>, S:T?> ExpressionWithColumnType<in S>.min()  : ExpressionWithColumnType<T?> = Min<T, S>(this, this.columnType)

fun<T:Comparable<T>, S:T?> ExpressionWithColumnType<in S>.max() : ExpressionWithColumnType<T?> = Max<T, S>(this, this.columnType)

/**
 * Calculates the average value. Typed to BigDecimal because some DBMS return floating point values for AVG, even if column an integral type
 * See examples [here](https://www.w3resource.com/sql/aggregate-functions/avg-function.php)
 */
fun<T:Comparable<T>, S:T?> ExpressionWithColumnType<in S>.avg(scale: Int = 2)  : ExpressionWithColumnType<BigDecimal?> = Avg<T, S>(this, scale)

fun<T:Any?> ExpressionWithColumnType<T>.stdDevPop(scale: Int = 2) = StdDevPop(this, scale)

fun<T:Any?> ExpressionWithColumnType<T>.stdDevSamp(scale: Int = 2) = StdDevSamp(this, scale)

fun<T:Any?> ExpressionWithColumnType<T>.varPop(scale: Int = 2) = VarPop(this, scale)

fun<T:Any?> ExpressionWithColumnType<T>.varSamp(scale: Int = 2) = VarSamp(this, scale)

fun<T:Any?> ExpressionWithColumnType<T>.sum() = Sum(this, this.columnType)

fun<R:Any> Expression<*>.castTo(columnType: IColumnType) = Cast<R>(this, columnType)

fun<T:String?> Expression<T>.substring(start: Int, length: Int) : Function<T> =
        Substring(this, LiteralOp(IntegerColumnType(), start), LiteralOp(IntegerColumnType(), length))

fun<T:String?> Expression<T>.trim() : Function<T> = Trim(this)

fun<T:String?> Expression<T>.lowerCase() : Function<T> = LowerCase(this)
fun<T:String?> Expression<T>.upperCase() : Function<T> = UpperCase(this)

fun Sequence.nextVal() : Function<Int> = NextVal(this)

fun<T:Any?> ExpressionWithColumnType<T>.function(functionName: String) : Function<T?> = CustomFunction(functionName, columnType, this)
fun CustomStringFunction(functionName: String, vararg params: Expression<*>) = CustomFunction<String?>(functionName, VarCharColumnType(), *params)
fun CustomLongFunction(functionName: String, vararg params: Expression<*>) = CustomFunction<Long?>(functionName, LongColumnType(), *params)

fun <T : String?> Expression<T>.groupConcat(separator: String? = null, distinct: Boolean = false, orderBy: Pair<Expression<*>,SortOrder>): GroupConcat<T> =
        GroupConcat(this, separator, distinct, orderBy)

fun <T : String?> Expression<T>.groupConcat(separator: String? = null, distinct: Boolean = false, orderBy: Array<Pair<Expression<*>,SortOrder>> = emptyArray()): GroupConcat<T> =
        GroupConcat(this, separator, distinct, *orderBy)


object SqlExpressionBuilder {
    fun <T, S:T?, E:ExpressionWithColumnType<S>, R:T> coalesce(expr: E, alternate: ExpressionWithColumnType<out T>) : ExpressionWithColumnType<R> =
            Coalesce(expr, alternate)

    fun case(value: Expression<*>? = null) = Case(value)

    fun<T, S:T?> ExpressionWithColumnType<in S>.wrap(value: T): Expression<T> = QueryParameter(value, columnType)

    infix fun <T> ExpressionWithColumnType<T>.eq(t: T) : Op<Boolean> {
        if (t == null) {
            return isNull()
        }
        return EqOp(this, wrap(t))
    }

    infix fun <T:Comparable<T>, E:EntityID<T>?> ExpressionWithColumnType<E>.eq(t: T?) : Op<Boolean> {
        if (t == null) {
            return isNull()
        }
        @Suppress("UNCHECKED_CAST") val table = (columnType as EntityIDColumnType<*>).idColumn.table as IdTable<T>
        val entityID = EntityID(t, table)
        return EqOp(this, wrap(entityID))
    }

    infix fun<T, S1: T?, S2: T?> Expression<in S1>.eq(other: Expression<in S2>) : Op<Boolean> = EqOp(this, other)

    infix fun <T> ExpressionWithColumnType<T>.neq(other: T): Op<Boolean> {
        if (other == null) {
            return isNotNull()
        }

        return NeqOp(this, wrap(other))
    }

    infix fun <T:Comparable<T>> ExpressionWithColumnType<EntityID<T>>.neq(t: T?) : Op<Boolean> {
        if (t == null) {
            return isNotNull()
        }
        return NeqOp(this, wrap(t))
    }

    infix fun <T, S1: T?, S2: T?> Expression<in S1>.neq(other: Expression<in S2>) : Op<Boolean> = NeqOp(this, other)

    fun<T> ExpressionWithColumnType<T>.isNull(): Op<Boolean> = IsNullOp(this)

    fun<T> ExpressionWithColumnType<T>.isNotNull(): Op<Boolean> = IsNotNullOp(this)

    infix fun<T:Comparable<T>, S: T?> ExpressionWithColumnType<in S>.less(t: T) : Op<Boolean> = LessOp(this, wrap(t))

    @JvmName("lessEntityID")
    infix fun<T:Comparable<T>> ExpressionWithColumnType<EntityID<T>>.less(t: T) : Op<Boolean> = LessOp(this, wrap(t))

    fun<T:Comparable<T>, S: T?> ExpressionWithColumnType<in S>.less(other: Expression<in S>) = LessOp(this, other)

    infix fun<T:Comparable<T>, S: T?> ExpressionWithColumnType<in S>.lessEq(t: T) : Op<Boolean> = LessEqOp(this, wrap(t))

    @JvmName("lessEqEntityID")
    infix fun<T:Comparable<T>> ExpressionWithColumnType<EntityID<T>>.lessEq(t: T) : Op<Boolean> = LessEqOp(this, wrap(t))

    fun<T:Comparable<T>, S: T?> ExpressionWithColumnType<in S>.lessEq(other: Expression<in S>) : Op<Boolean> = LessEqOp(this, other)

    infix fun<T:Comparable<T>, S: T?> ExpressionWithColumnType<in S>.greater(t: T) : Op<Boolean> = GreaterOp(this, wrap(t))

    @JvmName("greaterEntityID")
    infix fun<T:Comparable<T>> ExpressionWithColumnType<EntityID<T>>.greater(t: T) : Op<Boolean> = GreaterOp(this, wrap(t))

    fun<T:Comparable<T>, S: T?> ExpressionWithColumnType<in S>.greater(other: Expression<in S>) : Op<Boolean> = GreaterOp(this, other)

    infix fun<T:Comparable<T>, S: T?> ExpressionWithColumnType<in S>.greaterEq(t: T) : Op<Boolean> = GreaterEqOp(this, wrap(t))

    @JvmName("greaterEqEntityID")
    infix fun<T:Comparable<T>> ExpressionWithColumnType<EntityID<T>>.greaterEq(t: T) : Op<Boolean> = GreaterEqOp(this, wrap(t))

    fun<T:Comparable<T>, S: T?> ExpressionWithColumnType<in S>.greaterEq(other: Expression<in S>) : Op<Boolean> = GreaterEqOp(this, other)

    operator fun<T, S: T> ExpressionWithColumnType<T>.plus(other: Expression<S>) : ExpressionWithColumnType<T> = PlusOp(this, other, columnType)

    operator fun<T> ExpressionWithColumnType<T>.plus(t: T) : ExpressionWithColumnType<T> = PlusOp(this, wrap(t), columnType)

    operator fun<T, S: T> ExpressionWithColumnType<T>.minus(other: Expression<S>) : ExpressionWithColumnType<T> = MinusOp(this, other, columnType)

    operator fun<T> ExpressionWithColumnType<T>.minus(t: T) : ExpressionWithColumnType<T> = MinusOp(this, wrap(t), columnType)

    operator fun<T, S: T> ExpressionWithColumnType<T>.times(other: Expression<S>) : ExpressionWithColumnType<T> = TimesOp(this, other, columnType)

    operator fun<T> ExpressionWithColumnType<T>.times(t: T) : ExpressionWithColumnType<T> = TimesOp(this, wrap(t), columnType)

    operator fun<T, S: T> ExpressionWithColumnType<T>.div(other: Expression<S>) : ExpressionWithColumnType<T> = DivideOp(this, other, columnType)

    operator fun<T> ExpressionWithColumnType<T>.div(t: T) : ExpressionWithColumnType<T> = DivideOp(this, wrap(t), columnType)

    operator fun<T:Number?, S: Number> ExpressionWithColumnType<T>.rem(other: Expression<S>) : ExpressionWithColumnType<T> = ModOp(this, other, columnType)

    operator fun<T:Number?, S: T> ExpressionWithColumnType<T>.rem(t: S) : ExpressionWithColumnType<T> = ModOp(this, wrap(t), columnType)

    infix fun<T:Number?, S: Number> ExpressionWithColumnType<T>.mod(other: Expression<S>) : ExpressionWithColumnType<T> = this % other

    infix fun<T:Number?, S: T> ExpressionWithColumnType<T>.mod(t: S) : ExpressionWithColumnType<T> = this % t

    infix fun<T:String?> ExpressionWithColumnType<T>.like(pattern: String): Op<Boolean> = LikeOp(this, QueryParameter(pattern, columnType))

    @JvmName("likeWithEntityID")
    infix fun ExpressionWithColumnType<EntityID<String>>.like(pattern: String): Op<Boolean> = LikeOp(this, QueryParameter(pattern, columnType))

    infix fun<T:String?> ExpressionWithColumnType<T>.notLike(pattern: String): Op<Boolean> = NotLikeOp(this, QueryParameter(pattern, columnType))

    @JvmName("notLikeWithEntityID")
    infix fun ExpressionWithColumnType<EntityID<String>>.notLike(pattern: String): Op<Boolean> = NotLikeOp(this, QueryParameter(pattern, columnType))

    /*
     * Function will apply case-sensitive regexp function
     */
    infix fun<T:String?> ExpressionWithColumnType<T>.regexp(pattern: String): Op<Boolean> = RegexpOp(this, QueryParameter(pattern, columnType), true)

    fun<T:String?> ExpressionWithColumnType<T>.regexp(pattern: Expression<String>, caseSensitive: Boolean = true): Op<Boolean> = RegexpOp(this, pattern, caseSensitive)

    @Deprecated("Use not(RegexpOp()) instead", level = DeprecationLevel.ERROR)
    infix fun<T:String?> ExpressionWithColumnType<T>.notRegexp(pattern: String): Op<Boolean> = TODO()

    infix fun<T> ExpressionWithColumnType<T>.inList(list: Iterable<T>): Op<Boolean> = InListOrNotInListOp(this, list, isInList = true)

    @Suppress("UNCHECKED_CAST")
    @JvmName("inListIds")
    infix fun<T:Comparable<T>> Column<EntityID<T>>.inList(list: Iterable<T>): Op<Boolean> {
        val idTable = (columnType as EntityIDColumnType<T>).idColumn.table as IdTable<T>
        return inList(list.map { EntityIDFunctionProvider.createEntityID(it, idTable) })
    }

    infix fun<T> ExpressionWithColumnType<T>.notInList(list: Iterable<T>): Op<Boolean> = InListOrNotInListOp(this, list, isInList = false)

    @Suppress("UNCHECKED_CAST")
    @JvmName("notInListIds")
    infix fun<T:Comparable<T>> Column<EntityID<T>>.notInList(list: Iterable<T>): Op<Boolean> {
        val idTable = (columnType as EntityIDColumnType<T>).idColumn.table as IdTable<T>
        return notInList(list.map { EntityIDFunctionProvider.createEntityID(it, idTable) })
    }

    infix fun<T> ExpressionWithColumnType<T>.inSubQuery(query: Query): Op<Boolean> = InSubQueryOp(this, query)

    infix fun <T> ExpressionWithColumnType<T>.notInSubQuery(query: Query): Op<Boolean> = NotInSubQueryOp(this, query)

    @Suppress("UNCHECKED_CAST")
    fun<T, S: T?> ExpressionWithColumnType<S>.asLiteral(value: T) = when (value) {
        is Boolean -> booleanLiteral(value)
        is Int -> intLiteral(value)
        is Long -> longLiteral(value)
        is String -> stringLiteral(value)
        is ByteArray -> stringLiteral(value.toString(Charsets.UTF_8))
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

    fun <T:String?> concat(vararg expr: Expression<T>) = Concat("", *expr)
    fun <T:String?> concat(separator: String = "", expr: List<Expression<T>>) = Concat(separator, *expr.toTypedArray())
}
