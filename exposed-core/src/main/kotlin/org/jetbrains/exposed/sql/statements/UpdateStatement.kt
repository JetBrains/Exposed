package org.jetbrains.exposed.sql.statements

import org.jetbrains.exposed.exceptions.throwUnsupportedException
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.statements.api.PreparedStatementApi
import org.jetbrains.exposed.sql.vendors.H2Dialect.H2CompatibilityMode
import org.jetbrains.exposed.sql.vendors.H2FunctionProvider
import org.jetbrains.exposed.sql.vendors.h2Mode

open class UpdateStatement(val targetsSet: ColumnSet, val limit: Int?, val where: Op<Boolean>? = null) :
    UpdateBuilder<Int>(StatementType.UPDATE, targetsSet.targetTables()) {

    open val firstDataSet: List<Pair<Column<*>, Any?>> get() = values.toList()

    override fun PreparedStatementApi.executeInternal(transaction: Transaction): Int {
        if (values.isEmpty()) return 0
        return executeUpdate()
    }

    override fun prepareSQL(transaction: Transaction): String {
        require(firstDataSet.isNotEmpty()) { "Can't prepare UPDATE statement without fields to update" }

        val dialect = transaction.db.dialect
        return when (targetsSet) {
            is Table -> dialect.functionProvider.update(targetsSet, firstDataSet, limit, where, transaction)
            is Join -> {
                val functionProvider = when (dialect.h2Mode) {
                    H2CompatibilityMode.PostgreSQL, H2CompatibilityMode.Oracle, H2CompatibilityMode.SQLServer -> H2FunctionProvider
                    else -> dialect.functionProvider
                }
                functionProvider.update(targetsSet, firstDataSet, limit, where, transaction)
            }
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
