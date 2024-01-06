package org.jetbrains.exposed.sql.ops

import org.jetbrains.exposed.sql.AbstractQuery
import org.jetbrains.exposed.sql.Op
import org.jetbrains.exposed.sql.QueryBuilder
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.UntypedAndUnsizedArrayColumnType

/**
 * Represents an SQL operator that checks a value, based on the preceding comparison operator,
 * against elements returned by [subSearch].
 */
abstract class AllAnyFromBaseOp<T, SubSearch>(
    /** Returns `true` if at least 1 comparison must evaluate to `true`, or `false` if all comparisons must be `true`. **/
    val isAny: Boolean,
    /** Returns the source of elements to be compared against. */
    val subSearch: SubSearch
) : Op<T>() {
    override fun toQueryBuilder(queryBuilder: QueryBuilder) = queryBuilder {
        +(if (isAny) "ANY" else "ALL")
        +" ("
        registerSubSearchArgument(subSearch)
        +')'
    }

    /** Processes the [subSearch] value for inclusion in the generated query. */
    abstract fun QueryBuilder.registerSubSearchArgument(subSearch: SubSearch)
}

/**
 * Represents an SQL operator that checks a value, based on the preceding comparison operator,
 * against results returned by a query.
 */
class AllAnyFromSubQueryOp<T>(
    isAny: Boolean,
    subQuery: AbstractQuery<*>
) : AllAnyFromBaseOp<T, AbstractQuery<*>>(isAny, subQuery) {
    override fun QueryBuilder.registerSubSearchArgument(subSearch: AbstractQuery<*>) {
        subSearch.prepareSQL(this)
    }
}

/**
 * Represents an SQL operator that checks a value, based on the preceding comparison operator,
 * against an array of values.
 *
 * **Note** This operation is only supported by PostgreSQL and H2 dialects.
 */
class AllAnyFromArrayOp<T>(isAny: Boolean, array: Array<T>) : AllAnyFromBaseOp<T, Array<T>>(isAny, array) {
    override fun QueryBuilder.registerSubSearchArgument(subSearch: Array<T>) =
        registerArgument(UntypedAndUnsizedArrayColumnType, subSearch)
}

/**
 * Represents an SQL operator that checks a value, based on the preceding comparison operator,
 * against elements in a single-column table.
 *
 * **Note** This operation is only supported by MySQL, PostgreSQL, and H2 dialects.
 */
class AllAnyFromTableOp<T>(isAny: Boolean, table: Table) : AllAnyFromBaseOp<T, Table>(isAny, table) {
    override fun QueryBuilder.registerSubSearchArgument(subSearch: Table) {
        +"TABLE "
        +subSearch.tableName
    }
}
