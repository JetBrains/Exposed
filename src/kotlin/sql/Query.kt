package kotlin.sql

import java.sql.Connection
import java.util.HashSet
import java.util.ArrayList
import java.util.HashMap
import java.sql.ResultSet
import kotlin.properties.Delegates
import java.util.NoSuchElementException
import kotlin.dao.EntityCache
import org.joda.time.DateTime

public class ResultRow() {
    val data = HashMap<Field<*>, Any?>()

    [suppress("UNCHECKED_CAST")]
    fun <T> get(c: Field<T>) : T {
        val d:Any? = when {
            data.containsKey(c) -> data[c]
            else -> error("${c.toSQL(QueryBuilder(false))} is not in record set")
        }

        if (d == null) {
            return null as T
        }

        if (c is Column<*>) {
            val enumType = (c.columnType as? EnumerationColumnType<*>)?.klass
            if (enumType != null) {
                return enumType.getEnumConstants()!![d as Int] as T
            }
        }

        if (d is java.sql.Date) {
            return DateTime(d.getTime(), Database.timeZone) as T
        }

        if (d is java.sql.Timestamp) {
            return DateTime(d.getTime(), Database.timeZone) as T
        }

        if (d is java.sql.Clob) {
            return d.getCharacterStream().readText() as T
        }

        return d as T
    }

    fun<T> hasValue (c: Field<T>) : Boolean {
        return data.containsKey(c);
    }

    class object {
        fun create(rs: ResultSet, fields: List<Field<*>>): ResultRow {
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

    private val statement: String by Delegates.lazy {
        val sql = toSQL(QueryBuilder(false))
        log(sql)
        sql
    }

    private val countStatement: String by Delegates.lazy {
        val sql = toSQL(QueryBuilder(false), true)
        log(sql)
        sql
    }

    fun toSQL(queryBuilder: QueryBuilder, count: Boolean = false) : String {
        val sql = StringBuilder("SELECT ")

        with(sql) {
            if (count) {
                append("COUNT(*)")
            }
            else {
                append((set.fields map {it.toSQL(queryBuilder)}).makeString(", ", "", ""))
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
                    append((groupedByColumns map {session.fullIdentity(it)}).makeString(", ", "", ""))
                }

                if (having != null) {
                    append(" HAVING ")
                    append(having!!.toSQL(queryBuilder))
                }

                if (orderByColumns.size > 0) {
                    append(" ORDER BY ")
                    append((orderByColumns map { "${session.fullIdentity(it.first)} ${if(it.second) "ASC" else "DESC"}" }).makeString(", ", "", ""))
                }

                if (limit != null) {
                    append(" LIMIT ")
                    append(limit)
                }
            }
        }

        return sql.toString()
    }

    fun groupBy(vararg columns: Column<*>): Query {
        for (column in columns) {
            groupedByColumns.add(column)
        }
        return this
    }

    fun having (op: Op<Boolean>) : Query {
        if (having != null) {
            val fake = QueryBuilder(false)
            error ("HAVING clause is specified twice. Old value = '${having!!.toSQL(fake)}', new value = '${op.toSQL(fake)}'")
        }
        having = op;
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

    public override fun iterator(): Iterator<ResultRow> {
        // Flush data before executing query or results may be unpredictable
        EntityCache.getOrCreate(session).flush()

        val builder = QueryBuilder(true )
        return ResultIterator(builder.executeQuery(session, toSQL(builder)))
    }

    public override fun count(): Int {
        // Flush data before executing query or results may be unpredictable
        EntityCache.getOrCreate(session).flush()

        // Execute query itself
        val rs = session.connection.createStatement()?.executeQuery(countStatement)!!
        rs.next()
        return rs.getInt(1)
    }

    public override fun empty(): Boolean {
        // Flush data before executing query or results may be unpredictable
        EntityCache.getOrCreate(session).flush()

        val selectOneRowStatement = run {
            val oldLimit = limit
            try {
                limit = 1
                toSQL(QueryBuilder(false), false)
            } finally {
                limit = oldLimit
            }
        }
        // Execute query itself
        val rs = session.connection.createStatement()?.executeQuery(selectOneRowStatement)!!
        return !rs.next()
    }
}
