package org.jetbrains.exposed.sql.render

import org.jetbrains.exposed.sql.QueryBuilder

/**
 * SQL renderer allowing to register code for custom SQL render - DB specific dialects or so
 */
interface SQLRenderer {
    fun render(builder: QueryBuilder)
}

object NoopSQLRenderer : SQLRenderer {
    override fun render(builder: QueryBuilder) {
        //do nothing
    }
}
