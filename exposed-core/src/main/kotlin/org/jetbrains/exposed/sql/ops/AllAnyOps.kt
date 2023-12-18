package org.jetbrains.exposed.sql.ops

import org.jetbrains.exposed.sql.Op
import org.jetbrains.exposed.sql.Query
import org.jetbrains.exposed.sql.QueryBuilder
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.UntypedAndUnsizedArrayColumnType

abstract class AllAnyFromBaseOp<T, SubSearch>(val isAny: Boolean, val subSearch: SubSearch) : Op<T>() {
    override fun toQueryBuilder(queryBuilder: QueryBuilder) = queryBuilder {
        +(if (isAny) "ANY" else "ALL")
        +" ("
        registerSubSearchArgument(subSearch)
        +')'
    }

    abstract fun QueryBuilder.registerSubSearchArgument(subSearch: SubSearch)
}

class AllAnyFromSubQueryOp<T>(isAny: Boolean, subQuery: Query) : AllAnyFromBaseOp<T, Query>(isAny, subQuery) {
    override fun QueryBuilder.registerSubSearchArgument(subSearch: Query) {
        subSearch.prepareSQL(this)
    }
}

/** This function is only supported by PostgreSQL and H2 dialects. */
class AllAnyFromArrayOp<T>(isAny: Boolean, array: Array<T>) : AllAnyFromBaseOp<T, Array<T>>(isAny, array) {
    override fun QueryBuilder.registerSubSearchArgument(subSearch: Array<T>) =
        registerArgument(UntypedAndUnsizedArrayColumnType, subSearch)
}

/** This function is only supported by PostgreSQL and H2 dialects. */
class AllAnyFromTableOp<T>(isAny: Boolean, table: Table) : AllAnyFromBaseOp<T, Table>(isAny, table) {
    override fun QueryBuilder.registerSubSearchArgument(subSearch: Table) {
        +"TABLE "
        +subSearch.tableName
    }
}
