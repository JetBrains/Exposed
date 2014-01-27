package kotlin.sql


open class Column<out T>(val table: Table, val name: String, override val columnType: ColumnType) : ExpressionWithColumnType<T> {
    var referee: Column<*>? = null
    var defaultValue: T? = null

    override fun toSQL(queryBuilder: QueryBuilder): String {
        return Session.get().fullIdentity(this);
    }
}

class PKColumn<T>(table: Table, name: String, columnType: ColumnType) : Column<T>(table, name, columnType) {
}
