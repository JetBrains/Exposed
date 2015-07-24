package kotlin.sql

import java.sql.ResultSet
import java.util.ArrayList
import kotlin.dao.EntityCache

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
            session.flushCache()
            val stmt = session.prepareStatement(sql, autoincs)
            stmt.fillParameters(args)

            val count = stmt.executeUpdate()
            EntityCache.getOrCreate(session).clearReferrersCache()

            if (autoincs?.isNotEmpty() ?: false && generatedKeys != null) {
                generatedKeys(stmt.generatedKeys!!)
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

interface Expression<out T> {
    fun toSQL(queryBuilder: QueryBuilder): String

    companion object {
        inline fun <T> build(builder: SqlExpressionBuilder.()->Expression<T>): Expression<T> {
            return SqlExpressionBuilder.builder()
        }
    }
}

interface ExpressionWithColumnType<T> : Expression<T> {
    // used for operations with literals
    val columnType: ColumnType;
}
