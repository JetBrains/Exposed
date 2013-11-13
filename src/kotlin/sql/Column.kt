package kotlin.sql


open class Column<T>(val table: Table, val name: String, override val columnType: ColumnType) : Field<T>(), ExpressionWithColumnType<T> {
    var referee: PKColumn<T>? = null
    var defaultValue: T? = null

    fun eq(other: Expression<T>): Op<Boolean> {
        return EqOp(this, other)
    }

    fun eq(other: T): Op<Boolean> {
        if (other == null)
        {
            if (!columnType.nullable) error("Attempt to compare non-nulable column value with null")
            return isNull()
        }
        return EqOp(this, LiteralOp(columnType, other))
    }

    fun neq(other: T): Op<Boolean> {
        if (other == null)
        {
            if (!columnType.nullable) error("Attempt to compare non-nulable column value with null")
            return isNull()
        }
        return NeqOp(this, LiteralOp(columnType, other))
    }

    fun less(other: Expression<T>): Op<Boolean> {
        return LessOp(this, other)
    }

    fun less(other: T): Op<Boolean> {
        return LessOp(this, LiteralOp(columnType, other))
    }

    fun lessEq(other: Expression<T>): Op<Boolean> {
        return LessEqOp(this, other)
    }

    fun lessEq(other: T): Op<Boolean> {
        return LessEqOp(this, LiteralOp(columnType, other))
    }

    fun greater(other: Expression<T>): Op<Boolean> {
        return GreaterOp(this, other)
    }

    fun greater(other: T): Op<Boolean> {
        return GreaterOp(this, LiteralOp(columnType, other))
    }

    fun greaterEq(other: Expression<T>): Op<Boolean> {
        return GreaterEqOp(this, other)
    }

    fun greaterEq(other: T): Op<Boolean> {
        return GreaterEqOp(this, LiteralOp(columnType, other))
    }

    fun like(other: String): Op<Boolean> {
        return LikeOp(this, LiteralOp(columnType, other))
    }

    fun isNull(): Op<Boolean> {
        return IsNullOp(this)
    }

    fun isNotNull(): Op<Boolean> {
        return IsNotNullOp(this)
    }

    override fun toSQL(queryBuilder: QueryBuilder): String {
        return Session.get().fullIdentity(this);
    }
}

class PKColumn<T>(table: Table, name: String, columnType: ColumnType) : Column<T>(table, name, columnType) {
}
