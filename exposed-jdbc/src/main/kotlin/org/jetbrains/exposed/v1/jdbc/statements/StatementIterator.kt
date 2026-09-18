package org.jetbrains.exposed.v1.jdbc.statements

import org.jetbrains.exposed.v1.jdbc.statements.jdbc.JdbcResult
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager
import java.sql.ResultSet

internal abstract class StatementIterator<T, RR>(
    protected val jdbcResult: JdbcResult
) : Iterator<RR> {
    protected val result: ResultSet
        get() = jdbcResult.result

    protected abstract val fieldIndex: Map<T, Int>

    protected abstract fun createResultRow(): RR

    /** Whether another row can be read. Setting this to `false` releases the result set and the statement behind it. */
    protected var hasNext = false
        set(value) {
            field = value
            if (!field) {
                TransactionManager.current().releaseResult(jdbcResult)
            }
        }

    override fun hasNext(): Boolean = hasNext

    override operator fun next(): RR {
        if (!hasNext) throw NoSuchElementException()
        val resultRow = createResultRow()
        hasNext = result.next()
        return resultRow
    }
}
