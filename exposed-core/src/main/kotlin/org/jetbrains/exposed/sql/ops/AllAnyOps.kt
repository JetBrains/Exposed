package org.jetbrains.exposed.sql.ops

import org.jetbrains.exposed.sql.ArrayColumnType
import org.jetbrains.exposed.sql.Op
import org.jetbrains.exposed.sql.QueryBuilder

class AllAnyOp<T>(val opType: OpType, val array: Array<T>) : Op<T>() {
    enum class OpType {
        All, Any
    }

    override fun toQueryBuilder(queryBuilder: QueryBuilder) = queryBuilder {
        +when (opType) {
            OpType.All -> "ALL"
            OpType.Any -> "ANY"
        }
        +'('
        // +"ARRAY[" // This syntax is for PostgresSQL.
        registerArgument(ArrayColumnType, array)
        // +"]"
        +')'
    }
}

inline fun <reified T> AllAnyOp(opType: AllAnyOp.OpType, list: Iterable<T>) =
    AllAnyOp(opType, list.toList().toTypedArray())
