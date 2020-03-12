package org.jetbrains.exposed.sql.statements

import org.jetbrains.exposed.exceptions.throwUnsupportedException
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.statements.api.PreparedStatementApi

open class UpdateStatement(val targetsSet: ColumnSet, val limit: Int?, val where: Op<Boolean>? = null): UpdateBuilder<Int>(StatementType.UPDATE, targetsSet.targetTables()) {

    open val firstDataSet: List<Pair<Column<*>, Any?>> get() = values.toList()

    override fun PreparedStatementApi.executeInternal(transaction: Transaction): Int {
        if (values.isEmpty()) return 0
        return executeUpdate()
    }

    override fun prepareSQL(transaction: Transaction): String {
        return when (targetsSet) {
            is Table -> transaction.db.dialect.functionProvider.update(targetsSet, firstDataSet, limit, where, transaction)
            is Join -> transaction.db.dialect.functionProvider.update(targetsSet, firstDataSet, limit, where, transaction)
            else -> transaction.throwUnsupportedException("UPDATE with ${targetsSet::class.simpleName} unsupported")
        }
    }

    override fun arguments(): Iterable<Iterable<Pair<IColumnType, Any?>>> = QueryBuilder(true).run {
        values.forEach {
            registerArgument(it.key, it.value)
        }
        where?.toQueryBuilder(this)
        if (args.isNotEmpty()) listOf(args) else emptyList()
    }

}
