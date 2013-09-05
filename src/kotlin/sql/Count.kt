package kotlin.sql

data class Count(val column: Column<*>): Function<Int>(column) {
    override fun toSQL(): String {
        return "COUNT(${Session.get().fullIdentity(column)})"
    }

    protected override val columnType: ColumnType = IntegerColumnType();
}

data class Min<T>(val column: Column<T>): Function<T>(column) {
    override fun toSQL(): String {
        return "MIN(${Session.get().fullIdentity(column)})"
    }

    protected override val columnType: ColumnType = column.columnType
}

data class Max<T>(val column: Column<T>): Function<T>(column) {
    override fun toSQL(): String {
        return "MAX(${Session.get().fullIdentity(column)})"
    }

    protected override val columnType: ColumnType = column.columnType
}
