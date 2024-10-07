package org.jetbrains.exposed.sql

import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.SqlExpressionBuilder.greater
import org.jetbrains.exposed.sql.SqlExpressionBuilder.less
import org.jetbrains.exposed.sql.statements.Statement
import org.jetbrains.exposed.sql.statements.api.PreparedStatementApi
import org.jetbrains.exposed.sql.statements.api.ResultApi
import org.jetbrains.exposed.sql.vendors.ForUpdateOption
import org.jetbrains.exposed.sql.vendors.currentDialect

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

    /**
     * List of columns on which the query should be distinct.
     *
     * This parameter specifies columns for the `DISTINCT ON` clause, which allows selecting distinct rows based on
     * the specified columns and is supported by some SQL dialects (e.g., PostgreSQL, H2).
     */
    var distinctOn: List<Column<*>>? = null
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

    /** The stored comments and their [CommentPosition]s in this `SELECT` query. */
    var comments: Map<CommentPosition, String> = mutableMapOf()
        private set

    override val queryToExecute: Statement<ResultApi>
        get() {
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
    }

    override fun copyTo(other: Query) {
        super.copyTo(other)
        other.distinct = distinct
        other.distinctOn = distinctOn
        other.groupedByColumns = groupedByColumns.toMutableList()
        other.having = having
        other.forUpdate = forUpdate
        other.comments = comments.toMutableMap()
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
        if (value) {
            require(distinctOn == null) { "DISTINCT cannot be used with the DISTINCT ON modifier. Only one of them should be applied." }
        }
        distinct = value
    }

    /**
     * Specifies that the `SELECT` query should retrieve distinct results based on the given list of columns.
     *
     * This method can be used to set a `DISTINCT ON` clause for the query, which is supported by some SQL dialects
     * (e.g., PostgreSQL, H2). The resulting query will retrieve rows that are distinct based on the specified columns.
     *
     * @param columns The columns to apply the `DISTINCT ON` clause.
     * @return The current `Query` instance with the `DISTINCT ON` clause applied.
     */
    fun withDistinctOn(vararg columns: Column<*>): Query = apply {
        if (columns.isEmpty()) return@apply

        require(!distinct) { "DISTINCT ON cannot be used with the DISTINCT modifier. Only one of them should be applied." }
        distinctOn = (distinctOn ?: emptyList()) + columns
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

    @Deprecated(
        message = "As part of SELECT DSL design changes, this will be removed in future releases.",
        replaceWith = ReplaceWith("adjustSelect { body.invoke() }"),
        level = DeprecationLevel.ERROR
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
    inline fun adjustSelect(body: ColumnSet.(FieldSet) -> Query): Query = apply { set = set.source.body(set).set }

    /**
     * Assigns a new column set, either a [Table] or a [Join], by changing the `source` property of this query's [set],
     * while preserving its `fields` property.
     *
     * @param body Builder for the new column set, with the previous column set value as the receiver.
     * @sample org.jetbrains.exposed.sql.tests.shared.dml.AdjustQueryTests.testAdjustQueryColumnSet
     */
    inline fun adjustColumnSet(body: ColumnSet.() -> ColumnSet): Query {
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

    /**
     * Changes the [content] of the [comments] field at the specified [position] in this query.
     *
     * @param position The [CommentPosition] in the query that should be assigned a new value.
     * @param content The content of the comment that should be set. If left `null`, any comment at the specified
     * [position] will be removed.
     * @sample org.jetbrains.exposed.sql.tests.shared.dml.SelectTests.testSelectWithComment
     */
    fun adjustComments(position: CommentPosition, content: String? = null): Query = apply {
        content?.let {
            (comments as MutableMap)[position] = content
        } ?: run {
            (comments as MutableMap).remove(position)
        }
    }

    /** Whether this `SELECT` query already has a stored value option for performing locking reads. */
    fun hasCustomForUpdateState() = forUpdate != null

    /**
     * Whether this `SELECT` query will perform a locking read.
     *
     * **Note:** `SELECT FOR UPDATE` is not supported by all vendors. Please check the documentation.
     */
    fun isForUpdate() = (forUpdate?.let { it != ForUpdateOption.NoForUpdateOption } ?: false) && currentDialect.supportsSelectForUpdate()

    override suspend fun PreparedStatementApi.executeInternal(transaction: Transaction): ResultApi? {
        val fetchSize = this@Query.fetchSize ?: transaction.db.defaultFetchSize
        if (fetchSize != null) {
            this.fetchSize = fetchSize
        }
        return executeQuery()
    }

    override fun prepareSQL(builder: QueryBuilder): String {
        require(set.fields.isNotEmpty()) { "Can't prepare SELECT statement without columns or expressions to retrieve" }

        builder {
            comments[CommentPosition.FRONT]?.let { comment ->
                append("/*$comment*/ ")
            }

            append("SELECT ")

            if (count) {
                append("COUNT(*)")
            } else {
                if (distinct) {
                    append("DISTINCT ")
                }
                distinctOn
                    ?.takeIf { it.isNotEmpty() }
                    ?.let { columns ->
                        columns.appendTo(prefix = "DISTINCT ON (", postfix = ") ") { append(it) }
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

                if (limit != null || offset > 0) {
                    append(" ")
                    append(currentDialect.functionProvider.queryLimitAndOffset(limit, offset, orderByExpressions.isNotEmpty()))
                }
            }

            if (isForUpdate()) {
                forUpdate?.apply {
                    append(" $querySuffix")
                }
            }

            comments[CommentPosition.BACK]?.let { comment ->
                append(" /*$comment*/")
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
     * Appends an SQL comment, with [content] wrapped by `/* */`, at the specified [CommentPosition] in this `SELECT` query.
     *
     * Adding some comments may be useful for tracking, embedding metadata, or for special instructions, like using
     * `/*FORCE_MASTER*/` for some cloud databases to force the statement to run in the master database.
     *
     * @throws IllegalStateException If a comment has already been appended at the specified [position]. An existing
     * comment can be removed or altered by [adjustComments].
     * @sample org.jetbrains.exposed.sql.tests.shared.dml.SelectTests.testSelectWithComment
     */
    fun comment(content: String, position: CommentPosition = CommentPosition.FRONT): Query {
        comments[position]?.let {
            error("Comment at $position position is specified twice. Old value = '$it', new value = '$content'")
        }
        (comments as MutableMap)[position] = content
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
    // this will most likely need to be SUSPEND
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
        val fetchInAscendingOrder = sortOrder in listOf(SortOrder.ASC, SortOrder.ASC_NULLS_FIRST, SortOrder.ASC_NULLS_LAST)

        return object : Iterable<Iterable<ResultRow>> {
            override fun iterator(): Iterator<Iterable<ResultRow>> {
                return iterator {
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
    // this will most likely need to be SUSPEND
    override fun count(): Long {
        return if (distinct || groupedByColumns.isNotEmpty() || limit != null || offset > 0) {
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
                    (rs.getObject(1) as? Number)?.toLong().also {
                        rs.close()
                    }
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
    // this will most likely need to be SUSPEND
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

    /** Represents the position at which an SQL comment will be added in a `SELECT` query. */
    enum class CommentPosition {
        /** The start of the query, before the keyword `SELECT`. */
        FRONT,

        /** The end of the query, after all clauses. */
        BACK
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
