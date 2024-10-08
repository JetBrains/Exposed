package org.jetbrains.exposed.sql.statements

import org.jetbrains.exposed.sql.statements.api.ResultApi
import org.jetbrains.exposed.sql.transactions.TransactionManager

// this most likely needs SUSPEND variants for query result iteration
internal abstract class StatementIterator<RA : ResultApi, T, RR>(
    protected val result: RA
) : Iterator<RR> {
    protected abstract val fieldIndex: Map<T, Int>

    protected abstract fun createResultRow(): RR

    protected var hasNext = false
        set(value) {
            field = value
            if (!field) {
                result.releaseResult()
                TransactionManager.current().openResultSetsCount--
            }
        }

    override operator fun next(): RR {
        if (!hasNext) throw NoSuchElementException()
        val resultRow = createResultRow()
        hasNext = result.next()
        return resultRow
    }

    override fun hasNext(): Boolean = hasNext
}
