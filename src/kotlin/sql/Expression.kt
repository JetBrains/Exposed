package kotlin.sql

import java.util.ArrayList
import java.sql.ResultSet
import java.sql.PreparedStatement
import org.joda.time.DateTime
import kotlin.dao.EntityCache

class QueryBuilder(val prepared: Boolean) {
    val args = ArrayList<Pair<ColumnType, Any?>>()

    fun <T> registerArgument(arg: T, sqlType: ColumnType): String {
        if (prepared && isSupported(sqlType)) {
            args.add(sqlType to arg)
            return "?"
        }
        else {
            return sqlType.valueToString(arg)
        }
    }

    private fun PreparedStatement.fillParameters() {
        clearParameters()
        var index = 1
        for ((sqlType, value) in args) {
            when (value) {
                null -> setObject(index, null)
                is List<*> -> {
                    val array = getConnection()!!.createArrayOf("INT", value.map {sqlType.valueToString(it)}.copyToArray())
                    setArray(index, array)
                }
                else -> sqlType.setParameter(this, index, value)
            }

            index++
        }
    }

    public fun executeUpdate(session: Session, sql: String, autoincs: List<String>? = null, generatedKeys: ((ResultSet)->Unit)? = null): Int {
        val stmt = session.prepareStatement(sql, autoincs)
        stmt.fillParameters()

        val count = stmt.executeUpdate()
        EntityCache.getOrCreate(session).clearReferrersCache()

        if (autoincs?.isNotEmpty() ?: false && generatedKeys != null) {
            generatedKeys(stmt.getGeneratedKeys()!!)
        }


        return count
    }

    public fun executeQuery(session: Session, sql: String): ResultSet {
        val stmt = session.prepareStatement(sql)
        stmt.fillParameters()
        return stmt.executeQuery()
    }

    public class object {
        fun isSupported(sqlType: ColumnType): Boolean {
            return sqlType is StringColumnType ||
            sqlType is IntegerColumnType ||
            sqlType is LongColumnType ||
            sqlType is BlobColumnType ||
            sqlType is DateColumnType ||
            sqlType is DecimalColumnType
        }
    }
}

trait Expression<out T> {
    fun toSQL(queryBuilder: QueryBuilder): String

    class object {
        inline fun <T> build(builder: SqlExpressionBuilder.()->Expression<T>): Expression<T> {
            return SqlExpressionBuilder.builder()
        }
    }
}

trait ExpressionWithColumnType<out T> : Expression<T> {
    // used for operations with literals
    val columnType: ColumnType;
}
