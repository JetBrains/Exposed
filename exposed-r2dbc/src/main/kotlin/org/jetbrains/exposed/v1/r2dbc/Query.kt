package org.jetbrains.exposed.v1.r2dbc

import io.r2dbc.spi.R2dbcException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.single
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.core.SqlExpressionBuilder.greater
import org.jetbrains.exposed.v1.core.SqlExpressionBuilder.less
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.statements.api.ResultApi
import org.jetbrains.exposed.v1.core.vendors.ForUpdateOption
import org.jetbrains.exposed.v1.core.vendors.currentDialect
import org.jetbrains.exposed.v1.r2dbc.statements.SuspendExecutable
import org.jetbrains.exposed.v1.r2dbc.statements.api.R2dbcPreparedStatementApi
import org.jetbrains.exposed.v1.r2dbc.statements.api.R2dbcResult
import org.jetbrains.exposed.v1.r2dbc.transactions.TransactionManager

/** Class representing an SQL `SELECT` statement on which query clauses can be built. */
open class Query(
    override var set: FieldSet,
    where: Op<Boolean>?
) : AbstractQuery<Query>(set.source.targetTables()),
    SuspendExecutable<ResultApi, Query>,
    SizedIterable<ResultRow> {

    override val statement: Query = this

    init {
        this.where = where
    }

    protected val transaction: R2dbcTransaction
        get() = TransactionManager.current()

    /** Creates a new [Query] instance using all stored properties of this `SELECT` query. */
    override fun copy(): Query = Query(set, where).also { copy ->
        copyTo(copy)
    }

    override fun forUpdate(option: ForUpdateOption): Query {
        @OptIn(InternalApi::class)
        this.forUpdate = if (option is ForUpdateOption.NoForUpdateOption || currentDialect.supportsSelectForUpdate) {
            option
        } else {
            null
        }
        return this
    }

    override fun notForUpdate(): Query {
        @OptIn(InternalApi::class)
        forUpdate = ForUpdateOption.NoForUpdateOption
        return this
    }

    /** Modifies this query to return only [count] results. **/
    override fun limit(count: Int): Query = apply { limit = count }

    /** Modifies this query to return only results starting after the specified [start]. **/
    override fun offset(start: Long): Query = apply { offset = start }

    /** Modifies this query to sort results by the specified [column], according to the provided [order]. **/
    fun orderBy(column: Expression<*>, order: SortOrder = SortOrder.ASC): Query = orderBy(column to order)

    /** Modifies this query to sort results according to the provided [order] of expressions. **/
    override fun orderBy(vararg order: Pair<Expression<*>, SortOrder>): Query = apply {
        (orderByExpressions as MutableList).addAll(order)
    }

    /**
     * Specifies that the `SELECT` query should retrieve distinct results based on the given list of columns with sort orders.
     * This method sets a `DISTINCT ON` clause and may reorder the results as indicated.
     *
     * This method can be used to set a `DISTINCT ON` clause for the query, which is supported by some SQL dialects
     * (e.g., PostgreSQL, H2), along with an `ORDER BY` clause for the specified columns.
     *
     * @param columns The columns and their sort orders to apply the `DISTINCT ON` clause.
     * @return The current `Query` instance with the `DISTINCT ON` clause and reordering applied.
     */
    fun withDistinctOn(vararg columns: Pair<Column<*>, SortOrder>): Query = apply {
        if (columns.isEmpty()) return@apply

        require(!distinct) { "DISTINCT ON cannot be used with the DISTINCT modifier. Only one of them should be applied." }
        withDistinctOn(columns = columns.map { it.first }.toTypedArray())
        return orderBy(order = columns)
    }

    /**
     * Assigns a new selection of columns, by changing the `fields` property of this query's [set],
     * while preserving its `source` property.
     *
     * @param body Builder for the new column set defined using `select()`, with the current [set]'s `source`
     * property used as the receiver and the current [set] as an argument.
     * @sample org.jetbrains.exposed.v1.r2dbc.sql.tests.shared.dml.AdjustQueryTests.testAdjustQuerySlice
     */
    inline fun adjustSelect(body: ColumnSet.(FieldSet) -> Query): Query = apply { set = set.source.body(set).set }

    /**
     * Assigns a new column set, either a [org.jetbrains.exposed.v1.sql.Table] or a [org.jetbrains.exposed.v1.sql.Join], by changing the `source` property of this query's [set],
     * while preserving its `fields` property.
     *
     * @param body Builder for the new column set, with the previous column set value as the receiver.
     * @sample org.jetbrains.exposed.v1.r2dbc.sql.tests.shared.dml.AdjustQueryTests.testAdjustQueryColumnSet
     */
    inline fun adjustColumnSet(body: ColumnSet.() -> ColumnSet): Query {
        return adjustSelect { oldSlice -> body().select(oldSlice.fields) }
    }

    /**
     * Changes the [where] field of this query.
     *
     * @param body Builder for the new `WHERE` condition, with the previous value used as the receiver.
     * @sample org.jetbrains.exposed.v1.r2dbc.sql.tests.shared.dml.AdjustQueryTests.testAdjustQueryWhere
     */
    fun adjustWhere(body: Op<Boolean>?.() -> Op<Boolean>): Query = apply { where = where.body() }

    /**
     * Appends a `WHERE` clause with the specified [predicate] to this `SELECT` query.
     *
     * @sample org.jetbrains.exposed.v1.r2dbc.sql.tests.shared.dml.SelectTests.testSelect
     */
    fun where(predicate: SqlExpressionBuilder.() -> Op<Boolean>): Query = where(SqlExpressionBuilder.predicate())

    /**
     * Appends a `WHERE` clause with the specified [predicate] to this `SELECT` query.
     *
     * @sample org.jetbrains.exposed.v1.r2dbc.sql.tests.shared.dml.ExistsTests.testExists01
     */
    fun where(predicate: Op<Boolean>): Query {
        where?.let {
            error("WHERE clause is specified twice. Old value = '$it', new value = '$predicate'")
        }
        where = predicate
        return this
    }

    /**
     * Iterates over multiple executions of this `SELECT` query with its `LIMIT` clause set to [batchSize]
     * until the amount of results retrieved from the database is less than [batchSize].
     *
     * This query's [FieldSet] will be ordered by the first auto-increment column.
     *
     * **Note:** There is a possibility that the final batched sub-collection emitted may be empty, as the flow of results
     * cannot be pre-checked before being yielded. Whether the amount of results retrieved is less than the provided batch
     * size will not be determined until the batch is consumed on the user-end, which means the amount of results in a
     * batch may be zero.
     *
     * @param batchSize Size of each sub-collection to return.
     * @param sortOrder Order in which the results should be retrieved.
     * @return Retrieved results as a collection of batched [ResultRow] sub-collections.
     * @sample org.jetbrains.exposed.v1.r2dbc.sql.tests.shared.dml.FetchBatchedResultsTests.testFetchBatchedResultsWithWhereAndSetBatchSize
     */
    @Suppress("UNCHECKED_CAST")
    fun fetchBatchedResults(batchSize: Int = 1000, sortOrder: SortOrder = SortOrder.ASC): Flow<Flow<ResultRow>> {
        require(batchSize > 0) { "Batch size should be greater than 0." }
        require(limit == null) { "A manual `LIMIT` clause should not be set. By default, `batchSize` will be used." }
        require(orderByExpressions.isEmpty()) {
            "A manual `ORDER BY` clause should not be set. By default, the auto-incrementing column will be used."
        }

        val autoIncColumn = try {
            set.source.columns.first { it.columnType.isAutoInc }
        } catch (_: NoSuchElementException) {
            throw UnsupportedOperationException("Batched select only works on tables with an auto-incrementing column")
        }
        limit = batchSize
        (orderByExpressions as MutableList).add(autoIncColumn to sortOrder)
        val whereOp = where ?: Op.TRUE
        val fetchInAscendingOrder = sortOrder in listOf(SortOrder.ASC, SortOrder.ASC_NULLS_FIRST, SortOrder.ASC_NULLS_LAST)

        return object : Flow<Flow<ResultRow>> {
            override suspend fun collect(collector: FlowCollector<Flow<ResultRow>>) {
                var lastOffset = if (fetchInAscendingOrder) 0L else null
                while (true) {
                    val query = this@Query.copy().adjustWhere {
                        lastOffset?.let { lastOffset ->
                            whereOp and if (fetchInAscendingOrder) {
                                when (autoIncColumn.columnType) {
                                    is EntityIDColumnType<*> -> {
                                        (autoIncColumn as? Column<EntityID<Long>>)?.let {
                                            (it greater lastOffset)
                                        } ?: (autoIncColumn as? Column<EntityID<Int>>)?.let {
                                            (it greater lastOffset.toInt())
                                        } ?: (autoIncColumn greater lastOffset)
                                    }
                                    else -> (autoIncColumn greater lastOffset)
                                }
                            } else {
                                when (autoIncColumn.columnType) {
                                    is EntityIDColumnType<*> -> {
                                        (autoIncColumn as? Column<EntityID<Long>>)?.let {
                                            (it less lastOffset)
                                        } ?: (autoIncColumn as? Column<EntityID<Int>>)?.let {
                                            (it less lastOffset.toInt())
                                        } ?: (autoIncColumn less lastOffset)
                                    }
                                    else -> (autoIncColumn less lastOffset)
                                }
                            }
                        } ?: whereOp
                    }

                    var resultCount = 0
                    var lastResult: ResultRow? = null

                    val results = flow { query.collect(this) }.onEach {
                        resultCount++
                        lastResult = it
                    }

                    collector.emit(results)

                    if (resultCount < batchSize) break

                    lastResult?.let {
                        lastOffset = toLong(it[autoIncColumn]!!)
                    }
                }
            }

            private fun toLong(autoIncVal: Any): Long = when (autoIncVal) {
                is EntityID<*> -> toLong(autoIncVal.value)
                is Int -> autoIncVal.toLong()
                else -> autoIncVal as Long
            }
        }
    }

    /**
     * Returns the number of results retrieved after query execution.
     *
     * @sample org.jetbrains.exposed.v1.r2dbc.sql.tests.shared.dml.InsertSelectTests.testInsertSelect02
     */
    override suspend fun count(): Long {
        return if (distinct || distinctOn != null || groupedByColumns.isNotEmpty() || limit != null || offset > 0) {
            @OptIn(InternalApi::class)
            fun Column<*>.makeAlias() =
                alias(transaction.db.identifierManager.quoteIfNecessary("${table.tableNameWithoutSchemeSanitized}_$name"))

            val originalSet = set
            try {
                var expInx = 0
                adjustSelect {
                    select(
                        originalSet.fields.map {
                            when (it) {
                                is IExpressionAlias<*> -> it
                                is Column<*> -> it.makeAlias()
                                is ExpressionWithColumnType<*> -> it.alias("exp${expInx++}")
                                else -> it.alias("exp${expInx++}")
                            }
                        }
                    )
                }

                alias("subquery").selectAll().count()
            } finally {
                set = originalSet
            }
        } else {
            try {
                count = true
                val rs = transaction.execQuery(this)
                rs.mapRows { (it.getObject(1) as? Number)?.toLong() }.single()
                    ?: error("The query did not return a single count result. Please check the SQL logs.")
            } catch (cause: R2dbcException) {
                throw ExposedR2dbcException(cause, this.getContexts(), TransactionManager.current())
            } finally {
                count = false
            }
        }
    }

    /**
     * Returns whether any results were retrieved by query execution.
     *
     * @sample org.jetbrains.exposed.v1.r2dbc.sql.tests.shared.dml.SelectTests.testSizedIterable
     */
    override suspend fun empty(): Boolean {
        val oldLimit = limit
        try {
            if (!isForUpdate()) limit = 1
            val rs = transaction.execQuery(this)
            return rs.mapRows { }.firstOrNull() == null
        } catch (cause: R2dbcException) {
            throw ExposedR2dbcException(cause, this.getContexts(), TransactionManager.current())
        } finally {
            limit = oldLimit
        }
    }

    override suspend fun R2dbcPreparedStatementApi.executeInternal(transaction: R2dbcTransaction): R2dbcResult? {
        val fetchSize = this@Query.fetchSize ?: transaction.db.defaultFetchSize
        if (fetchSize != null) {
            setFetchSize(fetchSize)
        }
        return executeQuery()
    }

    private val queryToExecute: SuspendExecutable<ResultApi, Query>
        get() {
            val distinctExpressions = set.fields.distinct()
            return if (distinctExpressions.size < set.fields.size) {
                copy().adjustSelect { select(distinctExpressions) }
            } else {
                this
            }
        }

    override suspend fun collect(collector: FlowCollector<ResultRow>) {
        val fieldIndex = set.realFields.toSet()
            .mapIndexed { index, expression -> expression to index }
            .toMap()
        val tx = TransactionManager.current()
        val rs = tx.execQuery(queryToExecute)

        try {
            rs.mapRows {
                val resultRow = ResultRow.create(it, fieldIndex)
                trackResultSet(tx)
                resultRow
            }.collect { rr -> rr?.let { collector.emit(it) } }
        } catch (cause: R2dbcException) {
            throw ExposedR2dbcException(cause, this.getContexts(), transaction)
        }
    }

    companion object {
        private fun trackResultSet(transaction: R2dbcTransaction) {
            val threshold = transaction.db.config.logTooMuchResultSetsThreshold
            if (threshold > 0 && threshold < transaction.openResultRowsCount) {
                val message =
                    "Current opened result sets size ${transaction.openResultRowsCount} exceeds $threshold threshold for transaction ${transaction.id} "
                val stackTrace = Exception(message).stackTraceToString()
                exposedLogger.error(stackTrace)
            }
            transaction.openResultRowsCount++
        }
    }
}

