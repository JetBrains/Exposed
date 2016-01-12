package org.jetbrains.exposed.sql

import java.util.*

class UpdateQuery(val target: ((Transaction)->String), val limit: Int?, val where: Op<Boolean>? = null) {
    val values: MutableMap<Column<*>, Any?> = LinkedHashMap()

    operator fun <T, S: T> set(column: Column<T>, value: S?) {
        if (values.containsKey(column)) {
            error("$column is already initialized")
        }
        if (!column.columnType.nullable && value == null) {
            error("Trying to set null to not nullable column $column")
        }
        values[column] = value
    }

    fun <T, S: T> update(column: Column<T>, value: Expression<S>) {
        if (values.containsKey(column)) {
            error("$column is already initialized")
        }
        values[column] = value
    }

    fun execute(transaction: Transaction): Int {
        if (!values.isEmpty()) {
            val builder = QueryBuilder(true)
            var sqlStatement = StringBuilder("UPDATE ${target(transaction)}")
            var c = 0;
            sqlStatement.append(" SET ")
            for ((col, value) in values) {
                sqlStatement.append(col.toSQL(builder))
                   .append("=")

                when (value) {
                    is Expression<*> -> sqlStatement.append(value.toSQL(builder))
                    else -> sqlStatement.append(builder.registerArgument(value, col.columnType))
                }

                c++
                if (c < values.size) {
                    sqlStatement.append(", ")
                }
            }
            where?.let { sqlStatement.append(" WHERE " + it.toSQL(builder)) }
            if (limit != null) {
                sqlStatement.append(" LIMIT ").append(limit)
            }

            val statement = sqlStatement.toString()
            return builder.executeUpdate(transaction, statement)
        }
        return 0
    }
}
