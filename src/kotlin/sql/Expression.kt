package kotlin.sql

import java.util.ArrayList
import java.sql.ResultSet
import java.sql.PreparedStatement
import org.joda.time.DateTime
import kotlin.dao.EntityCache
import java.util.Stack

class QueryBuilder(val prepared: Boolean) {
    val args = ArrayList<Pair<ColumnType, Any?>>()

    fun <T> registerArgument(arg: T, sqlType: ColumnType): String {
        if (prepared) {
            args.add(sqlType to arg)
            return "?"
        }
        else {
            return sqlType.valueToString(arg)
        }
    }

    public fun executeUpdate(session: Session, sql: String, autoincs: List<String>? = null, generatedKeys: ((ResultSet)->Unit)? = null): Int {
        return session.exec(sql, args) {
            val stmt = session.prepareStatement(sql, autoincs)
            stmt.fillParameters(args)

            val count = stmt.executeUpdate()
            EntityCache.getOrCreate(session).clearReferrersCache()

            if (autoincs?.isNotEmpty() ?: false && generatedKeys != null) {
                generatedKeys(stmt.getGeneratedKeys()!!)
            }

            count
        }
    }

    public fun executeQuery(session: Session, sql: String): ResultSet {
        return session.exec(sql, args) {
            val stmt = session.prepareStatement(sql)
            stmt.fillParameters(args)
            stmt.executeQuery()
        }
    }
}

trait Expression<out T> {
    fun toSQL(queryBuilder: QueryBuilder): String

    companion object {
        inline fun <T> build(builder: SqlExpressionBuilder.()->Expression<T>): Expression<T> {
            return SqlExpressionBuilder.builder()
        }
    }
}

trait ExpressionWithColumnType<T> : Expression<T> {
    // used for operations with literals
    val columnType: ColumnType;
}
