package kotlin.sql

fun<T> ExpressionWithColumnType<T>.plus(other: Expression<T>) : ExpressionWithColumnType<T> {
    return PlusOp (this, other, columnType)
}

fun<T> ExpressionWithColumnType<T>.plus(other: T) : ExpressionWithColumnType<T> {
    return PlusOp (this, LiteralOp(columnType, other), columnType)
}

fun<T> ExpressionWithColumnType<T>.minus(other: Expression<T>) : ExpressionWithColumnType<T> {
    return MinusOp (this, other, columnType)
}

fun<T> ExpressionWithColumnType<T>.minus(other: T) : ExpressionWithColumnType<T> {
    return MinusOp (this, LiteralOp(columnType, other), columnType)
}

fun<T> ExpressionWithColumnType<T>.times(other: Expression<T>) : ExpressionWithColumnType<T> {
    return TimesOp (this, other, columnType)
}

fun<T> ExpressionWithColumnType<T>.times(other: T) : ExpressionWithColumnType<T> {
    return TimesOp (this, LiteralOp(columnType, other), columnType)
}

fun<T> ExpressionWithColumnType<T>.div(other: Expression<T>) : ExpressionWithColumnType<T> {
    return DivideOp (this, other, columnType)
}

fun<T> ExpressionWithColumnType<T>.div(other: T) : ExpressionWithColumnType<T> {
    return DivideOp (this, LiteralOp(columnType, other), columnType)
}

class PlusOp<out T>(val expr1: Expression<T>, val expr2: Expression<T>, override val columnType: ColumnType): ExpressionWithColumnType<T> {
    override fun toSQL():String {
        return expr1.toSQL() + "+" + expr2.toSQL()
    }
}

class MinusOp<out T>(val expr1: Expression<T>, val expr2: Expression<T>, override val columnType: ColumnType): ExpressionWithColumnType<T> {
    override fun toSQL():String {
        return expr1.toSQL() + "-" + expr2.toSQL()
    }
}

class TimesOp<out T>(val expr1: Expression<T>, val expr2: Expression<T>, override val columnType: ColumnType): ExpressionWithColumnType<T> {
    override fun toSQL():String {
        return "(${expr1.toSQL()}) * (${expr2.toSQL()})"
    }
}

class DivideOp<out T>(val expr1: Expression<T>, val expr2: Expression<T>, override val columnType: ColumnType): ExpressionWithColumnType<T> {
    override fun toSQL():String {
        return "(${expr1.toSQL()}) / (${expr2.toSQL()})"
    }

    fun toString(): String {
        throw RuntimeException("!")
    }
}
