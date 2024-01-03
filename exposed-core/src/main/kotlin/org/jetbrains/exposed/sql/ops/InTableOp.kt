package org.jetbrains.exposed.sql.ops

import org.jetbrains.exposed.sql.ComplexExpression
import org.jetbrains.exposed.sql.Expression
import org.jetbrains.exposed.sql.Op
import org.jetbrains.exposed.sql.QueryBuilder
import org.jetbrains.exposed.sql.Table

/** This function is only supported by PostgreSQL and H2 dialects. */
class InTableOp(
    val expr: Expression<*>,
    /** the table to check against. */
    val table: Table,
    /** Returns `true` if the check is inverted, `false` otherwise. */
    val isInTable: Boolean = true
) : Op<Boolean>(), ComplexExpression {
    override fun toQueryBuilder(queryBuilder: QueryBuilder) = queryBuilder {
        +expr
        +" "
        +if (isInTable) "" else "NOT "
        +"IN ("
        +"TABLE "
        +table.tableName
        +')'
    }
}
