package org.jetbrains.exposed.sql.statements

import org.jetbrains.exposed.exceptions.throwUnsupportedException
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.ColumnSet
import org.jetbrains.exposed.sql.IColumnType
import org.jetbrains.exposed.sql.Join
import org.jetbrains.exposed.sql.Op
import org.jetbrains.exposed.sql.QueryBuilder
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.statements.api.PreparedStatementApi
import org.jetbrains.exposed.sql.targetTables

abstract class AbstractUpdateStatement<R>(
    val targetsSet: ColumnSet,
    val limit: Int?,
    var where: Op<Boolean>?
) : UpdateBuilder<R>(StatementType.UPDATE, targetsSet.targetTables()) {

    open val firstDataSet: List<Pair<Column<*>, Any?>> get() = values.toList()

    override fun prepareSQL(transaction: Transaction): String {
        require(firstDataSet.isNotEmpty()) { "Can't prepare UPDATE statement without fields to update" }

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

open class UpdateStatement(
    targetsSet: ColumnSet,
    limit: Int? = null,
    where: Op<Boolean>? = null
) : AbstractUpdateStatement<Int>(targetsSet, limit, where) {

    override fun PreparedStatementApi.executeInternal(transaction: Transaction): Int {
        if (values.isEmpty()) return 0
        return executeUpdate()
    }
}
