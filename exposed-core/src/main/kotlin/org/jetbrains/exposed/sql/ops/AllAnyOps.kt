package org.jetbrains.exposed.sql.ops

import org.jetbrains.exposed.sql.Op
import org.jetbrains.exposed.sql.QueryBuilder
import org.jetbrains.exposed.sql.UntypedAndUnsizedArrayColumnType

class AllAnyOp<T>(val opName: String, val array: Array<T>) : Op<T>() {

    override fun toQueryBuilder(queryBuilder: QueryBuilder) = queryBuilder {
        +opName
        +'('
        registerArgument(UntypedAndUnsizedArrayColumnType, array)
        +')'
    }
}

inline fun <reified T> AllAnyOp(opName: String, list: List<T>) =
    AllAnyOp(opName, list.toTypedArray())
