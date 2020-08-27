package org.jetbrains.exposed.sql

import org.jetbrains.exposed.sql.statements.Statement
import org.jetbrains.exposed.sql.statements.StatementType
import org.jetbrains.exposed.sql.statements.api.PreparedStatementApi
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.vendors.currentDialect
import java.sql.ResultSet
import java.util.*

enum class SortOrder {
    ASC, DESC
}

open class Query(set: FieldSet, where: Op<Boolean>?): SizedIterable<ResultRow>, Statement<ResultSet>(StatementType.SELECT, set.source.targetTables()) {
    private val transaction get() = TransactionManager.current()
    var groupedByColumns: List<Expression<*>> = mutableListOf()
        private set
    var orderByExpressions: List<Pair<Expression<*>, SortOrder>> = mutableListOf()
        private set
    var having: Op<Boolean>? = null
        private set
    var distinct: Boolean = false
        private set
    private var forUpdate: Boolean? = null
    var set: FieldSet = set
        private set
    var where: Op<Boolean>? = where
        private set
    var limit: Int? = null
        private set
    var offset: Long = 0
        private set
    var fetchSize: Int? = null
        private set

    override fun copy(): Query = Query(set, where).also { copy ->
        copy.groupedByColumns = groupedByColumns.toMutableList()
        copy.orderByExpressions = orderByExpressions.toMutableList()
        copy.having = having
        copy.distinct = distinct
        copy.forUpdate = forUpdate
        copy.limit = limit
        copy.offset = offset
        copy.fetchSize = fetchSize
    }

    /**
     * Changes [set.fields] field of a Query, [set.source] will be preserved
     * @param body builder for new column set, current [set.source] used as a receiver and current [set] as an , you are expected to slice it
     * @sample org.jetbrains.exposed.sql.tests.shared.DMLTests.testAdjustQuerySlice
     */
    fun adjustSlice(body: ColumnSet.(FieldSet) -> FieldSet): Query = apply { set = set.source.body(set) }

    /**
     * Changes [set.source] field of a Query, [set.fields] will be preserved
     * @param body builder for new column set, previous value used as a receiver
     * @sample org.jetbrains.exposed.sql.tests.shared.DMLTests.testAdjustQueryColumnSet
     */
    fun adjustColumnSet(body: ColumnSet.() -> ColumnSet): Query {
        return adjustSlice { oldSlice -> body().slice(oldSlice.fields) }
    }

    /**
     * Changes [where] field of a Query.
     * @param body new WHERE condition builder, previous value used as a receiver
     * @sample org.jetbrains.exposed.sql.tests.shared.DMLTests.testAdjustQueryWhere
     */
    fun adjustWhere(body: Op<Boolean>?.() -> Op<Boolean>): Query = apply { where = where.body() }

    fun hasCustomForUpdateState() = forUpdate != null
    fun isForUpdate() = (forUpdate ?: false) && currentDialect.supportsSelectForUpdate()

    override fun PreparedStatementApi.executeInternal(transaction: Transaction): ResultSet? {
        val fetchSize = this@Query.fetchSize ?: transaction.db.defaultFetchSize
        if (fetchSize != null) {
            this.fetchSize = fetchSize
        }
        return executeQuery()
    }

    override fun arguments() = QueryBuilder(true).let {
        prepareSQL(it)
        if (it.args.isNotEmpty()) listOf(it.args) else emptyList()
    }

    override fun prepareSQL(transaction: Transaction): String = prepareSQL(QueryBuilder(true))

    fun prepareSQL(builder: QueryBuilder): String {
        builder {
            append("SELECT ")

            if (count) {
                append("COUNT(*)")
            }
            else {
                if (distinct) {
                    append("DISTINCT ")
                }
                set.realFields.appendTo { +it }
            }
            append(" FROM ")
            set.source.describe(transaction, this)

            where?.let {
                append(" WHERE ")
                +it
            }

            if (!count) {
                if (groupedByColumns.isNotEmpty()) {
                    append(" GROUP BY ")
                    groupedByColumns.appendTo {
                        +((it as? ExpressionAlias)?.aliasOnlyExpression() ?: it)
                    }
                }

                having?.let {
                    append(" HAVING ")
                    append(it)
                }

                if (orderByExpressions.isNotEmpty()) {
                    append(" ORDER BY ")
                    orderByExpressions.appendTo {
                        append((it.first as? ExpressionAlias<*>)?.alias ?: it.first, " ", it.second.name)
                    }
                }

                limit?.let {
                    append(" ")
                    append(currentDialect.functionProvider.queryLimit(it, offset, orderByExpressions.isNotEmpty()))
                }
            }

            if (isForUpdate()) {
                append(" FOR UPDATE")
            }
        }
        return builder.toString()
    }

