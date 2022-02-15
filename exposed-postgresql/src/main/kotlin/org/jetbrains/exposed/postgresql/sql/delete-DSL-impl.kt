package org.jetbrains.exposed.postgresql.sql

import org.jetbrains.exposed.sql.FieldSet
import org.jetbrains.exposed.sql.Op
import org.jetbrains.exposed.sql.ResultIterator
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SqlExpressionBuilder
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.statements.AbstractDeleteStatement
import org.jetbrains.exposed.sql.statements.DeleteStatement
import org.jetbrains.exposed.sql.statements.api.PreparedStatementApi

/**
 * Delete statement with `where { }` - Table.delete
 */
class PostgresqlDeleteWhereDSL(
    private val deleteStatement: DeleteStatement
) {

    fun where(where: SqlExpressionBuilder.() -> Op<Boolean>) {
        deleteStatement.where = SqlExpressionBuilder.where()
    }
}

class PostgresqlDeleteWhereReturningDSL(
    private val deleteStatement: AbstractDeleteStatement<*>,
    private val returningDSL: PostgresSqlReturningDSL
): PostgresSqlReturningDSL by returningDSL {

    fun where(where: SqlExpressionBuilder.() -> Op<Boolean>) {
        deleteStatement.where = SqlExpressionBuilder.where()
    }
}

internal class DeleteReturningStatement(
    table: Table,
    var returning: FieldSet? = null
) : AbstractDeleteStatement<Iterator<ResultRow>>(table, null, false, null, null) {


    override fun PreparedStatementApi.executeInternal(transaction: Transaction): Iterator<ResultRow> {
        val result = executeQuery()
        val returningFieldSet = returning ?: return ResultIterator.empty

        return ResultIterator(result, transaction, returningFieldSet)
    }
}