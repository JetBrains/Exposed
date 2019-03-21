package org.jetbrains.exposed.sql

import org.jetbrains.exposed.dao.IdTable
import org.jetbrains.exposed.sql.statements.Statement
import org.jetbrains.exposed.sql.statements.StatementType
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.vendors.currentDialect
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.util.*

class ResultRow(internal val fieldIndex: Map<Expression<*>, Int>) {
    private val data = arrayOfNulls<Any?>(fieldIndex.size)

    /**
     * Retrieves value of a given expression on this row.
     *
     * @param c expression to evaluate
     * @throws IllegalStateException if expression is not in record set or if result value is uninitialized
     *
     * @see [getOrNull] to get null in the cases an exception would be thrown
     */
    @Suppress("UNCHECKED_CAST")
    operator fun <T> get(c: Expression<T>): T {
        val d = getRaw(c)
        return when {
            d == null && c is Column<*> && c.dbDefaultValue != null && !c.columnType.nullable -> {
                exposedLogger.warn("Column ${TransactionManager.current().identity(c)} is marked as not null, " +
                            "has default db value, but returns null. Possible have to re-read it from DB.")
                null
            }
            d == null -> null
            d == NotInitializedValue -> error("${c.toSQL(QueryBuilder(false))} is not initialized yet")
            c is ExpressionAlias<T> && c.delegate is ExpressionWithColumnType<T> -> c.delegate.columnType.valueFromDB(d)
            c is ExpressionWithColumnType<T> -> c.columnType.valueFromDB(d)
            else -> d
        } as T
    }

    operator fun <T> set(c: Expression<out T>, value: T) {
        val index = fieldIndex[c] ?: error("${c.toSQL(QueryBuilder(false))} is not in record set")
        data[index] = value
    }

    fun <T> hasValue(c: Expression<T>): Boolean = fieldIndex[c]?.let{ data[it] != NotInitializedValue } ?: false

    fun <T> getOrNull(c: Expression<T>): T? = if (hasValue(c)) get(c) else null

    @Deprecated("Replaced with getOrNull to be more kotlinish", replaceWith = ReplaceWith("getOrNull(c)"))
    fun <T> tryGet(c: Expression<T>): T? = getOrNull(c)

    @Suppress("UNCHECKED_CAST")
    private fun <T> getRaw(c: Expression<T>): T? =
            data[fieldIndex[c] ?: error("${c.toSQL(QueryBuilder(false))} is not in record set")] as T?

    override fun toString(): String =
            fieldIndex.entries.joinToString { "${it.key.toSQL(QueryBuilder(false))}=${data[it.value]}" }

    internal object NotInitializedValue

