package org.jetbrains.exposed.sql

import org.jetbrains.exposed.sql.statements.Statement
import org.jetbrains.exposed.sql.statements.StatementType
import org.jetbrains.exposed.sql.transactions.TransactionManager
import java.sql.ResultSet

abstract class AbstractQuery<T : AbstractQuery<T>>(targets: List<Table>) : SizedIterable<ResultRow>, Statement<ResultSet>(StatementType.SELECT, targets) {
    protected val transaction get() = TransactionManager.current()

    var orderByExpressions: List<Pair<Expression<*>, SortOrder>> = mutableListOf()
        private set

    var limit: Int? = null
        protected set
    var offset: Long = 0
        private set
    var fetchSize: Int? = null
        private set

    abstract val set: FieldSet

    protected fun copyTo(other: AbstractQuery<T>) {
        other.orderByExpressions = orderByExpressions.toMutableList()
        other.limit = limit
        other.offset = offset
        other.fetchSize = fetchSize
    }

    override fun prepareSQL(transaction: Transaction) = prepareSQL(QueryBuilder(true))

    abstract fun prepareSQL(builder: QueryBuilder): String

    override fun arguments() = QueryBuilder(true).let {
        prepareSQL(it)
        if (it.args.isNotEmpty()) listOf(it.args) else emptyList()
    }

    abstract fun withDistinct(value: Boolean = true): T

    override fun limit(n: Int, offset: Long): T = apply {
        limit = n
        this.offset = offset
    } as T

    fun orderBy(column: Expression<*>, order: SortOrder = SortOrder.ASC): T = orderBy(column to order)

    override fun orderBy(vararg order: Pair<Expression<*>, SortOrder>): T = apply {
        (orderByExpressions as MutableList).addAll(order)
    } as T

    fun fetchSize(n: Int): T = apply {
        fetchSize = n
    } as T

    protected var count: Boolean = false

    protected abstract val queryToExecute: Statement<ResultSet>

    override fun iterator(): Iterator<ResultRow> {
        val resultIterator = ResultIterator(transaction.exec(queryToExecute)!!)
        return if (transaction.db.supportsMultipleResultSets) {
            resultIterator
        } else {
            Iterable { resultIterator }.toList().iterator()
        }
    }

    private inner class ResultIterator(val rs: ResultSet) : Iterator<ResultRow> {
        private var hasNext = false
            set(value) {
                field = value
                if (!field) {
                    rs.statement?.close()
                    transaction.openResultSetsCount--
                }
            }

        private val fieldsIndex = set.realFields.toSet().mapIndexed { index, expression -> expression to index }.toMap()

        init {
            hasNext = rs.next()
            if (hasNext) trackResultSet(transaction)
        }

        override operator fun next(): ResultRow {
            if (!hasNext) throw NoSuchElementException()
            val result = ResultRow.create(rs, fieldsIndex)
            hasNext = rs.next()
            return result
        }

        override fun hasNext(): Boolean = hasNext
    }

    companion object {
        private fun trackResultSet(transaction: Transaction) {
            val threshold = transaction.db.config.logTooMuchResultSetsThreshold
            if (threshold > 0 && threshold < transaction.openResultSetsCount) {
                val message =
                    "Current opened result sets size ${transaction.openResultSetsCount} exceeds $threshold threshold for transaction ${transaction.id} "
                val stackTrace = Exception(message).stackTraceToString()
                exposedLogger.error(stackTrace)
            }
            transaction.openResultSetsCount++
        }
    }
}
