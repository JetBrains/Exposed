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

    override fun prepareSQL(transaction: Transaction): String =
            transaction.db.dialect.functionProvider.update(targetsSet, firstDataSet, limit, where, transaction)

    override fun arguments(): Iterable<Iterable<Pair<IColumnType, Any?>>> = QueryBuilder(true).run {
        values.forEach {
            registerArgument(it.key, it.value)
        }
        where?.toSQL(this)
        if (args.isNotEmpty()) listOf(args) else emptyList()
    }

}
