package org.jetbrains.exposed.sql.statements

import org.jetbrains.exposed.sql.*
import java.sql.PreparedStatement

open class UpdateStatement(val targetsSet: ColumnSet, val limit: Int?, val where: Op<Boolean>? = null): UpdateBuilder<Int>(StatementType.UPDATE, targetsSet.targetTables()) {

    open val firstDataSet: List<Pair<Column<*>, Any?>> get() = values.toList()

    override fun PreparedStatement.executeInternal(transaction: Transaction): Int {
        if (values.isEmpty()) return 0
        transaction.flushCache()
        return executeUpdate().apply {
            transaction.entityCache.removeTablesReferrers(targetsSet.targetTables())
        }
    }

    override fun prepareSQL(transaction: Transaction): String = buildString {
        val builder = QueryBuilder(true)
        append("UPDATE ${targetsSet.describe(transaction)}")
        append(" SET ")
        append(firstDataSet.joinToString {
            val (col, value) = it
            "${transaction.identity(col)}=" + when (value) {
                is Expression<*> -> value.toSQL(builder)
                else -> builder.registerArgument(col.columnType, value)
            }
        })

        where?.let { append(" WHERE " + it.toSQL(builder)) }
        limit?.let { append(" LIMIT $it")}
    }


    override fun arguments(): Iterable<Iterable<Pair<ColumnType, Any?>>> = QueryBuilder(true).run {
        values.forEach {
            val value = it.value
            when (value) {
                is Expression<*> -> value.toSQL(this)
                else -> this.registerArgument(it.key.columnType, value)
            }
        }
        where?.toSQL(this)
        if (args.isNotEmpty()) listOf(args) else emptyList()
    }

}
