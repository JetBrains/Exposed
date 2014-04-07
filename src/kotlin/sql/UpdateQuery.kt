package kotlin.sql

import java.util.LinkedHashMap

class UpdateQuery(val table: Table, val limit: Int?, val where: Op<Boolean>) {
    val values = LinkedHashMap<Column<*>, Any>()

    fun <T> set(column: Column<T>, value: T) {
        if (values containsKey column) {
            error("$column is already initialized")
        }
        values[column] = value
    }

    fun execute(session: Session): Int {
        if (!values.isEmpty()) {
            val builder = QueryBuilder(true)
            var sql = StringBuilder("UPDATE ${session.identity(table)}")
            var c = 0;
            sql.append(" SET ")
            for ((col, value) in values) {
                sql.append(session.identity(col))
                   .append("=")

                when {
                    value != null && javaClass<Expression<Any>>().isAssignableFrom(value.javaClass) -> sql.append((value as Expression<Any>).toSQL(builder))
                    else -> sql.append(builder.registerArgument(value, col.columnType))
                }

                c++
                if (c < values.size()) {
                    sql.append(", ")
                }
            }
            sql.append(" WHERE " + where.toSQL(builder))
            if (limit != null) {
                sql.append(" LIMIT ").append(limit)
            }
            session.logger.log(sql)

            val statement = sql.toString()
            return builder.executeUpdate(session, statement)
        }
        return 0
    }
}
