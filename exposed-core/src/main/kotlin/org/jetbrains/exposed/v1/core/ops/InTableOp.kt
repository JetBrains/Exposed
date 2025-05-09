package org.jetbrains.exposed.v1.core.ops

import org.jetbrains.exposed.v1.core.ComplexExpression
import org.jetbrains.exposed.v1.core.Expression
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.QueryBuilder
import org.jetbrains.exposed.v1.core.Table

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
