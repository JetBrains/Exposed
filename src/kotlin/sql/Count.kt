package kotlin.sql

data class Count(val expr: Expression<*>): Function<Int>() {
    override fun toSQL(): String {
        return "COUNT(${expr.toSQL()})"
    }

    override val columnType: ColumnType = IntegerColumnType();
}

data class Min<T>(val expr: Expression<T>, _columnType: ColumnType): Function<T>() {
    override fun toSQL(): String {
        return "MIN(${expr.toSQL()})"
    }

    override val columnType: ColumnType = _columnType
}

data class Max<T>(val expr: Expression<T>, _columnType: ColumnType): Function<T>() {
    override fun toSQL(): String {
        return "MAX(${expr.toSQL()})"
    }

    override val columnType: ColumnType = _columnType
}

data class Sum<T>(val expr: Expression<T>, _columnType: ColumnType): Function<T>() {
    override fun toSQL(): String {
        return "SUM(${expr.toSQL()})"
    }

    override val columnType: ColumnType = _columnType
}