/**
 * Mutate Query instance and add `andPart` to having condition with `and` operator.
 * @return same Query instance which was provided as a receiver.
 */
fun Query.andHaving(andPart: SqlExpressionBuilder.() -> Op<Boolean>) = adjustHaving {
    val expr = Op.build { andPart() }
    if (this == null) expr else this and expr
}

/**
 * Mutate Query instance and add `orPart` to having condition with `or` operator.
 * @return same Query instance which was provided as a receiver.
 */
fun Query.orHaving(orPart: SqlExpressionBuilder.() -> Op<Boolean>) = adjustHaving {
    val expr = Op.build { orPart() }
    if (this == null) expr else this or expr
}

/**
 * Mutate Query instance and add `andPart` to where condition with `and` operator.
 * @return same Query instance which was provided as a receiver.
 */
fun Query.andWhere(andPart: SqlExpressionBuilder.() -> Op<Boolean>) = adjustWhere {
    val expr = Op.build { andPart() }
    if (this == null) expr else this and expr
}

/**
 * Mutate Query instance and add `orPart` to where condition with `or` operator.
 * @return same Query instance which was provided as a receiver.
 */
fun Query.orWhere(orPart: SqlExpressionBuilder.() -> Op<Boolean>) = adjustWhere {
    val expr = Op.build { orPart() }
    if (this == null) expr else this or expr
}
