package kotlinx.sql

import java.util.HashSet
import java.util.ArrayList
import java.util.HashMap
import java.sql.ResultSet
import java.util.NoSuchElementException
import kotlinx.dao.EntityCache
import java.util.LinkedHashSet
import kotlinx.dao.IdTable

public class ResultRow() {
    val data = HashMap<Expression<*>, Any?>()

    [suppress("UNCHECKED_CAST")]
    fun <T> get(c: Expression<T>) : T {
        val d:Any? = when {
            data.containsKey(c) -> data[c]
            else -> error("${c.toSQL(QueryBuilder(false))} is not in record set")
        }

        if (d == null) {
            return null as T
        }

        if (c is ExpressionWithColumnType<*>) {
            return c.columnType.valueFromDB(d) as T
        }

        return d as T
    }

    fun<T> hasValue (c: Expression<T>) : Boolean {
        return data.get(c) != null;
    }

    class object {
        fun create(rs: ResultSet, fields: List<Expression<*>>): ResultRow {
            val answer = ResultRow()
            fields.forEachWithIndex { (i, f) ->
                answer.data[f] = when {
                    f is Column<*> && f.columnType is BlobColumnType -> rs.getBlob(i + 1)
                    else -> rs.getObject(i + 1)
                }
            }
            return answer
        }
    }
}

open class Query(val session: Session, val set: FieldSet, val where: Op<Boolean>?): SizedIterable<ResultRow> {
    var selectedColumns = HashSet<Column<*>>();
    val groupedByColumns = ArrayList<Column<*>>();
    val orderByColumns = ArrayList<Pair<Column<*>, Boolean>>();
    var having: Op<Boolean>? = null;
    var limit: Int? = null
    var forUpdate: Boolean = session.selectsForUpdate


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
/* // Do not pretty print with * co the program won't crash on new column added
                for (table in tables) {
                    if (fields.containsAll(table.columns)) {
                        completeTables.add(table)
                        fields.removeAll(table.columns)
                    }
                }
*/

                append(((completeTables.map {Session.get().identity(it) + ".*"} ) + (fields map {it.toSQL(queryBuilder)})).join(", ", "", ""))
            }
            append(" FROM ")
            append(set.source.describe(session))

            if (where != null) {
                append(" WHERE ")
                append(where.toSQL(queryBuilder))
            }

            if (!count) {
                if (groupedByColumns.size > 0) {
                    append(" GROUP BY ")
                    append((groupedByColumns map {session.fullIdentity(it)}).join(", ", "", ""))
                }

                if (having != null) {
                    append(" HAVING ")
                    append(having!!.toSQL(queryBuilder))
                }

                if (orderByColumns.size > 0) {
                    append(" ORDER BY ")
                    append((orderByColumns map { "${session.fullIdentity(it.first)} ${if(it.second) "ASC" else "DESC"}" }).join(", ", "", ""))
                }

                if (limit != null) {
                    append(" LIMIT ")
                    append(limit)
                }
            }

            if (forUpdate) {
                append(" FOR UPDATE")
            }
        }

        return sql.toString()
    }

    fun forUpdate() : Query {
        this.forUpdate = true
        return this
    }

    fun groupBy(vararg columns: Column<*>): Query {
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

    fun orderBy (column: Column<*>, isAsc: Boolean = true) : Query {
        orderByColumns.add(column to isAsc)
        return this
    }

    fun orderBy (vararg columns: Pair<Column<*>,Boolean>) : Query {
        for (pair in columns) {
            orderByColumns.add(pair)
        }
        return this
    }

    fun limit(limit: Int): Query {
        this.limit = limit
        return this
    }

    private inner class ResultIterator(val rs: ResultSet): Iterator<ResultRow> {
        private var hasNext: Boolean? = null

        public override fun next(): ResultRow {
            if (hasNext == null) hasNext()
            if (hasNext == false) throw NoSuchElementException()
            hasNext = null
            return ResultRow.create(rs, set.fields)
        }

        public override fun hasNext(): Boolean {
            if (hasNext == null) hasNext = rs.next()
            return hasNext!!
        }
    }

    private fun flushEntities() {
        // Flush data before executing query or results may be unpredictable
        val tables = set.source.columns.map { it.table }.filterIsInstance(javaClass<IdTable>()).toSet()
        EntityCache.getOrCreate(session).flush(tables)
    }

    public override fun iterator(): Iterator<ResultRow> {
        flushEntities()
        val builder = QueryBuilder(true)
        val sql = toSQL(builder)
        return ResultIterator(builder.executeQuery(session, sql))
    }

    public override fun count(): Int {
        flushEntities()

        val builder = QueryBuilder(true)
        val sql = toSQL(builder, true)

        val rs = builder.executeQuery(session, sql)
        rs.next()
        return rs.getInt(1)
    }

    public override fun empty(): Boolean {
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
        val rs = builder.executeQuery(session, selectOneRowStatement)
        return !rs.next()
    }
}
