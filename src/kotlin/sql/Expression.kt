package kotlin.sql

import java.util.ArrayList

class QueryBuilder(val prepared: Boolean) {
    val args = ArrayList<Any?>()

    fun <T> registerArgument(arg: T, sqlType: ColumnType): String {
        if (prepared) {
            args.add(arg)
            return "?"
        }
        else {
            return sqlType.valueToString(arg)
        }
    }
}

trait Expression<out T> {
    fun toSQL(queryBuilder: QueryBuilder): String
}

trait ExpressionWithColumnType<out T> : Expression<T> {
    // used for operations with literals
    val columnType: ColumnType;
}
