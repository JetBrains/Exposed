package kotlin.sql

open class Column<T>(val table: Table, val name: String, val columnType: ColumnType) : Field<T>() {
    var referee: PKColumn<T>? = null

    fun eq(other: Expression): Op {
        return EqOp(this, other)
    }

    fun eq(other: T): Op {
        if (other == null)
        {
            if (!columnType.nullable) throw RuntimeException("Attempt to compare non-nulable column value with null")
            return isNull()
        }
        return EqOp(this, LiteralOp(columnType, other))
    }

    fun less(other: Expression): Op {
        return LessOp(this, other)
    }

    fun less(other: T): Op {
        return LessOp(this, LiteralOp(columnType, other))
    }

    fun lessEq(other: Expression): Op {
        return LessEqOp(this, other)
    }

    fun lessEq(other: T): Op {
        return LessEqOp(this, LiteralOp(columnType, other))
    }

    fun greater(other: Expression): Op {
        return GreaterOp(this, other)
    }

    fun greater(other: T): Op {
        return GreaterOp(this, LiteralOp(columnType, other))
    }

    fun greaterEq(other: Expression): Op {
        return GreaterEqOp(this, other)
    }

    fun greaterEq(other: T): Op {
        return GreaterEqOp(this, LiteralOp(columnType, other))
    }

    fun like(other: String): Op {
        return LikeOp(this, LiteralOp(columnType, other))
    }

    fun isNull(): Op {
        return IsNullOp(this)
    }

    fun isNotNull(): Op {
        return IsNotNullOp(this)
    }

    override fun toSQL(): String {
        return Session.get().fullIdentity(this);
    }
}

class PKColumn<T>(table: Table, name: String, columnType: ColumnType) : Column<T>(table, name, columnType) {
}
