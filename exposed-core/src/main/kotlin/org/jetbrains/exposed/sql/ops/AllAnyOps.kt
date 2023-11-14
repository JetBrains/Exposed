package org.jetbrains.exposed.sql.ops

import org.jetbrains.exposed.sql.ArrayColumnType
import org.jetbrains.exposed.sql.Op
import org.jetbrains.exposed.sql.QueryBuilder

abstract class AllAnyOp<T>(val list: Iterable<T>) : Op<T>() {
    enum class OpType {
        All, Any
    }

    abstract fun isAllOrAny(): OpType

    override fun toQueryBuilder(queryBuilder: QueryBuilder) = queryBuilder {
        +when (isAllOrAny()) {
            OpType.All -> "ALL"
            OpType.Any -> "ANY"
        }
        +'('
        //+"ARRAY[" // This syntax is for PostgresSQL.
        registerArgument(ArrayColumnType<T>(), list)
        //+"]"
        +')'
    }
}

class AllOp<T>(list: Iterable<T>) : AllAnyOp<T>(list) {
    override fun isAllOrAny(): OpType = OpType.All
}

class AnyOp<T>(list: Iterable<T>) : AllAnyOp<T>(list) {
    override fun isAllOrAny(): OpType = OpType.Any
}
