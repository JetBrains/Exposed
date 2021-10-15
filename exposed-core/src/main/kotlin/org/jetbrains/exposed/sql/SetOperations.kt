package org.jetbrains.exposed.sql

import org.jetbrains.exposed.exceptions.UnsupportedByDialectException
import org.jetbrains.exposed.sql.statements.Statement
import org.jetbrains.exposed.sql.statements.api.PreparedStatementApi
import org.jetbrains.exposed.sql.vendors.MariaDBDialect
import org.jetbrains.exposed.sql.vendors.MysqlDialect
import org.jetbrains.exposed.sql.vendors.currentDialect
import java.sql.ResultSet

sealed class SetOperation(
    val operationName: String,
    val firstStatement: AbstractQuery<*>,
    val secondStatement: AbstractQuery<*>
) : AbstractQuery<SetOperation>((firstStatement.targets + secondStatement.targets).distinct()) {
    val rawStatements: List<AbstractQuery<*>> = listOf(firstStatement, secondStatement)
    init {
        require(rawStatements.isNotEmpty()) { "$operationName is empty" }
        require(rawStatements.none { it is Query && it.isForUpdate() }) { "FOR UPDATE is not allowed within $operationName" }
        require(rawStatements.map { it.set.realFields.size }.distinct().size == 1) {
            "Each $operationName query must have the same number of columns"
        }
        if (!currentDialect.supportsSubqueryUnions) {
            require(rawStatements.none { q -> q.orderByExpressions.isNotEmpty() || q.limit != null }) {
                "$operationName may not contain subqueries"
            }
        }
    }

    override val set: FieldSet = firstStatement.set

    override val queryToExecute: Statement<ResultSet> = this

    override fun count(): Long {
        try {
            count = true
            return transaction.exec(this) { rs ->
                rs.next()
                rs.getLong(1).also {
                    rs.close()
                }
            }!!
        } finally {
            count = false
        }
    }

    override fun empty(): Boolean {
        val oldLimit = limit
        try {
            limit = 1
            val rs = transaction.exec(this)!!
            return !rs.next().also { rs.close() }
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

            if (count) append(") subquery")
        }
        return builder.toString()
    }

    protected open fun prepareStatementSQL(builder: QueryBuilder) {
        builder {
            rawStatements.appendTo(separator = " $operationName ") {
                when (it) {
                    is Query -> {
                        val isSubQuery = it.orderByExpressions.isNotEmpty() || it.limit != null
                        if (isSubQuery) append("(")
                        it.prepareSQL(this)
                        if (isSubQuery) append(")")
                    }
                    is SetOperation -> it.prepareSQL(this)
                }
            }
        }
    }
}

class Union(firstStatement: AbstractQuery<*>, secondStatement: AbstractQuery<*>) : SetOperation("UNION", firstStatement, secondStatement) {
    override fun withDistinct(value: Boolean): SetOperation {
        return if (!value) {
            UnionAll(firstStatement, secondStatement).also {
                copyTo(it)
            }
        } else {
            this
        }
    }

    override fun copy() = Union(firstStatement, secondStatement).also {
        copyTo(it)
    }
}

class UnionAll(firstStatement: AbstractQuery<*>, secondStatement: AbstractQuery<*>) : SetOperation("UNION ALL", firstStatement, secondStatement) {

    override fun withDistinct(value: Boolean): SetOperation {
        return if (value) {
            Union(firstStatement, secondStatement)
        } else {
            this
        }
    }

    override fun copy() = UnionAll(firstStatement, secondStatement).also {
        copyTo(it)
    }
}

class Intersect(firstStatement: AbstractQuery<*>, secondStatement: AbstractQuery<*>) : SetOperation("INTERSECT", firstStatement, secondStatement) {
    override fun copy() = Intersect(firstStatement, secondStatement).also {
        copyTo(it)
    }

    override fun withDistinct(value: Boolean): SetOperation = this

    override fun prepareStatementSQL(builder: QueryBuilder) {
        if (currentDialect is MysqlDialect && currentDialect !is MariaDBDialect) {
            throw UnsupportedByDialectException("$operationName is unsupported", currentDialect)
        } else {
            super.prepareStatementSQL(builder)
        }
    }
}

class Except(firstStatement: AbstractQuery<*>, secondStatement: AbstractQuery<*>) : SetOperation("EXCEPT", firstStatement, secondStatement) {
    override fun copy() = Intersect(firstStatement, secondStatement).also {
        copyTo(it)
    }

    override fun withDistinct(value: Boolean): SetOperation = this

    override fun prepareStatementSQL(builder: QueryBuilder) {
        if (currentDialect is MysqlDialect && currentDialect !is MariaDBDialect) {
            throw UnsupportedByDialectException("$operationName is unsupported", currentDialect)
        } else {
            super.prepareStatementSQL(builder)
        }
    }
}

fun AbstractQuery<*>.union(other: Query): Union = Union(this, other)

fun AbstractQuery<*>.unionAll(other: Query): UnionAll = UnionAll(this, other)

fun AbstractQuery<*>.intersect(other: Query): Intersect = Intersect(this, other)

fun AbstractQuery<*>.except(other: Query): Except = Except(this, other)
