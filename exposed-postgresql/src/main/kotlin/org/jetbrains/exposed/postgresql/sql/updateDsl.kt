package org.jetbrains.exposed.postgresql.sql

import org.jetbrains.exposed.sql.ColumnSet
import org.jetbrains.exposed.sql.Op
import org.jetbrains.exposed.sql.QueryBuilder
import org.jetbrains.exposed.sql.ResultIterator
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SqlExpressionBuilder
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.statements.AbstractUpdateStatement
import org.jetbrains.exposed.sql.statements.api.PreparedStatementApi

open class PostgresqlUpdateDSL<T: Table, S : AbstractUpdateStatement<*>>(
    protected val columnSet: T,
    internal val updateStatement: S
) {

    fun set(statement: T.(AbstractUpdateStatement<*>) -> Unit) {
        columnSet.statement(updateStatement)
    }
}

open class PostgresqlUpdateWhereDSL<T: Table, S : AbstractUpdateStatement<*>>(
    columnSet: T,
    updateStatement: S
) : PostgresqlUpdateDSL<T, S>(columnSet, updateStatement) {

    fun where(where: SqlExpressionBuilder.() -> Op<Boolean>) {
        updateStatement.where = SqlExpressionBuilder.where()
    }
}

class PostgresqlUpdateReturningDSL<T : Table>(
    columnSet: T,
) : PostgresqlUpdateDSL<T, UpdateReturningStatement>(columnSet, UpdateReturningStatement(columnSet)) {

    fun returning(columnSet: ColumnSet = this.columnSet) {
        updateStatement.returning(columnSet)
    }
}

class PostgresqlUpdateAllReturningDSL<T : Table>(
    columnSet: T,
) : PostgresqlUpdateWhereDSL<T, UpdateReturningStatement>(columnSet, UpdateReturningStatement(columnSet)) {

    fun returning(columnSet: ColumnSet = this.columnSet) {
        updateStatement.returning(columnSet)
    }
}

class UpdateReturningStatement(
    table: Table,
    where: Op<Boolean>? = null,
) : AbstractUpdateStatement<Iterator<ResultRow>>(table, null, where) {

    private var returning: ColumnSet = table

    override fun PreparedStatementApi.executeInternal(transaction: Transaction): Iterator<ResultRow> {
        if (values.isEmpty()) return emptyList<ResultRow>().iterator()

        return ResultIterator(executeQuery(), transaction, targetsSet).iterator()
    }

    override fun prepareSQL(transaction: Transaction): String {
        val sql = super.prepareSQL(transaction)
        return QueryBuilder(prepared = true).apply {
            append(sql)
            returning.realFields.appendTo(prefix = " RETURNING ") {
                it.toQueryBuilder(this)
            }
        }.toString()
    }

    fun returning(columnSet: ColumnSet) {
        returning = columnSet
    }
}
