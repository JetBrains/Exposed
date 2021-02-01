package org.jetbrains.exposed.sql

import java.sql.ResultSet
import java.util.NoSuchElementException

internal class ResultIterator(val rs: ResultSet, private val fields: List<Expression<*>>): Iterator<ResultRow> {
    private var hasNext: Boolean? = null

    override operator fun next(): ResultRow {
        if (hasNext == null) hasNext()
        if (hasNext == false) throw NoSuchElementException()
        hasNext = null
        return ResultRow.create(rs, fields)
    }

    override fun hasNext(): Boolean {
        if (hasNext == null) hasNext = rs.next()
        if (hasNext == false) rs.close()
        return hasNext!!
    }
}
