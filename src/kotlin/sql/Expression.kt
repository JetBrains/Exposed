package kotlin.sql

trait Expression<out T> {
    fun toSQL(): String
}

trait ExpressionWithColumnType<out T> : Expression<T> {
    // used for operations with literals
    val columnType: ColumnType;
}