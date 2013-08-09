package kotlin.sql

open class Column<T>(val table: Table, val name: String, val columnType: ColumnType) : Field<T>() {
    var referee: PKColumn<T>? = null

    fun equals(other: Expression): Op {
        return EqualsOp(this, other)
    }

    fun equals(other: T): Op {
        return EqualsOp(this, LiteralOp(other))
    }

    fun like(other: String): Op {
        return LikeOp(this, LiteralOp(other))
    }

    fun isNull(): Op {
        return IsNullOp(this)
    }

    override fun toSQL(): String {
        return Session.get().fullIdentity(this);
    }
}

class PKColumn<T>(table: Table, name: String, columnType: ColumnType) : Column<T>(table, name, columnType) {

}
