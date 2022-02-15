package org.jetbrains.exposed.postgresql.sql

import org.jetbrains.exposed.sql.Op
import org.jetbrains.exposed.sql.QueryBuilder

/**
 * SQL renderer allowing to register code for custom SQL render - DB specific dialects or so
 */
internal interface SQLRenderer {
    fun render(builder: QueryBuilder)
}

internal object NoopSQLRenderer : SQLRenderer {
    override fun render(builder: QueryBuilder) {
        //do nothing
    }
}

internal fun checkWhereCalled(funName: String, useInstead: String, where: Op<Boolean>?) {
    if (where == null) {
        throw IllegalStateException(
            "Calling $funName without where clause. " +
                "This exception try to avoid unwanted modification (update or delete) on whole table. " +
                "Call $useInstead."
        )
    }
}
