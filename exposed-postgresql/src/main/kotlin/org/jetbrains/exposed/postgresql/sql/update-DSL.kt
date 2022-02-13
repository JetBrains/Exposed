package org.jetbrains.exposed.postgresql.sql

import org.jetbrains.exposed.sql.FieldSet
import org.jetbrains.exposed.sql.Op
import org.jetbrains.exposed.sql.QueryBuilder
import org.jetbrains.exposed.sql.ResultIterator
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SqlExpressionBuilder
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.render.SQLRenderer
import org.jetbrains.exposed.sql.statements.AbstractUpdateStatement
import org.jetbrains.exposed.sql.statements.api.PreparedStatementApi
import org.jetbrains.exposed.sql.vendors.RenderUpdateSQLCallback

/**
 * Base update DSL for Postgres. Declare only set { } and on conflicts block.
 */
open class PostgresqlUpdateDSL<T: Table>(
    protected val columnSet: T,
    protected val updateStatement: AbstractUpdateStatement<*>
) {

    fun set(statement: T.(AbstractUpdateStatement<*>) -> Unit) {
        columnSet.statement(updateStatement)
    }
}

/**
 * Add where to update - not all update need where clause. updateAll - does not have one.
 */
open class PostgresqlUpdateWhereDSL<T: Table>(
    columnSet: T,
    updateStatement: AbstractUpdateStatement<*>
) : PostgresqlUpdateDSL<T>(columnSet, updateStatement) {

    fun where(where: SqlExpressionBuilder.() -> Op<Boolean>) {
        updateStatement.where = SqlExpressionBuilder.where()
    }
}

/**
 * Update with set { } and returning { }
 */
open class PostgresqlUpdateAllReturningDSL<T : Table>(
    columnSet: T,
    updateStatement: AbstractUpdateStatement<*>,
    private val returningDSL: PostgresSqlReturningDSL
) : PostgresSqlReturningDSL by returningDSL, PostgresqlUpdateDSL<T>(columnSet, updateStatement)

class PostgresqlUpdateReturningDSL<T : Table>(
    columnSet: T,
    updateStatement: AbstractUpdateStatement<*>,
    returningDSL: PostgresSqlReturningDSL
) : PostgresqlUpdateAllReturningDSL<T>(columnSet, updateStatement, returningDSL) {

    fun where(where: SqlExpressionBuilder.() -> Op<Boolean>) {
        updateStatement.where = SqlExpressionBuilder.where()
    }
}

class UpdateReturningStatement(
    table: Table
) : AbstractUpdateStatement<Iterator<ResultRow>>(table, null, null) {

    private var returning: FieldSet? = null

    override fun PreparedStatementApi.executeInternal(transaction: Transaction): Iterator<ResultRow> {
        if (values.isEmpty()) return ResultIterator.empty
        val resultSet = executeQuery()
        val returningSet = returning ?: return ResultIterator.empty

        return ResultIterator(resultSet, transaction, returningSet)
    }

    fun updateReturningSet(returning: FieldSet) {
        this.returning = returning
    }
}

internal class PostgresUpdateRenderSQLCallback(
    private val returningRenderer: SQLRenderer
) : RenderUpdateSQLCallback {
    override fun returning(builder: QueryBuilder) {
        returningRenderer.render(builder)
    }
}

internal fun checkWhereCalled(funName: String, useInstead: String, where: Op<Boolean>?) {
    if (where == null) {
        throw IllegalStateException("""
            Calling $funName without where clause. This exception try to avoid unwanted update of whole table.
            "In case of update all call $useInstead.""".trimIndent()
        )
    }
}