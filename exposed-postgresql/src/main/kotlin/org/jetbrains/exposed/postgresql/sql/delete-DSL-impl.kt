package org.jetbrains.exposed.postgresql.sql

import org.jetbrains.exposed.sql.ColumnSet
import org.jetbrains.exposed.sql.Op
import org.jetbrains.exposed.sql.SqlExpressionBuilder

class PostgresqlDeleteReturningDSL(
    internal var returningColumnSet: ColumnSet
) {
    internal var where: Op<Boolean>? = null


    fun where(body: SqlExpressionBuilder.() -> Op<Boolean>) {
        this.where = SqlExpressionBuilder.body()
    }

    fun returning(returning: ColumnSet = this.returningColumnSet) {
        returningColumnSet = returning
    }
}