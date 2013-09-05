package kotlin.sql

abstract class Function<T>(vararg val columns: Column<*>): Field<T>() {
    // used for comparison operations
    protected abstract val columnType: ColumnType;

    fun eq(t: T) : EqOp {
        return EqOp (this, LiteralOp(columnType, t))
    }

    fun eq(otherFunc: Function<T>) : EqOp {
        return EqOp (this, otherFunc)
    }

    fun less(t: T) : LessOp {
        return LessOp (this, LiteralOp(columnType, t))
    }

    fun less(otherFunc: Function<T>) : LessOp {
        return LessOp (this, otherFunc)
    }

    fun lessEq(t: T) : LessEqOp {
        return LessEqOp (this, LiteralOp(columnType, t))
    }

    fun lessEq(otherFunc: Function<T>) : LessEqOp {
        return LessEqOp (this, otherFunc)
    }

    fun greater(t: T) : GreaterOp {
        return GreaterOp (this, LiteralOp(columnType, t))
    }

    fun greater(otherFunc: Function<T>) : GreaterOp {
        return GreaterOp (this, otherFunc)
    }

    fun greaterEq(t: T) : GreaterEqOp {
        return GreaterEqOp (this, LiteralOp(columnType, t))
    }

    fun greaterEq(otherFunc: Function<T>) : GreaterEqOp {
        return GreaterEqOp (this, otherFunc)
    }
}