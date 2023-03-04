package org.jetbrains.exposed.sql.statements

import org.jetbrains.exposed.exceptions.throwUnsupportedException
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.statements.api.PreparedStatementApi
import org.jetbrains.exposed.sql.vendors.H2Dialect.H2CompatibilityMode
import org.jetbrains.exposed.sql.vendors.H2FunctionProvider
import org.jetbrains.exposed.sql.vendors.h2Mode
import java.sql.ResultSet

// Need to subclasss either UpdateStatement or prob UpdateBuilder and inherit so we can change the executeInternal to return rows.
open class UpdateReturningStatement(val targetsSet: ColumnSet, val limit: Int?, val where: Op<Boolean>? = null, val returning: FieldSet) :
    UpdateBuilder<Iterator<ResultRow>>(StatementType.UPDATE, targetsSet.targetTables()) {

    open val firstDataSet: List<Pair<Column<*>, Any?>> get() = values.toList()

    override fun PreparedStatementApi.executeInternal(transaction: Transaction): Iterator<ResultRow> {
        return ResultIterator(executeUpdateReturning())
    }

    private inner class ResultIterator(val rs: ResultSet) : Iterator<ResultRow> {
        private var hasNext = false
            set(value) {
                field = value
                if (!field) {
                    rs.statement?.close()
                }
            }

        private val fieldsIndex = returning.realFields.toSet().mapIndexed { index, expression -> expression to index }.toMap()

        init {
            hasNext = rs.next()
        }

        override operator fun next(): ResultRow {
            if (!hasNext) throw NoSuchElementException()
            val result = ResultRow.create(rs, fieldsIndex)
            hasNext = rs.next()
            return result
        }

        override fun hasNext(): Boolean = hasNext
    }

    override fun prepareSQL(transaction: Transaction): String {
        require(firstDataSet.isNotEmpty()) { "Can't prepare UPDATE statement without fields to update" }

        val dialect = transaction.db.dialect
        return when (targetsSet) {
            is Table -> dialect.functionProvider.update(targetsSet, firstDataSet, limit, where, returning, transaction)
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

        //add here.
    }
}
