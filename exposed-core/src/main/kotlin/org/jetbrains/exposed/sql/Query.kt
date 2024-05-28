package org.jetbrains.exposed.sql

import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.SqlExpressionBuilder.greater
import org.jetbrains.exposed.sql.SqlExpressionBuilder.less
import org.jetbrains.exposed.sql.statements.Statement
import org.jetbrains.exposed.sql.statements.api.PreparedStatementApi
import org.jetbrains.exposed.sql.vendors.ForUpdateOption
import org.jetbrains.exposed.sql.vendors.currentDialect
import java.sql.ResultSet
import java.util.*

enum class SortOrder(val code: String) {
    ASC(code = "ASC"),
    DESC(code = "DESC"),
    ASC_NULLS_FIRST(code = "ASC NULLS FIRST"),
    DESC_NULLS_FIRST(code = "DESC NULLS FIRST"),
    ASC_NULLS_LAST(code = "ASC NULLS LAST"),
    DESC_NULLS_LAST(code = "DESC NULLS LAST")
}

/** Class representing an SQL `SELECT` statement on which query clauses can be built. */
open class Query(override var set: FieldSet, where: Op<Boolean>?) : AbstractQuery<Query>(set.source.targetTables()) {
    /** Whether only distinct results should be retrieved by this `SELECT` query. */
    var distinct: Boolean = false
        protected set

    /** The stored list of columns for a `GROUP BY` clause in this `SELECT` query. */
    var groupedByColumns: List<Expression<*>> = mutableListOf()
        private set

    /** The stored condition for a `HAVING` clause in this `SELECT` query. */
    var having: Op<Boolean>? = null
        private set

    private var forUpdate: ForUpdateOption? = null

    /** The stored condition for a `WHERE` clause in this `SELECT` query. */
    var where: Op<Boolean>? = where
        private set

    override val queryToExecute: Statement<ResultSet> get() {
        val distinctExpressions = set.fields.distinct()
        return if (distinctExpressions.size < set.fields.size) {
            copy().adjustSelect { select(distinctExpressions) }
        } else {
            this
        }
    }

    /** Creates a new [Query] instance using all stored properties of this `SELECT` query. */
    override fun copy(): Query = Query(set, where).also { copy ->
        copyTo(copy)
        copy.distinct = distinct
        copy.groupedByColumns = groupedByColumns.toMutableList()
        copy.having = having
        copy.forUpdate = forUpdate
    }

    override fun forUpdate(option: ForUpdateOption): Query {
        this.forUpdate = option
        return this
    }

    override fun notForUpdate(): Query {
        forUpdate = ForUpdateOption.NoForUpdateOption
        return this
    }

    override fun withDistinct(value: Boolean): Query = apply {
        distinct = value
    }

    @Deprecated(
        message = "As part of SELECT DSL design changes, this will be removed in future releases.",
        replaceWith = ReplaceWith("adjustSelect { body.invoke() }"),
        level = DeprecationLevel.WARNING
    )
    fun adjustSlice(body: ColumnSet.(FieldSet) -> FieldSet): Query = apply { set = set.source.body(set) }

    /**
     * Assigns a new selection of columns, by changing the `fields` property of this query's [set],
     * while preserving its `source` property.
     *
     * @param body Builder for the new column set defined using `select()`, with the current [set]'s `source`
     * property used as the receiver and the current [set] as an argument.
     * @sample org.jetbrains.exposed.sql.tests.shared.dml.AdjustQueryTests.testAdjustQuerySlice
     */
    fun adjustSelect(body: ColumnSet.(FieldSet) -> Query): Query = apply { set = set.source.body(set).set }

    /**
     * Assigns a new column set, either a [Table] or a [Join], by changing the `source` property of this query's [set],
     * while preserving its `fields` property.
     *
     * @param body Builder for the new column set, with the previous column set value as the receiver.
     * @sample org.jetbrains.exposed.sql.tests.shared.dml.AdjustQueryTests.testAdjustQueryColumnSet
     */
    fun adjustColumnSet(body: ColumnSet.() -> ColumnSet): Query {
        return adjustSelect { oldSlice -> body().select(oldSlice.fields) }
    }

    /**
     * Changes the [where] field of this query.
     *
     * @param body Builder for the new `WHERE` condition, with the previous value used as the receiver.
     * @sample org.jetbrains.exposed.sql.tests.shared.dml.AdjustQueryTests.testAdjustQueryWhere
     */
    fun adjustWhere(body: Op<Boolean>?.() -> Op<Boolean>): Query = apply { where = where.body() }

    /**
     * Changes the [having] field of this query.
     *
     * @param body Builder for the new `HAVING` condition, with the previous value used as the receiver.
     * @sample org.jetbrains.exposed.sql.tests.shared.dml.AdjustQueryTests.testAdjustQueryHaving
     */
    fun adjustHaving(body: Op<Boolean>?.() -> Op<Boolean>): Query = apply { having = having.body() }

    /** Whether this `SELECT` query already has a stored value option for performing locking reads. */
    fun hasCustomForUpdateState() = forUpdate != null

    /**
     * Whether this `SELECT` query will perform a locking read.
     *
     * **Note:** `SELECT FOR UPDATE` is not supported by all vendors. Please check the documentation.
     */
    fun isForUpdate() = (forUpdate?.let { it != ForUpdateOption.NoForUpdateOption } ?: false) && currentDialect.supportsSelectForUpdate()

