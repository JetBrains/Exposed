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
}