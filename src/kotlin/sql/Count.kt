package kotlin.sql

class Count(val column: Column<*>): Function<Int>() {
    override fun toSQL(): String {
        return "COUNT(${Session.get().identity(column)})"
    }
}