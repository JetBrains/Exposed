package org.jetbrains.exposed.sql.ops

import org.jetbrains.exposed.sql.*
import kotlin.reflect.KClass

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
class AllAnyFromArrayOp<T : Any>(
    isAny: Boolean,
    array: Array<T>,
    private val klass: KClass<T>,
    private val delegateType: ColumnType? = null
) : AllAnyFromBaseOp<T, Array<T>>(isAny, array) {
    override fun QueryBuilder.registerSubSearchArgument(subSearch: Array<T>) {
        // emptyArray() without type info generates ARRAY[]
        val default = if (subSearch.isEmpty()) TextColumnType() else null
        registerArgument(
            ArrayColumnType(delegateType ?: resolveColumnType(klass, default)),
            subSearch
        )
    }
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

/**
 * Represents an SQL operator that checks a value, based on the preceding comparison operator,
 * against a collection of values returned by the provided expression.
 *
 * **Note** This operation is only supported by PostgreSQL and H2 dialects.
 */
class AllAnyFromExpressionOp<E, T : List<E>?>(
    isAny: Boolean,
    expression: Expression<T>
) : AllAnyFromBaseOp<E, Expression<T>>(isAny, expression) {
    override fun QueryBuilder.registerSubSearchArgument(subSearch: Expression<T>) {
        append(subSearch)
    }
}
