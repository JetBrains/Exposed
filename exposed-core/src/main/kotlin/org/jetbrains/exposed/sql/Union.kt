package org.jetbrains.exposed.sql

import org.jetbrains.exposed.sql.statements.Statement
import org.jetbrains.exposed.sql.statements.api.PreparedStatementApi
import org.jetbrains.exposed.sql.vendors.currentDialect
import java.sql.ResultSet

class Union(
    unionAll: Boolean,
    vararg rawStatements: Statement<ResultSet>,
) : AbstractQuery<Union>(rawStatements.flatMap { it.targets }.distinct()) {
    init {
        require(rawStatements.isNotEmpty()) { "UNION is empty" }
        require(rawStatements.none { it is Query && it.isForUpdate() }) { "FOR UPDATE is not allowed within UNION" }
        require(rawStatements.all { it is AbstractQuery<*> }) { "Only Query or Union supported as statements for UNION" }
        require(rawStatements.map { (it as AbstractQuery<*>).set.realFields.size }.distinct().size == 1) {
            "Each UNION query must have the same number of columns"
        }
        if (!currentDialect.supportsSubqueryUnions) {
            require(rawStatements.none { (it as AbstractQuery<*>).let { q -> q.orderByExpressions.isNotEmpty() || q.limit != null } }) {
                "UNION may not contain subqueries"
            }
        }
        distinct = !unionAll
    }

    internal val statements = rawStatements.filterIsInstance<AbstractQuery<*>>().toTypedArray()

    override val set: FieldSet = statements.first().set

    override val queryToExecute: Statement<ResultSet> = this

    private val unionKeyword: String get() = if (distinct) "UNION" else "UNION ALL"

    override fun copy() = Union(distinct, rawStatements = statements).also {
        copyTo(it)
    }

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

    override fun PreparedStatementApi.executeInternal(transaction: Transaction): ResultSet = executeQuery()

    override fun prepareSQL(builder: QueryBuilder): String {
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
                }
            }
        }
    }
}

fun Query.union(other: Query) = Union(unionAll = false, this, other)

fun Query.unionAll(other: Query) = Union(unionAll = true, this, other)

fun Union.union(other: Query) = Union(!distinct, *statements, other)