    override fun PreparedStatementApi.executeInternal(transaction: Transaction): ResultSet? {
        val fetchSize = this@Query.fetchSize ?: transaction.db.defaultFetchSize
        if (fetchSize != null) {
            this.fetchSize = fetchSize
        }
        return executeQuery()
    }

    override fun prepareSQL(builder: QueryBuilder): String {
        require(set.fields.isNotEmpty()) { "Can't prepare SELECT statement without columns or expressions to retrieve" }

        builder {
            append("SELECT ")

            if (count) {
                append("COUNT(*)")
            } else {
                if (distinct) {
                    append("DISTINCT ")
                }
                set.realFields.appendTo { +it }
            }
            if (set.source != Table.Dual || currentDialect.supportsDualTableConcept) {
                append(" FROM ")
                set.source.describe(transaction, this)
            }

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
                    orderByExpressions.appendTo { (expression, sortOrder) ->
                        currentDialect.dataTypeProvider.precessOrderByClause(this, expression, sortOrder)
                    }
                }

                limit?.let {
                    append(" ")
                    append(currentDialect.functionProvider.queryLimit(it, offset, orderByExpressions.isNotEmpty()))
                }
            }

            if (isForUpdate()) {
                forUpdate?.apply {
                    append(" $querySuffix")
                }
            }
        }
        return builder.toString()
    }

    /**
     * Appends a `GROUP BY` clause with the specified [columns] to this `SELECT` query.
     *
     * @sample org.jetbrains.exposed.sql.tests.shared.dml.GroupByTests.testGroupBy02
     */
    fun groupBy(vararg columns: Expression<*>): Query {
        for (column in columns) {
            (groupedByColumns as MutableList).add(column)
        }
        return this
    }

    /**
     * Appends a `HAVING` clause with the specified [op] condition to this `SELECT` query.
     *
     * @sample org.jetbrains.exposed.sql.tests.shared.dml.GroupByTests.testGroupBy02
     */
    fun having(op: SqlExpressionBuilder.() -> Op<Boolean>): Query {
        val oop = SqlExpressionBuilder.op()
        if (having != null) {
            error("HAVING clause is specified twice. Old value = '$having', new value = '$oop'")
        }
        having = oop
        return this
    }

    /**
     * Appends a `WHERE` clause with the specified [predicate] to this `SELECT` query.
     *
     * @sample org.jetbrains.exposed.sql.tests.shared.dml.SelectTests.testSelect
     */
    fun where(predicate: SqlExpressionBuilder.() -> Op<Boolean>): Query = where(SqlExpressionBuilder.predicate())

    /**
     * Appends a `WHERE` clause with the specified [predicate] to this `SELECT` query.
     *
     * @sample org.jetbrains.exposed.sql.tests.shared.dml.ExistsTests.testExists01
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
     * @param batchSize Size of each sub-collection to return.
     * @param sortOrder Order in which the results should be retrieved.
     * @return Retrieved results as a collection of batched [ResultRow] sub-collections.
     * @sample org.jetbrains.exposed.sql.tests.shared.dml.FetchBatchedResultsTests.testFetchBatchedResultsWithWhereAndSetBatchSize
     */
    fun fetchBatchedResults(batchSize: Int = 1000, sortOrder: SortOrder = SortOrder.ASC): Iterable<Iterable<ResultRow>> {
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

        return object : Iterable<Iterable<ResultRow>> {
            override fun iterator(): Iterator<Iterable<ResultRow>> {
                return iterator {
                    var lastOffset: Long? = null
                    while (true) {
                        val query = this@Query.copy().adjustWhere {
                            return@adjustWhere lastOffset.let {
                                if (it == null) return@let whereOp

                                return@let if (listOf(SortOrder.ASC, SortOrder.ASC_NULLS_FIRST, SortOrder.ASC_NULLS_LAST)
                                        .contains(sortOrder)
                                ) {
                                    whereOp and (autoIncColumn greater it)
                                } else {
                                    whereOp and (autoIncColumn less it)
                                }
                            }
                        }

                        val results = query.iterator().asSequence().toList()

                        if (results.isNotEmpty()) {
                            yield(results)
                        }

                        if (results.size < batchSize) break

                        lastOffset = toLong(results.last()[autoIncColumn]!!)
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
     * @sample org.jetbrains.exposed.sql.tests.shared.dml.InsertSelectTests.testInsertSelect02
     */
    override fun count(): Long {
        return if (distinct || groupedByColumns.isNotEmpty() || limit != null) {
            fun Column<*>.makeAlias() =
                alias(transaction.db.identifierManager.quoteIfNecessary("${table.tableNameWithoutSchemeSanitized}_$name"))

            val originalSet = set
            try {
                var expInx = 0
                adjustSelect {
                    select(
                        originalSet.fields.map {
                            it as? ExpressionAlias<*> ?: ((it as? Column<*>)?.makeAlias() ?: it.alias("exp${expInx++}"))
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
                transaction.exec(this) { rs ->
                    rs.next()
                    rs.getLong(1).also { rs.close() }
                }!!
            } finally {
                count = false
            }
        }
    }

    /**
     * Returns whether any results were retrieved by query execution.
     *
     * @sample org.jetbrains.exposed.sql.tests.shared.dml.SelectTests.testSizedIterable
     */
    override fun empty(): Boolean {
        val oldLimit = limit
        try {
            if (!isForUpdate()) limit = 1
            val resultSet = transaction.exec(this)!!
            return !resultSet.next().also { resultSet.close() }
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
