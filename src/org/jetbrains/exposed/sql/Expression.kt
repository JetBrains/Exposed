package org.jetbrains.exposed.sql

import java.sql.ResultSet
import java.util.ArrayList
import org.jetbrains.exposed.dao.EntityCache

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

    fun executeUpdate(transaction: Transaction, sql: String, autoincs: List<String>? = null, generatedKeys: ((ResultSet)->Unit)? = null): Int {
        return transaction.exec(sql, args) {
            transaction.flushCache()
            val stmt = transaction.prepareStatement(sql, autoincs)
            stmt.fillParameters(args)

            val count = stmt.executeUpdate()
            EntityCache.getOrCreate(transaction).clearReferrersCache()

            if (autoincs?.isNotEmpty() ?: false && generatedKeys != null) {
                generatedKeys(stmt.generatedKeys!!)
            }

            count
        }
    }

    fun executeQuery(transaction: Transaction, sql: String): ResultSet {
        return transaction.exec(sql, args) {
            val stmt = transaction.prepareStatement(sql)
            stmt.fillParameters(args)
            stmt.executeQuery()
        }
    }
}

abstract class Expression<out T>() {
    private val _hashCode by lazy {
        toString().hashCode()
    }

    abstract fun toSQL(queryBuilder: QueryBuilder): String

    override fun equals(other: Any?): Boolean {
        return (other as? Expression<*>)?.toString() == toString()
    }

    override fun hashCode(): Int {
        return _hashCode
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
