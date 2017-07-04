package org.jetbrains.exposed.sql

import org.jetbrains.exposed.dao.IdTable
import org.jetbrains.exposed.sql.statements.Statement
import org.jetbrains.exposed.sql.statements.StatementType
import org.jetbrains.exposed.sql.transactions.TransactionManager
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.util.*

class ResultRow(size: Int, private val fieldIndex: Map<Expression<*>, Int>) {
    val data = arrayOfNulls<Any?>(size)

    /**
     * Function might returns null. Use @tryGet if you don't sure of nullability (e.g. in left-join cases)
     */
    @Suppress("UNCHECKED_CAST")
    operator fun <T> get(c: Expression<T>) : T {
        val d:Any? = getRaw(c)

        return d?.let {
            if (d == NotInitializedValue) error("${c.toSQL(QueryBuilder(false))} is not initialized yet")
            (c as? ExpressionWithColumnType<T>)?.columnType?.valueFromDB(it) ?: it ?: run {
                val column = c as? Column<T>
                if (column?.dbDefaultValue != null && column.columnType.nullable == false) {
                    exposedLogger.warn("Column ${TransactionManager.current().identity(column)} is marked as not null, " +
                            "has default db value, but returns null. Possible have to re-read it from DB.")
                }
                null
            }
        } as T
    }

    operator fun <T> set(c: Expression<T>, value: T) {
        val index = fieldIndex[c] ?: error("${c.toSQL(QueryBuilder(false))} is not in record set")
        data[index] = value
    }

    fun<T> hasValue (c: Expression<T>) : Boolean {
        return fieldIndex[c]?.let{ data[it] != NotInitializedValue } ?: false
    }

    fun <T> tryGet(c: Expression<T>): T? {
        return if (hasValue(c)) get(c) else null
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> getRaw(c: Expression<T>): T? {
        return data[fieldIndex[c] ?: error("${c.toSQL(QueryBuilder(false))} is not in record set")] as T?
    }

    override fun toString(): String {
        return fieldIndex.map { "${it.key.toSQL(QueryBuilder(false))}=${data[it.value]}" }.joinToString()
    }

    internal object NotInitializedValue

    companion object {
        fun create(rs: ResultSet, fields: List<Expression<*>>, fieldsIndex: Map<Expression<*>, Int>) : ResultRow {
            val size = fieldsIndex.size
            val answer = ResultRow(size, fieldsIndex)

            fields.forEachIndexed { i, f ->
                answer.data[i] = (f as? Column<*>)?.columnType?.readObject(rs, i + 1) ?: rs.getObject(i + 1)
            }
            return answer
        }

        internal fun create(columns : List<Column<*>>): ResultRow =
            ResultRow(columns.size, columns.mapIndexed { i, c -> c to i }.toMap()).apply {
                columns.forEach {
                    this[it] = it.defaultValueFun?.invoke() ?: if (!it.columnType.nullable) NotInitializedValue else null
                }
            }
    }
}

open class Query(val transaction: Transaction, set: FieldSet, where: Op<Boolean>?): SizedIterable<ResultRow>, Statement<ResultSet>(StatementType.SELECT, set.source.targetTables()) {
    var groupedByColumns: List<Expression<*>> = mutableListOf()
        private set
    var orderByColumns: List<Pair<Expression<*>, Boolean>> = mutableListOf()
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
    fun isForUpdate() = (forUpdate ?: transaction.selectsForUpdate) && transaction.db.dialect.supportsSelectForUpdate()

    override fun PreparedStatement.executeInternal(transaction: Transaction): ResultSet? = executeQuery()

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
        append(set.source.describe(transaction))

        where?.let {
            append(" WHERE ")
            append(it.toSQL(builder))
        }

        if (!count) {
            if (groupedByColumns.isNotEmpty()) {
                append(" GROUP BY ")
                append((groupedByColumns.map {it.toSQL(builder)}).joinToString())
            }

            having?.let {
                append(" HAVING ")
                append(it.toSQL(builder))
            }

            if (orderByColumns.isNotEmpty()) {
                append(" ORDER BY ")
                append(orderByColumns.joinToString {
                    "${(it.first as? ExpressionAlias<*>)?.alias ?: it.first.toSQL(builder)} ${if(it.second) "ASC" else "DESC"}"
                })
            }

            limit?.let {
                append(" ")
                append(transaction.db.dialect.limit(it, offset))
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

    fun having (op: SqlExpressionBuilder.() -> Op<Boolean>) : Query {
        val oop = Op.build { op() }
        if (having != null) {
            val fake = QueryBuilder(false)
            error ("HAVING clause is specified twice. Old value = '${having!!.toSQL(fake)}', new value = '${oop.toSQL(fake)}'")
        }
        having = oop
        return this
    }

    fun orderBy (column: Expression<*>, isAsc: Boolean = true) : Query {
        (orderByColumns as MutableList).add(column to isAsc)
        return this
    }

    fun orderBy (vararg columns: Pair<Column<*>,Boolean>) : Query {
        for (pair in columns) {
            (orderByColumns as MutableList).add(pair)
        }
        return this
    }

    override fun limit(n: Int, offset: Int): Query {
        this.limit = n
        this.offset = offset
        return this
    }

    private inner class ResultIterator(val rs: ResultSet): Iterator<ResultRow> {
        private var hasNext: Boolean? = null
        private val fieldsIndex = HashMap<Expression<*>, Int>()

        init {
            set.fields.forEachIndexed { idx, field ->
                fieldsIndex[field] = idx
            }
        }

        operator override fun next(): ResultRow {
            if (hasNext == null) hasNext()
            if (hasNext == false) throw NoSuchElementException()
            hasNext = null
            return ResultRow.create(rs, set.fields, fieldsIndex)
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

    operator override fun iterator(): Iterator<ResultRow> {
        flushEntities()
        return ResultIterator(transaction.exec(this)!!)
    }

    private var count: Boolean = false
    override fun count(): Int {
        flushEntities()

        if (distinct) {
            return this.alias("subq").selectAll().count()
        }

        return try {
            count = true
            transaction.exec(this) {
                it.next()
                it.getInt(1)
            }!!
        }  finally {
            count = false
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
