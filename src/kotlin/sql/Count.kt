package kotlin.sql

data class Count(val column: Column<*>): Function<Int>(column) {
    override fun toSQL(): String {
        return "COUNT(${Session.get().fullIdentity(column)})"
    }
}
