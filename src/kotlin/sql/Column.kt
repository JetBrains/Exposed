package kotlin.sql

open class Column<T>(val table: Table, val name: String, val columnType: ColumnType) : Field<T>() {
    var referee: PKColumn<T>? = null

    fun equals(other: Expression): Op {
        return EqualsOp(this, other)
    }

    fun equals(other: T): Op {
        if (other == null)
        {
            if (!columnType.nullable) throw RuntimeException("Attempt to compare non-nulable column value with null")
            return isNull()
        }
        return EqualsOp(this, LiteralOp(columnType, other))
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
