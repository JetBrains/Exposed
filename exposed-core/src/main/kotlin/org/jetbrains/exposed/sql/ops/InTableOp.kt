package org.jetbrains.exposed.sql.ops

import org.jetbrains.exposed.sql.ComplexExpression
import org.jetbrains.exposed.sql.Expression
import org.jetbrains.exposed.sql.Op
import org.jetbrains.exposed.sql.QueryBuilder
import org.jetbrains.exposed.sql.Table

/**
 * Represents an SQL operator that checks if [expr] is equal to any element from a single-column [table].
 *
 * **Note** This operation is only supported by MySQL, PostgreSQL, and H2 dialects.
 */
class InTableOp(
    /** Returns the expression compared to each element in the table's column. */
    val expr: Expression<*>,
    /** Returns the single-column table to check against. */
    val table: Table,
    /** Returns `false` if the check is inverted, `true` otherwise. */
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
