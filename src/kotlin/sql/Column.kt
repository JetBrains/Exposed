package kotlin.sql

open class Column<T>(val table: Table, val name: String, val columnType: InternalColumnType, val primaryKey: Boolean, val nullable: Boolean, val length: Int, val autoIncrement: Boolean, val references: Column<*>?) : Field<T>() {
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

    fun invoke(value: T): Pair<Column<T>, T> {
        return Pair<Column<T>, T>(this, value)
    }

    fun <B> plus(b: Column<B>): Column2<T, B> {
        return Column2<T, B>(this, b)
    }
}

class PKColumn<T>(table: Table, name: String, columnType: InternalColumnType, primaryKey: Boolean, nullable: Boolean, length: Int, autoIncrement: Boolean, references: Column<*>?) : Column<T>(table, name, columnType, primaryKey, nullable, length, autoIncrement, references) {

}

class Column2<A, B>(val a: Column<A>, val b: Column<B>) {
    fun <C> plus(c: Column<C>): Column3<A, B, C> {
        return Column3<A, B, C>(a, b, c)
    }

    fun invoke(av: A, bv: B): Array<Pair<Column<*>, *>> {
        return array(Pair(a, av), Pair(b, bv))
    }
}

class Column3<A, B, C>(val a: Column<A>, val b: Column<B>, val c: Column<C>) {
    fun <D> plus(d: Column<D>): Column4<A, B, C, D> {
        return Column4<A, B, C, D>(a, b, c, d)
    }

    fun invoke(av: A, bv: B, cv: C): Array<Pair<Column<*>, *>> {
        return array(Pair(a, av), Pair(b, bv), Pair(c, cv))
    }
}

class Column4<A, B, C, D>(val a: Column<A>, val b: Column<B>, val c: Column<C>, val d: Column<D>) {
    fun invoke(av: A, bv: B, cv: C, dv: D): Array<Pair<Column<*>, *>> {
        return array(Pair(a, av), Pair(b, bv), Pair(c, cv), Pair(d, dv))
    }
}

