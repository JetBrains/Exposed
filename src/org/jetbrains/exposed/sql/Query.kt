package org.jetbrains.exposed.sql

import java.sql.ResultSet
import java.util.*
import org.jetbrains.exposed.dao.EntityCache
import org.jetbrains.exposed.dao.IdTable

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
            (c as? ExpressionWithColumnType<*>)?.columnType?.valueFromDB(it) ?: it
        } as T
    }

    operator fun <T> set(c: Expression<T>, value: T) {
        val index = fieldIndex[c] ?: error("${c.toSQL(QueryBuilder(false))} is not in record set")
        data[index] = value
    }

    fun<T> hasValue (c: Expression<T>) : Boolean {
        return fieldIndex[c]?.let{ data[it] != NotInitializedValue } ?: false;
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

    internal object NotInitializedValue;

    companion object {
        fun create(rs: ResultSet, fields: List<Expression<*>>, fieldsIndex: Map<Expression<*>, Int>) : ResultRow {
            val size = fieldsIndex.size
            val answer = ResultRow(size, fieldsIndex)

            fields.forEachIndexed{ i, f ->
                answer.data[i]  = when {
                    f is Column<*> && f.columnType is BlobColumnType -> rs.getBlob(i + 1)
                    else -> rs.getObject(i + 1)
                }
            }
            return answer
        }

        internal fun create(columns : List<Column<*>>): ResultRow =
            ResultRow(columns.size, columns.mapIndexed { i, c -> c to i }.toMap()).apply {
                columns.forEach {
                    this[it] = it.defaultValue ?: if (!it.columnType.nullable) NotInitializedValue else null
                }
            }
    }
}

open class Query(val transaction: Transaction, val set: FieldSet, val where: Op<Boolean>?): SizedIterable<ResultRow> {
    val groupedByColumns = ArrayList<Expression<*>>();
    val orderByColumns = ArrayList<Pair<Expression<*>, Boolean>>();
    var having: Op<Boolean>? = null;
    var limit: Int? = null
    var forUpdate: Boolean = transaction.selectsForUpdate && transaction.db.vendorSupportsForUpdate()


    fun toSQL(queryBuilder: QueryBuilder, count: Boolean = false) : String {
        val sql = StringBuilder("SELECT ")

        with(sql) {
            if (count) {
                append("COUNT(*)")
            }
            else {
                val tables = set.source.columns.map { it.table }.toSet()
                val fields = LinkedHashSet(set.fields)
                val completeTables = ArrayList<Table>()
/*              // Do not pretty print with * co the program won't crash on new column added
                for (table in tables) {
                    if (fields.containsAll(table.columns)) {
                        completeTables.add(table)
                        fields.removeAll(table.columns)
                    }
                }
*/

                append(((completeTables.map { Transaction.current().identity(it) + ".*"} ) + (fields.map {it.toSQL(queryBuilder)})).joinToString())
            }
            append(" FROM ")
            append(set.source.describe(transaction))

            if (where != null) {
                append(" WHERE ")
                append(where.toSQL(queryBuilder))
            }

            if (!count) {
                if (groupedByColumns.isNotEmpty()) {
                    append(" GROUP BY ")
                    append((groupedByColumns.map {it.toSQL(queryBuilder)}).joinToString())
                }

                if (having != null) {
                    append(" HAVING ")
                    append(having!!.toSQL(queryBuilder))
                }

                if (orderByColumns.isNotEmpty()) {
                    append(" ORDER BY ")
                    append((orderByColumns.map { "${it.first.toSQL(queryBuilder)} ${if(it.second) "ASC" else "DESC"}" }).joinToString())
                }

                if (limit != null) {
                    append(" LIMIT ")
                    append(limit!!)
                }
            }

            if (forUpdate) {
                append(" FOR UPDATE")
            }
        }

        return sql.toString()
    }

    override fun forUpdate() : Query {
        this.forUpdate = true
        return this
    }

    override fun notForUpdate(): Query {
        forUpdate = false
        return this
    }

    infix fun groupBy(vararg columns: Expression<*>): Query {
        for (column in columns) {
            groupedByColumns.add(column)
        }
        return this
    }

    fun having (op: SqlExpressionBuilder.() -> Op<Boolean>) : Query {
        val oop = Op.build { op() }
        if (having != null) {
            val fake = QueryBuilder(false)
            error ("HAVING clause is specified twice. Old value = '${having!!.toSQL(fake)}', new value = '${oop.toSQL(fake)}'")
        }
        having = oop;
        return this;
    }

    fun orderBy (column: Expression<*>, isAsc: Boolean = true) : Query {
        orderByColumns.add(column to isAsc)
        return this
    }

    fun orderBy (vararg columns: Pair<Column<*>,Boolean>) : Query {
        for (pair in columns) {
            orderByColumns.add(pair)
        }
        return this
    }

    infix override fun limit(n: Int): Query {
        this.limit = n
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
            return hasNext!!
        }
    }

    private fun flushEntities() {
        // Flush data before executing query or results may be unpredictable
        val tables = set.source.columns.map { it.table }.filterIsInstance(IdTable::class.java).toSet()
        EntityCache.getOrCreate(transaction).flush(tables)
    }

    operator override fun iterator(): Iterator<ResultRow> {
        flushEntities()
        val builder = QueryBuilder(true)
        val sql = toSQL(builder)
        return ResultIterator(builder.executeQuery(transaction, sql))
    }

    override fun count(): Int {
        flushEntities()

        val builder = QueryBuilder(true)
        val sql = toSQL(builder, true)

        val rs = builder.executeQuery(transaction, sql)
        rs.next()
        return rs.getInt(1)
    }

    override fun empty(): Boolean {
        flushEntities()
        val builder = QueryBuilder(true)

        val selectOneRowStatement = run {
            val oldLimit = limit
            try {
                limit = 1
                toSQL(builder, false)
            } finally {
                limit = oldLimit
            }
        }
        // Execute query itself
        val rs = builder.executeQuery(transaction, selectOneRowStatement)
        return !rs.next()
    }
}
