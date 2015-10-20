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

abstract class Expression<out T>() {
    abstract fun toSQL(queryBuilder: QueryBuilder): String

    override fun equals(other: Any?): Boolean {
        return (other as? Expression<*>)?.toString() == toString()
    }

    override fun hashCode(): Int {
        return toString().hashCode()
    }

    override fun toString(): String {
        return toSQL(QueryBuilder(false))
    }

    companion object {
        inline fun <T> build(builder: SqlExpressionBuilder.()->Expression<T>): Expression<T> {
            return SqlExpressionBuilder.builder()
        }
    }
}

abstract class ExpressionWithColumnType<T> : Expression<T>() {
    // used for operations with literals
    abstract val columnType: ColumnType;
}
