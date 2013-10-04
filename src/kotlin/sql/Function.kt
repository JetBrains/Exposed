package kotlin.sql

abstract class Function<T>(): Field<T>(), ExpressionWithColumnType<T> {
    fun eq(t: T) : EqOp<T> {
        return EqOp<T> (this, LiteralOp(columnType, t))
    }

    fun eq(otherFunc: Function<T>) : EqOp<T> {
        return EqOp<T> (this, otherFunc)
    }

    fun less(t: T) : LessOp<T> {
        return LessOp<T> (this, LiteralOp(columnType, t))
    }

    fun less(otherFunc: Function<T>) : LessOp<T> {
        return LessOp<T> (this, otherFunc)
    }

    fun lessEq(t: T) : LessEqOp<T> {
        return LessEqOp<T> (this, LiteralOp(columnType, t))
    }

    fun lessEq(otherFunc: Function<T>) : LessEqOp<T> {
        return LessEqOp<T> (this, otherFunc)
    }

    fun greater(t: T) : GreaterOp<T> {
        return GreaterOp<T> (this, LiteralOp(columnType, t))
    }

    fun greater(otherFunc: Function<T>) : GreaterOp<T> {
        return GreaterOp (this, otherFunc)
    }

    fun greaterEq(t: T) : GreaterEqOp<T> {
        return GreaterEqOp (this, LiteralOp(columnType, t))
    }

    fun greaterEq(otherFunc: Function<T>) : GreaterEqOp<T> {
        return GreaterEqOp (this, otherFunc)
    }
}