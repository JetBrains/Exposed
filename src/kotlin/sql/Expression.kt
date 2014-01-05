package kotlin.sql

import java.util.ArrayList
import java.sql.ResultSet
import java.sql.PreparedStatement
import org.joda.time.DateTime

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
            if (value == null) {
                setObject(index, null)
            }
            else {
                sqlType.setParameter(this, index, value)
            }
            index++
        }
    }

    public fun executeUpdate(session: Session, sql: String, autoincs: List<String>? = null): Int? {
        val stmt = session.prepareStatement(sql, autoincs)
        stmt.fillParameters()

        stmt.executeUpdate()
        if (autoincs?.isNotEmpty() ?: false) {
            val rs = stmt.getGeneratedKeys()!!
            if (rs.next()) {
                return rs.getInt(1)
            }
        }

        return null
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
}

trait ExpressionWithColumnType<out T> : Expression<T> {
    // used for operations with literals
    val columnType: ColumnType;
}
