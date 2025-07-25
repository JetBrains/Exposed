package org.jetbrains.exposed.v1.jdbc

import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.core.statements.api.ResultApi
import org.jetbrains.exposed.v1.core.vendors.H2Dialect
import org.jetbrains.exposed.v1.core.vendors.MariaDBDialect
import org.jetbrains.exposed.v1.core.vendors.MysqlDialect
import org.jetbrains.exposed.v1.core.vendors.OracleDialect
import org.jetbrains.exposed.v1.core.vendors.currentDialect
import org.jetbrains.exposed.v1.core.vendors.h2Mode
import org.jetbrains.exposed.v1.exceptions.UnsupportedByDialectException
import org.jetbrains.exposed.v1.jdbc.statements.BlockingExecutable
import org.jetbrains.exposed.v1.jdbc.statements.StatementIterator
import org.jetbrains.exposed.v1.jdbc.statements.api.JdbcPreparedStatementApi
import org.jetbrains.exposed.v1.jdbc.statements.jdbc.JdbcResult
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager
import java.sql.ResultSet

/**
 * Represents an SQL operation that combines the results of multiple queries into a single result.
 *
 * @param secondStatement The SQL statement on the right-hand side of the set operator.
 */
sealed class SetOperation(
    operationName: String,
    _firstStatement: AbstractQuery<*>,
    val secondStatement: AbstractQuery<*>
) : AbstractQuery<JdbcResult>((_firstStatement.targets + secondStatement.targets).distinct()),
    BlockingExecutable<ResultApi, SetOperation>,
    SizedIterable<ResultRow> {

    override val statement: SetOperation = this

    protected val transaction: JdbcTransaction
        get() = TransactionManager.current()

    /** The SQL statement on the left-hand side of the set operator. */
    val firstStatement: AbstractQuery<*> = when (_firstStatement) {
        is Query -> {
            val newSlice = _firstStatement.set.fields.mapIndexed { index, expression ->
                when (expression) {
                    is Column<*>, is IExpressionAlias<*> -> expression
                    is ExpressionWithColumnType<*> -> expression.alias("exp$index")
                    else -> expression.alias("exp$index")
                }
            }
            _firstStatement.copy().adjustSelect { select(newSlice) }
        }
        is SetOperation -> _firstStatement
        else -> error("Unsupported statement type ${_firstStatement::class.simpleName} in $operationName")
    }

    private val rawStatements: List<AbstractQuery<*>> = listOf(firstStatement, secondStatement)

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

    /** The SQL keyword representing the set operation. */
    open val operationName = operationName

    /** Returns the number of results retrieved after query execution. */
    override fun count(): Long {
        try {
            count = true
            return transaction.exec(this) { rs ->
                check(rs is JdbcResult) { "Unexpected result type: $rs" }
                rs.next()
                (rs.getObject(1) as? Number)?.toLong().also {
                    rs.close()
                }
            }!!
        } finally {
            count = false
        }
    }

    /** Returns whether any results were retrieved by query execution. */
    override fun empty(): Boolean {
        val oldLimit = limit
        try {
            limit = 1
            val rs = transaction.exec(this)!!
            check(rs is JdbcResult) { "Unexpected result type: $rs" }
            return !rs.next().also { rs.close() }
        } finally {
            limit = oldLimit
        }
    }

    override fun JdbcPreparedStatementApi.executeInternal(
        transaction: JdbcTransaction
    ): JdbcResult = executeQuery()

    override fun prepareSQL(builder: QueryBuilder): String {
        builder {
            if (count) append("SELECT COUNT(*) FROM (")

            prepareStatementSQL(this)

            if (orderByExpressions.isNotEmpty()) {
                append(" ORDER BY ")
                orderByExpressions.appendTo { (expression, sortOrder) ->
                    currentDialect.dataTypeProvider.precessOrderByClause(this, expression, sortOrder)
                }
            }

            if (limit != null || offset > 0) {
                append(" ")
                append(currentDialect.functionProvider.queryLimitAndOffset(limit, offset, true))
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

    override fun limit(count: Int): SetOperation = apply { limit = count }

    override fun offset(start: Long): SetOperation = apply { offset = start }

    fun orderBy(column: Expression<*>, order: SortOrder = SortOrder.ASC): SetOperation = orderBy(column to order)

    override fun orderBy(vararg order: Pair<Expression<*>, SortOrder>): SetOperation = apply {
        (orderByExpressions as MutableList).addAll(order)
    }

    private val queryToExecute: BlockingExecutable<ResultApi, SetOperation>
        get() = this

    override fun iterator(): Iterator<ResultRow> {
        val rs = transaction.exec(queryToExecute)!! as JdbcResult
        val resultIterator = ResultIterator(rs.result)
        return if (transaction.db.supportsMultipleResultSets) {
            resultIterator
        } else {
            Iterable { resultIterator }.toList().iterator()
        }
    }

    private inner class ResultIterator(rs: ResultSet) : StatementIterator<Expression<*>, ResultRow>(rs) {
        override val fieldIndex = set.realFields.toSet()
            .mapIndexed { index, expression -> expression to index }
            .toMap()

        init {
            hasNext = result.next()
            if (hasNext) trackResultSet(transaction)
        }

        override fun createResultRow(): ResultRow = ResultRow.create(JdbcResult(result), fieldIndex)
    }

    companion object {
        private fun trackResultSet(transaction: JdbcTransaction) {
            val threshold = transaction.db.config.logTooMuchResultSetsThreshold
            if (threshold > 0 && threshold < transaction.openResultSetsCount) {
                val message = "Current opened result sets size ${transaction.openResultSetsCount} " +
                    "exceeds $threshold threshold for transaction ${transaction.id} "
                val stackTrace = Exception(message).stackTraceToString()
                exposedLogger.error(stackTrace)
            }
            transaction.openResultSetsCount++
        }
    }
}

/** Represents an SQL operation that combines all results from two queries, without any duplicates. */
class Union(
    firstStatement: AbstractQuery<*>,
    secondStatement: AbstractQuery<*>
) : SetOperation("UNION", firstStatement, secondStatement) {
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

/** Represents an SQL operation that combines all results from two queries, with duplicates included. */
class UnionAll(
    firstStatement: AbstractQuery<*>,
    secondStatement: AbstractQuery<*>
) : SetOperation("UNION ALL", firstStatement, secondStatement) {

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

/** Represents an SQL operation that returns only the common rows from two query results, without any duplicates. */
class Intersect(
    firstStatement: AbstractQuery<*>,
    secondStatement: AbstractQuery<*>
) : SetOperation("INTERSECT", firstStatement, secondStatement) {
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

/**
 * Represents an SQL operation that returns the distinct results of [firstStatement] that are not common to [secondStatement].
 */
class Except(
    firstStatement: AbstractQuery<*>,
    secondStatement: AbstractQuery<*>
) : SetOperation("EXCEPT", firstStatement, secondStatement) {

    override val operationName: String
        get() = when {
            currentDialect is OracleDialect || currentDialect.h2Mode == H2Dialect.H2CompatibilityMode.Oracle -> "MINUS"
            else -> "EXCEPT"
        }

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

/**
 * Combines all results from [this] query with the results of [other], WITHOUT including duplicates.
 *
 * @sample org.jetbrains.exposed.v1.tests.shared.dml.UnionTests.testUnionWithLimit
 */
fun AbstractQuery<*>.union(other: Query): Union = Union(this, other)

/**
 * Combines all results from [this] query with the results of [other], WITH duplicates included.
 *
 * @sample org.jetbrains.exposed.v1.tests.shared.dml.UnionTests.testUnionWithAllResults
 */
fun AbstractQuery<*>.unionAll(other: Query): UnionAll = UnionAll(this, other)

/**
 * Returns only results from [this] query that are common to the results of [other], WITHOUT including any duplicates.
 *
 * @sample org.jetbrains.exposed.v1.tests.shared.dml.UnionTests.testIntersectWithThreeQueries
 */
fun AbstractQuery<*>.intersect(other: Query): Intersect = Intersect(this, other)

/**
 * Returns only distinct results from [this] query that are NOT common to the results of [other].
 *
 * @sample org.jetbrains.exposed.v1.tests.shared.dml.UnionTests.testExceptWithTwoQueries
 */
fun AbstractQuery<*>.except(other: Query): Except = Except(this, other)
