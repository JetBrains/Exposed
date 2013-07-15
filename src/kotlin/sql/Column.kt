package kotlin.sql

open class Column<T>(val table: Table, val name: String, val columnType: ColumnType, val nullable: Boolean) : Expression {
    fun equals(other: Expression): Op {
        return EqualsOp(this, other)
    }

    fun equals(other: Any): Op {
        return EqualsOp(this, LiteralOp(other))
    }

    fun isNull(): Op {
        return IsNullOp(this)
    }

    open fun toString(): String {
        return table.tableName + "." + name;
    }

    fun invoke(value: T): Pair<Column<T>, T> {
        return Pair<Column<T>, T>(this, value)
    }
}