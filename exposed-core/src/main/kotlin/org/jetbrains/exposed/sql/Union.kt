package org.jetbrains.exposed.sql

import org.jetbrains.exposed.sql.statements.Statement
import org.jetbrains.exposed.sql.statements.StatementType
import org.jetbrains.exposed.sql.statements.api.PreparedStatementApi
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.vendors.currentDialect
import java.sql.ResultSet

open class Union(
    internal vararg val statements: Statement<ResultSet>,
): SizedIterable<ResultRow>, Statement<ResultSet>(StatementType.SELECT, statements.flatMap { it.targets }.distinct()) {
    init {
        require(statements.isNotEmpty()) { "UNION is empty" }
        require(statements.none { it is Query && it.isForUpdate() }) { "FOR UPDATE is not allowed within UNION" }
        require(statements.all {
            when (it) {
                is Query -> it.set
                is Union -> it.set
                else -> throw UnsupportedOperationException()
            }.realFields.size == set.realFields.size
        }) { "Each UNION query must have the same number of columns" }
        if (!currentDialect.supportsSubqueryUnions) {
            require(statements.none {
                when (it) {
                    is Query -> it.orderByExpressions.isNotEmpty() || it.limit != null
                    is Union -> it.orderByExpressions.isNotEmpty() || it.limit != null
                    else -> throw UnsupportedOperationException()
                }
            }) { "UNION may not contain subqueries" }
        }
    }

    private val transaction get() = TransactionManager.current()

    var orderByExpressions: List<Pair<Expression<*>, SortOrder>> = mutableListOf()
        private set
    var distinct: Boolean = true
        private set
    var limit: Int? = null
        private set
    var offset: Long = 0
        private set
    val set: FieldSet
        get() = statements.first().let {
        when (it) {
            is Query -> it.set
            is Union -> it.set
            else -> throw UnsupportedOperationException()
        }
    }

    override fun iterator(): Iterator<ResultRow> {
        val resultIterator = ResultIterator(transaction.exec(this)!!, set.realFields)
        return if (transaction.db.supportsMultipleResultSets)
            resultIterator
        else {
            Iterable { resultIterator }.toList().iterator()
        }
    }

    override fun limit(n: Int, offset: Long) = apply {
        limit = n
        this.offset = offset
    }

    private var count: Boolean = false

    override fun count(): Long {
        try {
            count = true
            return transaction.exec(this) {
                it.next()
                it.getLong(1)
            }!!
        } finally {
            count = false
        }
    }

    override fun empty(): Boolean {
        val oldLimit = limit
        try {
            limit = 1
            return !transaction.exec(this)!!.next()
        } finally {
            limit = oldLimit
        }
    }

    override fun copy() = Union(*statements).also {
        it.orderByExpressions = orderByExpressions
        it.limit = limit
        it.offset = offset
        it.distinct = distinct
    }

    fun orderBy(column: Expression<*>, order: SortOrder = SortOrder.ASC) = orderBy(column to order)

    override fun orderBy(vararg order: Pair<Expression<*>, SortOrder>) = apply {
        (orderByExpressions as MutableList).addAll(order)
    }

    fun withDistinct(value: Boolean = true) = apply {
        distinct = value
    }

    fun withAll() = withDistinct(false)

    override fun PreparedStatementApi.executeInternal(transaction: Transaction): ResultSet? {
        return executeQuery()
    }

    override fun prepareSQL(transaction: Transaction) = prepareSQL(QueryBuilder(true))

    internal fun prepareSQL(builder: QueryBuilder): String {
        builder {
            if (count) append("SELECT COUNT(*) FROM (")

            prepareStatementSQL(this)

            if (orderByExpressions.isNotEmpty()) {
                append(" ORDER BY ")
                orderByExpressions.appendTo {
                    append((it.first as? ExpressionAlias<*>)?.alias ?: it.first, " ", it.second.name)
                }
            }

            limit?.let {
                append(" ")
                append(currentDialect.functionProvider.queryLimit(it, offset, true))
            }

            if (count) append(") as subquery")
        }
        return builder.toString()
    }

    private val unionKeyword: String get() = if (distinct) "UNION" else "UNION ALL"

    private fun prepareStatementSQL(builder: QueryBuilder) {
        builder {
            statements.toList().appendTo(separator = " $unionKeyword ") {
                when (it) {
                    is Query -> {
                        val isSubQuery = it.orderByExpressions.isNotEmpty() || it.limit != null
                        if (isSubQuery) append("(")
                        it.prepareSQL(this)
                        if (isSubQuery) append(")")
                    }
                    is Union -> it.prepareSQL(this)
                    else -> throw UnsupportedOperationException()
                }

            }
        }
    }

    override fun arguments() = QueryBuilder(true).let {
        prepareSQL(it)
        if (it.args.isNotEmpty()) listOf(it.args) else emptyList()
    }
}

fun Query.union(other: Query) = Union(this, other)

fun Union.union(other: Query) = Union(*statements, other)
