package kotlin.sql

fun Column<*>.count(): Count {
    return Count(this)
}

fun Column<*>.countDistinct(): Count {
    return Count(this, true)
}

fun<T> Column<T>.min(): Min<T> {
    return Min(this, this.columnType)
}

fun<T> Column<T>.max(): Max<T> {
    return Max(this, this.columnType)
}

fun<T> Column<T>.sum(): Sum<T> {
    return Sum(this, this.columnType)
}

fun Column<String?>.substring(start: Int, length: Int): Substring {
    return Substring(this, LiteralOp(IntegerColumnType(), start), LiteralOp(IntegerColumnType(), length))
}

fun <T> Column<T>.distinct(): Distinct<T> {
    return Distinct(this, this.columnType)
}

object SqlExpressionBuilder {
    fun case(value: Expression<*>? = null) : Case {
        return Case(value)
    }

    public fun<T> ExpressionWithColumnType<T>.wrap(value: T): Expression<T> {
        return QueryParameter(value, columnType)
    }

    public fun<T> ExpressionWithColumnType<T>.eq(t: T) : Op<Boolean> {
        if (t == null) {
            if (!columnType.nullable) error("Attempt to compare non-nulable column value with null")
            return isNull()
        }
        return EqOp<T> (this, wrap(t))
    }


    public fun<T> Expression<T>.eq(other: Expression<T>) : Op<Boolean> {
        return EqOp<T> (this, other)
    }

    public fun<T> ExpressionWithColumnType<T>.eq(other: Expression<T>) : Op<Boolean> {
        return EqOp<T> (this, other)
    }

    public fun<T> ExpressionWithColumnType<T>.neq(other: T): Op<Boolean> {
        if (other == null) {
            if (!columnType.nullable) error("Attempt to compare non-nulable column value with null")
            return isNotNull()
        }

        return NeqOp(this, wrap(other))
    }

    public fun<T> ExpressionWithColumnType<T>.neq(other: Expression<T>) : Op<Boolean> {
        return NeqOp<T> (this, other)
    }

    public fun<T> ExpressionWithColumnType<T>.isNull(): Op<Boolean> {
        return IsNullOp(this)
    }

    public fun<T> ExpressionWithColumnType<T>.isNotNull(): Op<Boolean> {
        return IsNotNullOp(this)
    }

    public fun<T> ExpressionWithColumnType<T>.less(t: T) : LessOp<T> {
        return LessOp<T> (this, wrap(t))
    }

    public fun<T> ExpressionWithColumnType<T>.less(other: Expression<T>) : LessOp<T> {
        return LessOp<T> (this, other)
    }

    public fun<T> ExpressionWithColumnType<T>.lessEq(t: T) : LessEqOp<T> {
        return LessEqOp<T> (this, wrap(t))
    }

    public fun<T> ExpressionWithColumnType<T>.lessEq(other: Expression<T>) : LessEqOp<T> {
        return LessEqOp<T> (this, other)
    }

    public fun<T> ExpressionWithColumnType<T>.greater(t: T) : GreaterOp<T> {
        return GreaterOp<T> (this, wrap(t))
    }

    public fun<T> ExpressionWithColumnType<T>.greater(other: Expression<T>) : GreaterOp<T> {
        return GreaterOp (this, other)
    }

    public fun<T> ExpressionWithColumnType<T>.greaterEq(t: T) : GreaterEqOp<T> {
        return GreaterEqOp (this, wrap(t))
    }

    public fun<T> ExpressionWithColumnType<T>.greaterEq(other: Expression<T>) : GreaterEqOp<T> {
        return GreaterEqOp (this, other)
    }
    public fun<T> ExpressionWithColumnType<T>.plus(other: Expression<T>) : ExpressionWithColumnType<T> {
        return PlusOp (this, other, columnType)
    }

    public fun<T> ExpressionWithColumnType<T>.plus(t: T) : ExpressionWithColumnType<T> {
        return PlusOp (this, wrap(t), columnType)
    }

    public fun<T> ExpressionWithColumnType<T>.minus(other: Expression<T>) : ExpressionWithColumnType<T> {
        return MinusOp (this, other, columnType)
    }

    public fun<T> ExpressionWithColumnType<T>.minus(t: T) : ExpressionWithColumnType<T> {
        return MinusOp (this, wrap(t), columnType)
    }

    public fun<T> ExpressionWithColumnType<T>.times(other: Expression<T>) : ExpressionWithColumnType<T> {
        return TimesOp (this, other, columnType)
    }

    public fun<T> ExpressionWithColumnType<T>.times(t: T) : ExpressionWithColumnType<T> {
        return TimesOp (this, wrap(t), columnType)
    }

    public fun<T> ExpressionWithColumnType<T>.div(other: Expression<T>) : ExpressionWithColumnType<T> {
        return DivideOp (this, other, columnType)
    }

    public fun<T> ExpressionWithColumnType<T>.div(t: T) : ExpressionWithColumnType<T> {
        return DivideOp (this, wrap(t), columnType)
    }

    public fun<T> ExpressionWithColumnType<T>.like(other: String): Op<Boolean> {
        if (columnType !is StringColumnType) error("Like is only for strings")
        return LikeOp(this, QueryParameter(other, columnType))
    }

    public fun<T> ExpressionWithColumnType<T>.regexp(other: String): Op<Boolean> {
        if (columnType !is StringColumnType) error("Regexp is only for strings")
        return RegexpOp(this, QueryParameter(other, columnType))
    }

    public fun<T> ExpressionWithColumnType<T>.notRegexp(other: String): Op<Boolean> {
        if (columnType !is StringColumnType) error("Not regexp is only for strings")
        return NotRegexpOp(this, QueryParameter(other, columnType))
    }

    public fun<T> ExpressionWithColumnType<T>.inList(list: List<T>): Op<Boolean> {
        return InListOp(this, list)
    }
}
