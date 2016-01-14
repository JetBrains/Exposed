package org.jetbrains.exposed.sql

class UpdateQuery(val target: ((Transaction)->String), val limit: Int?, val where: Op<Boolean>? = null): UpdateBuilder() {
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
