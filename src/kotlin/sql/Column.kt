package kotlin.sql

open class Column<T>(val table: Table, val name: String, val columnType: ColumnType, val nullable: Boolean) : Field<T>() {
    fun equals(other: Expression): Op {
        return EqualsOp(this, other)
    }

    fun equals(other: Any): Op {
        return EqualsOp(this, LiteralOp(other))
    }

    fun isNull(): Op {
        return IsNullOp(this)
    }

    override fun toSQL(): String {
        return Session.get().fullIdentity(this);
    }

    fun invoke(value: T): Pair<Column<T>, T> {
        return Pair<Column<T>, T>(this, value)
    }

    fun <B> plus(b: Column<B>): Column2<T, B> {
        return Column2<T, B>(this, b)
    }
}

class Column2<A, B>(val a: Column<A>, val b: Column<B>) {
    fun <C> plus(c: Column<C>): Column3<A, B, C> {
        return Column3<A, B, C>(a, b, c)
    }
}

class Column3<A, B, C>(val a: Column<A>, val b: Column<B>, val c: Column<C>) {
    fun <D> plus(d: Column<D>): Column4<A, B, C, D> {
        return Column4<A, B, C, D>(a, b, c, d)
    }
}

class Column4<A, B, C, D>(val a: Column<A>, val b: Column<B>, val c: Column<C>, val d: Column<D>) {
}