    override fun forUpdate() : Query {
        this.forUpdate = true
        return this
    }

    override fun notForUpdate(): Query {
        forUpdate = false
        return this
    }

    fun withDistinct(value: Boolean = true) : Query {
        distinct = value
        return this
    }

    fun groupBy(vararg columns: Expression<*>): Query {
        for (column in columns) {
            (groupedByColumns as MutableList).add(column)
        }
        return this
    }

    fun having(op: SqlExpressionBuilder.() -> Op<Boolean>) : Query {
        val oop = Op.build { op() }
        if (having != null) {
            error ("HAVING clause is specified twice. Old value = '$having', new value = '$oop'")
        }
        having = oop
        return this
    }

    fun orderBy(column: Expression<*>, order: SortOrder = SortOrder.ASC) = orderBy(column to order)

    override fun orderBy(vararg columns: Pair<Expression<*>, SortOrder>) : Query {
        (orderByExpressions as MutableList).addAll(columns)
        return this
    }

    override fun limit(n: Int, offset: Long): Query {
        this.limit = n
        this.offset = offset
        return this
    }

    fun fetchSize(n: Int): Query {
        this.fetchSize = n
        return this
    }

    private inner class ResultIterator(val rs: ResultSet): Iterator<ResultRow> {
        private var hasNext: Boolean? = null

        private val fields = set.realFields

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


    override operator fun iterator(): Iterator<ResultRow> {
        val distinctExpressions = this.set.fields.distinct()
        val queryToExecute = if (distinctExpressions.size < set.fields.size) {
            copy().adjustSlice { slice(distinctExpressions) }
        } else
            this
        val resultIterator = ResultIterator(transaction.exec(queryToExecute)!!)
        return if (transaction.db.supportsMultipleResultSets)
            resultIterator
        else {
            arrayListOf<ResultRow>().apply {
                resultIterator.forEach {
                    this += it
                }
            }.iterator()
        }
    }

    private var count: Boolean = false
    override fun count(): Long {
        return if (distinct || groupedByColumns.isNotEmpty() || limit != null) {
            fun Column<*>.makeAlias() = alias(transaction.db.identifierManager.quoteIfNecessary("${table.tableName}_$name"))

            val originalSet = set
            try {
                var expInx = 0
                adjustSlice {
                    slice(originalSet.fields.map {
                        it as? ExpressionAlias<*> ?: ((it as? Column<*>)?.makeAlias() ?: it.alias("exp${expInx++}"))
                    })
                }

                alias("subquery").selectAll().count()
            } finally {
                set = originalSet
            }
        } else {
            try {
                count = true
                transaction.exec(this) {
                    it.next()
                    it.getLong(1)
                }!!
            } finally {
                count = false
            }
        }
    }

    override fun empty(): Boolean {
        val oldLimit = limit
        try {
            if (!isForUpdate())
                limit = 1
            return !transaction.exec(this)!!.next()
        } finally {
            limit = oldLimit
        }
    }
}

/**
 * Mutate Query instance and add `andPart` to where condition with `and` operator.
 * @return same Query instance which was provided as a receiver.
 */
fun Query.andWhere(andPart: SqlExpressionBuilder.() -> Op<Boolean>) = adjustWhere {
    val expr = Op.build { andPart() }
    if(this == null) expr
    else this and expr
}

/**
 * Mutate Query instance and add `andPart` to where condition with `or` operator.
 * @return same Query instance which was provided as a receiver.
 */
fun Query.orWhere(andPart: SqlExpressionBuilder.() -> Op<Boolean>) = adjustWhere {
    val expr = Op.build { andPart() }
    if(this == null) expr
    else this or expr
}
