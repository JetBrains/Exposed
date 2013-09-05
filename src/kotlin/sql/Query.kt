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

public class ResultRow(val rs: ResultSet, fields: List<Field<*>>) {
    val data = HashMap<Field<*>, Any?>();
    {
        fields.forEachWithIndex { (i, f) -> data[f] = rs.getObject(i + 1) }
    }

    fun <T> get(c: Field<T>) : T {
        val d:Any? = when {
            data.containsKey(c) -> data[c]
            else -> throw RuntimeException("${c.toSQL()} is not in record set")
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
            return DateTime(d.getTime()) as T
        }

        return d as T
    }

    fun<T> hasValue (c: Field<T>) : Boolean {
        return data.containsKey(c);
    }
}

open class Query(val session: Session, val set: FieldSet, val where: Op?): Iterable<ResultRow> {
    var selectedColumns = HashSet<Column<*>>();
    val groupedByColumns = ArrayList<Column<*>>();
    val orderByColumns = ArrayList<Pair<Column<*>, Boolean>>();
    var having: Op? = null;

    private val statement: String by Delegates.lazy {
        val sql = StringBuilder("SELECT ")

        with(sql) {
            append((set.fields map {it.toSQL()}).makeString(", ", "", ""))
            append(" FROM ")
            append(set.source.describe(session))

            if (where != null) {
                append(" WHERE ")
                append(where.toSQL())
            }

            if (groupedByColumns.size > 0) {
                append(" GROUP BY ")
                append((groupedByColumns map {session.fullIdentity(it)}).makeString(", ", "", ""))
            }

            if (having != null) {
                append(" HAVING ")
                append(having!!.toSQL())
            }

            if (orderByColumns.size > 0) {
                append(" ORDER BY ")
                append((orderByColumns map { "${session.fullIdentity(it.first)} ${if(it.second) "ASC" else "DESC"}" }).makeString(", ", "", ""))
            }
        }

        log(sql)
        sql.toString()
    }

    fun groupBy(vararg columns: Column<*>): Query {
        for (column in columns) {
            groupedByColumns.add(column)
        }
        return this
    }

    fun having (op: Op) : Query {
        if (having != null) throw RuntimeException ("HAVING clause is specified twice. Old value = '${having!!.toSQL()}', new value = '${op.toSQL()}'")
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

    private inner class ResultIterator(val rs: ResultSet): Iterator<ResultRow> {
        private var hasNext: Boolean? = null

        public override fun next(): ResultRow {
            if (hasNext == null) hasNext()
            if (hasNext == false) throw NoSuchElementException()
            hasNext = null
            return ResultRow(rs, set.fields)
        }

        public override fun hasNext(): Boolean {
            if (hasNext == null) hasNext = rs.next()
            return hasNext!!
        }
    }

    public override fun iterator(): Iterator<ResultRow> {
        // Flush data before executing query or results may be unpredictable
        EntityCache.getOrCreate(session).flush()

        // Execute query itself
        val rs = session.connection.createStatement()?.executeQuery(statement)!!
        return ResultIterator(rs)
    }
}
