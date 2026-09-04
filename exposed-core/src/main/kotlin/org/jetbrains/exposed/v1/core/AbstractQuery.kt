package org.jetbrains.exposed.v1.core

import org.jetbrains.exposed.v1.core.statements.Statement
import org.jetbrains.exposed.v1.core.statements.StatementType
import org.jetbrains.exposed.v1.core.statements.api.ResultApi
import org.jetbrains.exposed.v1.core.transactions.currentTransaction
import org.jetbrains.exposed.v1.core.vendors.ForUpdateOption
import org.jetbrains.exposed.v1.core.vendors.MariaDBDialect
import org.jetbrains.exposed.v1.core.vendors.MysqlDialect
import org.jetbrains.exposed.v1.core.vendors.RecursiveCteSyntax
import org.jetbrains.exposed.v1.core.vendors.currentDialect
import org.jetbrains.exposed.v1.exceptions.UnsupportedByDialectException

@Suppress("ForbiddenComment")
// TODO: consider naming this as QueryState (or something related to state of the query) and check that it has only single responsibility
/** Base class representing an SQL query that returns a database result when executed. */
abstract class AbstractQuery<T : AbstractQuery<T>>(
    targets: List<Table>
) : Statement<ResultApi>(StatementType.SELECT, targets) {
    /** The stored list of columns and their [SortOrder] for an `ORDER BY` clause in this query. */
    var orderByExpressions: List<Pair<Expression<*>, SortOrder>> = mutableListOf()
        private set

    /** The stored value for a `LIMIT` clause in this query. */
    var limit: Int? = null
        protected set

    /** The stored value for an `OFFSET` clause in this query. */
    var offset: Long = 0
        protected set

    /** The number of results that should be fetched when this query is executed. */
    var fetchSize: Int? = null
        private set

    /** The set of columns on which a query should be executed, contained by a [ColumnSet]. */
    abstract val set: FieldSet

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

    protected var forUpdate: ForUpdateOption? = null

    /** The stored comments and their [CommentPosition]s in this `SELECT` query. */
    var comments: Map<CommentPosition, String> = mutableMapOf()
        private set

    private var commonTableExpressions: List<CommonTableExpression> = emptyList()

    /**
     * Copies all stored properties of this `SELECT` query into the properties of [other].
     *
     * Override this function to additionally copy any properties that are not part of the primary constructor.
     */
    open fun copyTo(other: T) {
        other.orderByExpressions = orderByExpressions.toMutableList()
        other.limit = limit
        other.offset = offset
        other.fetchSize = fetchSize
        other.distinct = distinct
        other.distinctOn = distinctOn
        other.groupedByColumns = groupedByColumns.toMutableList()
        other.having = having
        other.forUpdate = forUpdate
        other.comments = comments.toMutableMap()
        other.commonTableExpressions = commonTableExpressions.toList()
    }

    /** Attaches [ctes] to this outermost query in declaration order. */
    fun withCtes(vararg ctes: CommonTableExpression): T = apply {
        require(ctes.isNotEmpty()) { "At least one CTE must be provided" }
        require(ctes.distinct().size == ctes.size) { "The same CTE cannot be attached more than once" }
        require(commonTableExpressions.none { it in ctes }) { "The same CTE cannot be attached more than once" }
        commonTableExpressions = commonTableExpressions + ctes
    } as T

    /** Creates an immutable snapshot suitable for use as a CTE definition. */
    @InternalApi
    open fun snapshotForCte(): AbstractQuery<*> = error(
        "${this::class.simpleName} must override snapshotForCte() before it can be used as a CTE definition"
    )

    /** Returns whether this query owns an attached `WITH` clause. */
    @InternalApi
    protected fun hasCommonTableExpressions(): Boolean = commonTableExpressions.isNotEmpty()

    /** Returns whether the field at [index] has a framework-generated alias. */
    @InternalApi
    open fun isGeneratedCteField(index: Int): Boolean = false

    /** Runs [body] while this query's attached CTEs are temporarily detached. */
    @InternalApi
    protected fun <R> withCommonTableExpressionsDetached(body: (List<CommonTableExpression>) -> R): R {
        val originalCtes = commonTableExpressions
        commonTableExpressions = emptyList()
        return try {
            body(originalCtes)
        } finally {
            commonTableExpressions = originalCtes
        }
    }

    /** Runs suspending [body] while this query's attached CTEs are temporarily detached. */
    @InternalApi
    protected suspend fun <R> withCommonTableExpressionsDetachedSuspending(
        body: suspend (List<CommonTableExpression>) -> R
    ): R {
        val originalCtes = commonTableExpressions
        commonTableExpressions = emptyList()
        return try {
            body(originalCtes)
        } finally {
            commonTableExpressions = originalCtes
        }
    }

    /** Verifies that this query may be rendered as a nested query. */
    internal fun validateAsSubquery() {
        require(commonTableExpressions.isEmpty()) {
            "A query owning a WITH clause cannot be used as a subquery; attach its CTEs to the outermost query instead"
        }
    }

    /** Verifies that this query may be stored as a CTE definition. */
    internal fun validateAsCteDefinition(name: String) {
        require(commonTableExpressions.isEmpty()) {
            "CTE '$name' definition already owns a WITH clause; attach all CTEs to the outermost query instead"
        }
    }

    /** Renders this query as a nested query after enforcing CTE ownership rules. */
    @InternalApi
    fun prepareAsSubquerySQL(builder: QueryBuilder): String {
        validateAsSubquery()
        return prepareSQL(builder)
    }

    /** Renders this query as a nested query after enforcing CTE ownership rules. */
    internal fun prepareAsSubquerySQL(transaction: Transaction, prepared: Boolean): String {
        validateAsSubquery()
        return prepareSQL(transaction, prepared)
    }

    override fun arguments() = QueryBuilder(true).let {
        prepareSQL(it)
        if (it.args.isNotEmpty()) listOf(it.args) else emptyList()
    }

    /** Modifies this query to retrieve only distinct results if [value] is set to `true`. */
    open fun withDistinct(value: Boolean = true): T = apply {
        if (value) {
            require(distinctOn == null) { "DISTINCT cannot be used with the DISTINCT ON modifier. Only one of them should be applied." }
        }
        distinct = value
    } as T

    /** Modifies the number of results that should be fetched when this query is executed. */
    fun fetchSize(n: Int): T = apply {
        fetchSize = n
    } as T

    /** The stored condition for a `WHERE` clause in this `SELECT` query. */
    var where: Op<Boolean>? = null
        protected set

    /**
     * Specifies that the `SELECT` query should retrieve distinct results based on the given list of columns.
     *
     * This method can be used to set a `DISTINCT ON` clause for the query, which is supported by some SQL dialects
     * (e.g., PostgreSQL, H2). The resulting query will retrieve rows that are distinct based on the specified columns.
     *
     * @param columns The columns to apply the `DISTINCT ON` clause.
     * @return The current `Query` instance with the `DISTINCT ON` clause applied.
     */
    fun withDistinctOn(vararg columns: Column<*>): T = apply {
        if (columns.isEmpty()) return@apply

        require(!distinct) { "DISTINCT ON cannot be used with the DISTINCT modifier. Only one of them should be applied." }
        distinctOn = (distinctOn ?: emptyList()) + columns
    } as T

    protected var count: Boolean = false

    /**
     * Changes the [having] field of this query.
     *
     * @param body Builder for the new `HAVING` condition, with the previous value used as the receiver.
     * @sample org.jetbrains.exposed.v1.tests.shared.dml.AdjustQueryTests.testAdjustQueryHaving
     */
    fun adjustHaving(body: Op<Boolean>?.() -> Op<Boolean>): T = apply { having = having.body() } as T

    /**
     * Changes the [content] of the [comments] field at the specified [position] in this query.
     *
     * @param position The [CommentPosition] in the query that should be assigned a new value.
     * @param content The content of the comment that should be set. If left `null`, any comment at the specified
     * [position] will be removed.
     * @sample org.jetbrains.exposed.v1.tests.shared.dml.SelectTests.testSelectWithComment
     */
    fun adjustComments(position: CommentPosition, content: String? = null): T = apply {
        content?.let {
            (comments as MutableMap)[position] = content
        } ?: run {
            (comments as MutableMap).remove(position)
        }
    } as T

    /** Whether this `SELECT` query already has a stored value option for performing locking reads. */
    fun hasCustomForUpdateState() = forUpdate != null

    /**
     * Whether this `SELECT` query will perform a locking read.
     *
     * **Note:** `SELECT FOR UPDATE` is not supported by all vendors. Please check the documentation.
     */
    fun isForUpdate(): Boolean = (
        @OptIn(InternalApi::class)
        forUpdate?.let { it != ForUpdateOption.NoForUpdateOption }
            ?: false
        )

    /**
     * Appends a `GROUP BY` clause with the specified [columns] to this `SELECT` query.
     *
     * @sample org.jetbrains.exposed.v1.tests.shared.dml.GroupByTests.testGroupBy02
     */
    fun groupBy(vararg columns: Expression<*>): T {
        for (column in columns) {
            (groupedByColumns as MutableList).add(column)
        }
        return this as T
    }

    /**
     * Appends a `HAVING` clause with the specified [op] condition to this `SELECT` query.
     *
     * @sample org.jetbrains.exposed.v1.tests.shared.dml.GroupByTests.testGroupBy02
     */
    fun having(op: () -> Op<Boolean>): T {
        val oop = op()
        if (having != null) {
            error("HAVING clause is specified twice. Old value = '$having', new value = '$oop'")
        }
        having = oop
        return this as T
    }

    /**
     * Appends an SQL comment, with [content] wrapped by `/* */`, at the specified [CommentPosition] in this `SELECT` query.
     *
     * Adding some comments may be useful for tracking, embedding metadata, or for special instructions, like using
     * `/*FORCE_MASTER*/` for some cloud databases to force the statement to run in the master database
     * or using optimizer hints.
     *
     * @throws IllegalStateException If a comment has already been appended at the specified [position]. An existing
     * comment can be removed or altered by [adjustComments].
     * @sample org.jetbrains.exposed.v1.tests.shared.dml.SelectTests.testSelectWithComment
     */
    fun comment(content: String, position: CommentPosition = CommentPosition.FRONT): T {
        comments[position]?.let {
            error("Comment at $position position is specified twice. Old value = '$it', new value = '$content'")
        }
        (comments as MutableMap)[position] = content
        return this as T
    }

    override fun prepareSQL(transaction: Transaction, prepared: Boolean) = prepareSQL(QueryBuilder(prepared))

    /** Returns the string representation of an SQL query, generated by appending SQL expressions to a [QueryBuilder]. **/
    open fun prepareSQL(builder: QueryBuilder): String {
        require(set.fields.isNotEmpty()) { "Can't prepare SELECT statement without columns or expressions to retrieve" }

        builder {
            appendStatementPreamble(this)

            append("SELECT ")

            comments[CommentPosition.AFTER_SELECT]?.let { comment ->
                append("/*$comment*/ ")
            }

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
            @OptIn(InternalApi::class)
            if (set.source != Table.Dual || currentDialect.supportsDualTableConcept) {
                append(" FROM ")
                set.source.describe(currentTransaction(), this)
            }

            where?.let {
                append(" WHERE ")
                +it
            }

            if (!count) {
                if (groupedByColumns.isNotEmpty()) {
                    append(" GROUP BY ")
                    groupedByColumns.appendTo {
                        +((it as? IExpressionAlias<*>)?.aliasOnlyExpression() ?: it)
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

    /** Appends the front comment and attached CTE definitions. */
    protected fun appendStatementPreamble(builder: QueryBuilder) {
        builder {
            comments[CommentPosition.FRONT]?.let { comment -> append("/*$comment*/ ") }
            appendCommonTableExpressions(this)
        }
    }

    /** Appends attached CTE definitions without emitting comments. */
    @OptIn(InternalApi::class)
    protected fun appendCommonTableExpressions(builder: QueryBuilder) {
        if (commonTableExpressions.isEmpty()) return

        val dialect = currentDialect
        if (!dialect.supportsCommonTableExpressions) {
            val message = when (dialect) {
                is MariaDBDialect -> "MariaDB 10.2 or newer is required for common table expressions"
                is MysqlDialect -> "MySQL 8.0 or newer is required for common table expressions"
                else -> "Common table expressions are unsupported"
            }
            throw UnsupportedByDialectException(message, dialect)
        }
        if (commonTableExpressions.any { it.recursive } && !dialect.supportsRecursiveCommonTableExpressions) {
            throw UnsupportedByDialectException("Recursive common table expressions are unsupported", dialect)
        }

        val identifierManager = currentTransaction().db.identifierManager
        val normalizedCteNames = commonTableExpressions.map { identifierManager.inProperCase(it.name) }
        require(normalizedCteNames.distinct().size == normalizedCteNames.size) {
            "A query cannot attach CTEs with duplicate names"
        }
        builder.registerCommonTableExpressions(commonTableExpressions)

        builder {
            append("WITH")
            if (commonTableExpressions.any { it.recursive } && dialect.recursiveCteSyntax == RecursiveCteSyntax.RECURSIVE_KEYWORD) {
                append(" RECURSIVE")
            }
            append(' ')
            commonTableExpressions.appendTo { cte ->
                cte.validatePhysicalSchema()
                val normalizedOutputNames = cte.outputNames.map { identifierManager.inProperCase(it) }
                require(normalizedOutputNames.distinct().size == normalizedOutputNames.size) {
                    "CTE '${cte.name}' contains duplicate output names"
                }
                append(identifierManager.quoteIdentifierWhenWrongCaseOrNecessary(cte.name))
                cte.outputNames.appendTo(prefix = " (", postfix = ")") { outputName ->
                    append(identifierManager.quoteIdentifierWhenWrongCaseOrNecessary(outputName))
                }
                append(" AS (")
                cte.definition.prepareAsSubquerySQL(this)
                append(')')
            }
            append(' ')
        }
    }

    /** Represents the position at which an SQL comment will be added in a `SELECT` query. */
    enum class CommentPosition {
        /** The start of the query, before the keyword `SELECT`. */
        FRONT,

        /** Immediately following the keyword `SELECT`. */
        AFTER_SELECT,

        /** The end of the query, after all clauses. */
        BACK
    }
}
