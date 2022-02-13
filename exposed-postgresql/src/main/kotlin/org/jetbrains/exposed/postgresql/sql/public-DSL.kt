package org.jetbrains.exposed.postgresql.sql

import org.jetbrains.exposed.sql.FieldSet
import org.jetbrains.exposed.sql.Op
import org.jetbrains.exposed.sql.Query
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SqlExpressionBuilder
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.render.NoopSQLRenderer
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.statements.DeleteStatement
import org.jetbrains.exposed.sql.statements.InsertStatement
import org.jetbrains.exposed.sql.statements.UpdateStatement
import org.jetbrains.exposed.sql.transactions.TransactionManager

/**
 * Insert for Postgresql dialect with `ON CONFLICT` and `RETURNING` statements.
 */
fun <T : Table> T.insert(body: PostgresqlInsertDSL<T>.(T) -> Unit): Int {
    val insertStatement = InsertStatement<Number>(this)

    val transaction = TransactionManager.current()
    val onConflictDSL = PostgresSQLOnConflictDSLImpl<T>(this, transaction)
    val dsl = PostgresqlInsertDSL(this, insertStatement, onConflictDSL)
    body(dsl, this)

    insertStatement.renderSqlCallback = PostgresSqlPrepareInsertSQLCallbacks(
        onConflictRenderer = onConflictDSL.sqlRenderer,
        returningRender = NoopSQLRenderer
    )

    return insertStatement.execute(transaction)!!
}

fun <T : Table> T.insertReturning(body: PostgresqlInsertReturningDSL<T>.(T) -> Unit): ResultRow {
    val insertStatement = InsertStatement<List<ResultRow>>(this)
    val transaction = TransactionManager.current()
    val onConflictDSL = PostgresSQLOnConflictDSLImpl(this, transaction)
    val returningDSL = PostgresSqlReturningDSLImpl(this)
    val dsl = PostgresqlInsertReturningDSL(this, insertStatement, returningDSL, onConflictDSL)
    body(dsl, this)

    insertStatement.renderSqlCallback = PostgresSqlPrepareInsertSQLCallbacks(
        onConflictRenderer = onConflictDSL.sqlRenderer,
        returningRender = returningDSL.sqlRenderer
    )

    insertStatement.execute(TransactionManager.current())!!

    return insertStatement.resultedValues!!.single()
}


fun FieldSet.select(where: SqlExpressionBuilder.() -> Op<Boolean>): Query = select(where)
fun FieldSet.selectAll(): Query = selectAll()

fun <T : Table> T.update(body: PostgresqlUpdateWhereDSL<T>.() -> Unit): Int {
    val updateStatement = UpdateStatement(this, where = null)
    val updateDsl = PostgresqlUpdateWhereDSL(this, updateStatement)
    body(updateDsl)

    if (updateStatement.where == null) {
        throw IllegalStateException("""
            Calling update without where clause. This exception try to avoid unwanted update of whole table.
            "In case of update all call updateAll.""".trimIndent()
        )
    }

    return updateStatement.execute(TransactionManager.current())!!
}

fun <T : Table> T.updateAll(body: PostgresqlUpdateDSL<T>.() -> Unit): Int {
    val updateStatement = UpdateStatement(this, where = null)
    val updateDsl = PostgresqlUpdateDSL(this, updateStatement)
    body(updateDsl)

    return updateStatement.execute(TransactionManager.current())!!
}

fun <T: Table> T.updateReturning(body: PostgresqlUpdateReturningDSL<T>.() -> Unit): Iterable<ResultRow> {
    val updateStatement = UpdateReturningStatement(this)
    val returningDSL = PostgresSqlReturningDSLImpl(this, updateStatement::updateReturningSet)
    val updateDsl = PostgresqlUpdateReturningDSL(this, updateStatement, returningDSL)
    body(updateDsl)


    checkWhereCalled("updateReturning", "updateAllReturning", updateStatement.where)

    updateStatement.sqlRendererCallback = PostgresUpdateRenderSQLCallback(returningDSL.sqlRenderer)

    return Iterable { updateStatement.execute(TransactionManager.current())!! }
}

fun <T: Table> T.updateAllReturning(body: PostgresqlUpdateAllReturningDSL<T>.() -> Unit): Iterable<ResultRow> {
    val updateStatement = UpdateReturningStatement(this)
    val returningDSL = PostgresSqlReturningDSLImpl(this, updateStatement::updateReturningSet)
    val updateDsl = PostgresqlUpdateAllReturningDSL(this, updateStatement, returningDSL)
    body(updateDsl)

    updateStatement.sqlRendererCallback = PostgresUpdateRenderSQLCallback(returningDSL.sqlRenderer)

    return Iterable { updateStatement.execute(TransactionManager.current())!! }
}

fun Table.delete(ignoreErrors: Boolean = false, body: SqlExpressionBuilder.() -> Op<Boolean>): Int {
    return DeleteStatement.where(TransactionManager.current(), this, SqlExpressionBuilder.body(), ignoreErrors)
}

//fun Table.deleteReturning(ignoreErrors: Boolean = false, body: PostgresqlDeleteReturningDSL.() -> Unit): Iterator<ResultRow> {
//    val dsl = PostgresqlDeleteReturningDSL(this)
//    dsl.body()
//
//    val where = dsl.where ?: throw IllegalStateException("Where function has to be called or use deleteAll()")
//    val deleteStatement = DeleteStatement(this, where, ignoreErrors)
//
//    val exec = deleteStatement.execute(TransactionManager.current())
//
//    return deleteStatement
//}

fun Table.deleteAll() = DeleteStatement.all(TransactionManager.current(), this)
//fun Table.deleteAllReturning(): Iterator<ResultRow> = DeleteStatement.all(TransactionManager.current(), this)