    companion object {
        fun create(rs: ResultSet, fields: List<Expression<*>>): ResultRow {
            val fieldsIndex = fields.distinct().mapIndexed { i, field ->
                val value = (field as? Column<*>)?.columnType?.readObject(rs, i + 1) ?: rs.getObject(i + 1)
                (field to i) to value
            }.toMap()
            return ResultRow(fieldsIndex.keys.toMap()).apply {
                fieldsIndex.forEach{ (i, f) ->
                    data[i.second] = f
                }
            }
        }

        internal fun createAndFillValues(data: Map<Column<*>, Any?>) : ResultRow =
            ResultRow(data.keys.mapIndexed { i, c -> c to i }.toMap()).also { row ->
                data.forEach { (c, v) -> row[c] = v }
            }

        internal fun createAndFillDefaults(columns : List<Column<*>>): ResultRow =
            ResultRow(columns.mapIndexed { i, c -> c to i }.toMap()).apply {
                columns.forEach {
                    this[it] = it.defaultValueFun?.invoke() ?: if (!it.columnType.nullable) NotInitializedValue else null
                }
            }
    }
}

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
    var offset: Int = 0
        private set
    var fetchSize: Int? = null
        private set

    override fun copy(): SizedIterable<ResultRow> = Query(set, where).also { copy ->
        copy.groupedByColumns = groupedByColumns
        copy.orderByExpressions = orderByExpressions
        copy.having = having
        copy.distinct = distinct
        copy.forUpdate = forUpdate
        copy.limit = limit
        copy.offset = offset
        copy.fetchSize = fetchSize
    }

    /**
     * Changes [set.fields] field of a Query, [set.source] will be preserved
     * @param body builder for new column set, current [set.source] used as a receiver, you are expected to slice it
     * @sample org.jetbrains.exposed.sql.tests.shared.DMLTests.testAdjustQuerySlice
     */
    fun adjustSlice(body: ColumnSet.() -> FieldSet): Query = apply { set = set.source.body() }

    /**
     * Changes [set.source] field of a Query, [set.fields] will be preserved
     * @param body builder for new column set, previous value used as a receiver
     * @sample org.jetbrains.exposed.sql.tests.shared.DMLTests.testAdjustQueryColumnSet
     */
    fun adjustColumnSet(body: ColumnSet.() -> ColumnSet): Query {
        val oldSlice = set.fields
        return adjustSlice { body().slice(oldSlice) }
    }

    /**
     * Changes [where] field of a Query.
     * @param body new WHERE condition builder, previous value used as a receiver
     * @sample org.jetbrains.exposed.sql.tests.shared.DMLTests.testAdjustQueryWhere
     */
    fun adjustWhere(body: Op<Boolean>?.() -> Op<Boolean>): Query = apply { where = where.body() }

    fun hasCustomForUpdateState() = forUpdate != null
    fun isForUpdate() = (forUpdate ?: false) && currentDialect.supportsSelectForUpdate()

    override fun PreparedStatement.executeInternal(transaction: Transaction): ResultSet? {
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

    fun prepareSQL(builder: QueryBuilder): String = buildString {
        append("SELECT ")

        if (count) {
            append("COUNT(*)")
        }
        else {
            if (distinct) {
                append("DISTINCT ")
            }
            append(set.fields.joinToString {it.toSQL(builder)})
        }
        append(" FROM ")
        append(set.source.describe(transaction, builder))

        where?.let {
            append(" WHERE ")
            append(it.toSQL(builder))
        }

        if (!count) {
            if (groupedByColumns.isNotEmpty()) {
                append(" GROUP BY ")
                append(groupedByColumns.joinToString {
                    ((it as? ExpressionAlias)?.aliasOnlyExpression() ?: it).toSQL(builder)
                })
            }

            having?.let {
                append(" HAVING ")
                append(it.toSQL(builder))
            }

            if (orderByExpressions.isNotEmpty()) {
                append(" ORDER BY ")
                append(orderByExpressions.joinToString {
                    "${(it.first as? ExpressionAlias<*>)?.alias ?: it.first.toSQL(builder)} ${it.second.name}"
                })
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
            val fake = QueryBuilder(false)
            error ("HAVING clause is specified twice. Old value = '${having!!.toSQL(fake)}', new value = '${oop.toSQL(fake)}'")
        }
        having = oop
        return this
    }

    @Deprecated("use orderBy with SortOrder instead")
    @JvmName("orderByDeprecated")
    fun orderBy(column: Expression<*>, isAsc: Boolean) : Query = orderBy(column to isAsc)

    fun orderBy(column: Expression<*>, order: SortOrder = SortOrder.ASC) = orderBy(column to order)

    @Deprecated("use orderBy with SortOrder instead")
    @JvmName("orderByDeprecated2")
    fun orderBy(vararg columns: Pair<Expression<*>, Boolean>) : Query {
        (orderByExpressions as MutableList).addAll(columns.map{ it.first to if(it.second) SortOrder.ASC else SortOrder.DESC })
        return this
    }

    override fun orderBy(vararg columns: Pair<Expression<*>, SortOrder>) : Query {
        (orderByExpressions as MutableList).addAll(columns)
        return this
    }

    override fun limit(n: Int, offset: Int): Query {
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

        override operator fun next(): ResultRow {
            if (hasNext == null) hasNext()
            if (hasNext == false) throw NoSuchElementException()
            hasNext = null
            return ResultRow.create(rs, set.fields)
        }

        override fun hasNext(): Boolean {
            if (hasNext == null) hasNext = rs.next()
            if (hasNext == false) rs.close()
            return hasNext!!
        }
    }

    private fun flushEntities() {
        // Flush data before executing query or results may be unpredictable
        val tables = set.source.columns.map { it.table }.filterIsInstance(IdTable::class.java).toSet()
        transaction.entityCache.flush(tables)
    }

    override operator fun iterator(): Iterator<ResultRow> {
        flushEntities()
        val resultIterator = ResultIterator(transaction.exec(this)!!)
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
    override fun count(): Int {
        flushEntities()

        return if (distinct || groupedByColumns.isNotEmpty() || limit != null) {
            fun Column<*>.makeAlias() = alias(transaction.quoteIfNecessary("${table.tableName}_$name"))

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
                    it.getInt(1)
                }!!
            } finally {
                count = false
            }
        }
    }

    override fun empty(): Boolean {
        flushEntities()

        val oldLimit = limit
        try {
            limit = 1
            return !transaction.exec(this)!!.next()
        } finally {
            limit = oldLimit
        }
    }
}

fun Query.andWhere(andPart: SqlExpressionBuilder.() -> Op<Boolean>) = adjustWhere {
    val expr = Op.build { andPart() }
    if(this == null) expr
    else this and expr
}