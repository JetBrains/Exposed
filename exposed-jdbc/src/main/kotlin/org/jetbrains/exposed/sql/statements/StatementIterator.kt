package org.jetbrains.exposed.sql.statements

import org.jetbrains.exposed.sql.JdbcTransaction
import org.jetbrains.exposed.sql.transactions.TransactionManager
import java.sql.ResultSet

internal abstract class StatementIterator<T, RR>(
    protected val result: ResultSet
) : Iterator<RR> {
    protected abstract val fieldIndex: Map<T, Int>

    protected abstract fun createResultRow(): RR

    protected var hasNext = false
        set(value) {
            field = value
            if (!field) {
                val statement = result.statement
                result.close()
                statement?.close()
                (TransactionManager.current() as JdbcTransaction).openResultSetsCount--
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
