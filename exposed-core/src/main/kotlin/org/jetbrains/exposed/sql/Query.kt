package org.jetbrains.exposed.sql

import org.jetbrains.exposed.sql.statements.Statement
import org.jetbrains.exposed.sql.statements.api.PreparedStatementApi
import org.jetbrains.exposed.sql.vendors.currentDialect
import java.sql.ResultSet
import java.util.*

enum class SortOrder(val code: String, val requiresNullsFirstLastSupport: Boolean = false) {
    ASC(code = "ASC"),
    DESC(code = "DESC"),
    ASC_NULLS_FIRST(code = "ASC NULLS FIRST", requiresNullsFirstLastSupport = true),
    DESC_NULLS_FIRST(code = "DESC NULLS FIRST", requiresNullsFirstLastSupport = true),
    ASC_NULLS_LAST(code = "ASC NULLS LAST", requiresNullsFirstLastSupport = true),
    DESC_NULLS_LAST(code = "DESC NULLS LAST", requiresNullsFirstLastSupport = true)
}

open class Query(override var set: FieldSet, where: Op<Boolean>?) : AbstractQuery<Query>(set.source.targetTables()) {

    var groupedByColumns: List<Expression<*>> = mutableListOf()
        private set

    var having: Op<Boolean>? = null
        private set

    private var forUpdate: Boolean? = null

    // private set
    var where: Op<Boolean>? = where
        private set

    override val queryToExecute: Statement<ResultSet>
        get() {
            val distinctExpressions = set.fields.distinct()
            return if (distinctExpressions.size < set.fields.size) {
                copy().adjustSlice { slice(distinctExpressions) }
            } else
                this
        }

    override fun copy(): Query = Query(set, where).also { copy ->
        copyTo(copy)
        copy.groupedByColumns = groupedByColumns.toMutableList()
        copy.having = having
        copy.forUpdate = forUpdate
    }

    override fun forUpdate(): Query {
        this.forUpdate = true
        return this
    }

    override fun notForUpdate(): Query {
        forUpdate = false
        return this
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

    override fun prepareSQL(builder: QueryBuilder): String {
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
                        if (sortOrder.requiresNullsFirstLastSupport && !currentDialect.supportsOrderByNullsFirstLast) {
                            error(
                                """Sort order '${sortOrder.name}' requires extended ORDER BY support,
                                | but the current dialect '${currentDialect.name}' does not support this"""
                                    .trimMargin()
                            )
                        }
                        append((expression as? ExpressionAlias<*>)?.alias ?: expression, " ", sortOrder.code)
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

    fun groupBy(vararg columns: Expression<*>): Query {
        for (column in columns) {
            (groupedByColumns as MutableList).add(column)
        }
        return this
    }

    fun having(op: SqlExpressionBuilder.() -> Op<Boolean>): Query {
        val oop = SqlExpressionBuilder.op()
        if (having != null) {
            error("HAVING clause is specified twice. Old value = '$having', new value = '$oop'")
        }
        having = oop
        return this
    }

    override fun count(): Long {
        return if (distinct || groupedByColumns.isNotEmpty() || limit != null) {
            fun Column<*>.makeAlias() =
                alias(transaction.db.identifierManager.quoteIfNecessary("${table.tableName}_$name"))

            val originalSet = set
            try {
                var expInx = 0
                adjustSlice {
                    slice(
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

    override fun empty(): Boolean {
        val oldLimit = limit
        try {
            if (!isForUpdate())
                limit = 1
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
    if (this == null) expr
    else this and expr
}

/**
 * Mutate Query instance and add `andPart` to where condition with `or` operator.
 * @return same Query instance which was provided as a receiver.
 */
fun Query.orWhere(andPart: SqlExpressionBuilder.() -> Op<Boolean>) = adjustWhere {
    val expr = Op.build { andPart() }
    if (this == null) expr
    else this or expr
}
