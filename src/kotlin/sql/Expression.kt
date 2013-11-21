package kotlin.sql

import java.util.ArrayList

class QueryBuilder(val prepared: Boolean) {
    val args = ArrayList<Any?>()

    fun <T> registerArgument(arg: T, sqlType: ColumnType): String {
        if (prepared && isSupported(sqlType)) {
            args.add(arg)
            return "?"
        }
        else {
            return sqlType.valueToString(arg)
        }
    }

    private fun isSupported(sqlType: ColumnType): Boolean {
        return sqlType is StringColumnType || sqlType is IntegerColumnType || sqlType is LongColumnType /* TODO: DateTime*/
    }
}

trait Expression<out T> {
    fun toSQL(queryBuilder: QueryBuilder): String
}

trait ExpressionWithColumnType<out T> : Expression<T> {
    // used for operations with literals
    val columnType: ColumnType;
}
