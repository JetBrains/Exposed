package org.jetbrains.exposed.postgresql.sql

import org.jetbrains.exposed.sql.FieldSet
import org.jetbrains.exposed.sql.Op
import org.jetbrains.exposed.sql.Query
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SqlExpressionBuilder
import org.jetbrains.exposed.sql.Table
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
    val returningDSL = PostgresSqlReturningDSLImpl(this) {
        //insert work fine without propagating returning
    }
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

    checkWhereCalled("update", "updateAll", updateStatement.where)

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
    val returningDSL = PostgresSqlReturningDSLImpl(this) { newReturningValue ->
        updateStatement.returning = newReturningValue
    }
    val updateDsl = PostgresqlUpdateReturningDSL(this, updateStatement, returningDSL)
    body(updateDsl)

    checkWhereCalled("updateReturning", "updateAllReturning", updateStatement.where)

    updateStatement.sqlRendererCallback = PostgresUpdateRenderSQLCallback(returningDSL.sqlRenderer)

    return Iterable { updateStatement.execute(TransactionManager.current())!! }
}

fun <T: Table> T.updateAllReturning(body: PostgresqlUpdateAllReturningDSL<T>.() -> Unit): Iterable<ResultRow> {
    val updateStatement = UpdateReturningStatement(this)
    val returningDSL = PostgresSqlReturningDSLImpl(this) { updateStatement.returning = it }
    val updateDsl = PostgresqlUpdateAllReturningDSL(this, updateStatement, returningDSL)
    body(updateDsl)

    updateStatement.sqlRendererCallback = PostgresUpdateRenderSQLCallback(returningDSL.sqlRenderer)

    return Iterable { updateStatement.execute(TransactionManager.current())!! }
}

fun Table.delete(body: PostgresqlDeleteWhereDSL.() -> Unit): Int {
    val deleteStatement = DeleteStatement(this)
    val deleteDSL = PostgresqlDeleteWhereDSL(deleteStatement)
    deleteDSL.body()

    checkWhereCalled("delete", "deleteAll", deleteStatement.where)

    return deleteStatement.execute(TransactionManager.current())!!
}

fun Table.deleteAll(): Int = DeleteStatement.all(TransactionManager.current(), this)

fun Table.deleteReturning(body: PostgresqlDeleteWhereReturningDSL.() -> Unit): Iterable<ResultRow> {
    val deleteStatement = DeleteReturningStatement(this, this)
    val returningDSL = PostgresSqlReturningDSLImpl(this) { deleteStatement.returning = it }

    val dsl = PostgresqlDeleteWhereReturningDSL(deleteStatement, returningDSL)
    dsl.body()

    checkWhereCalled("deleteReturning", "deleteAllReturning", deleteStatement.where)

    return Iterable { deleteStatement.execute(TransactionManager.current())!! }
}

fun Table.deleteAllReturning(): Iterable<ResultRow> {
    val deleteStatement = DeleteReturningStatement(this, this)

    return Iterable { deleteStatement.execute(TransactionManager.current())!! }
}