package org.jetbrains.exposed.postgresql.sql

import org.jetbrains.exposed.sql.QueryBuilder
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.render.RenderInsertSQLCallbacks
import org.jetbrains.exposed.sql.statements.InsertStatement

/**
 * Postgres insert DSL - introduce only [values] lambda for inserts
 */
open class PostgresqlInsertDSL<T : Table>(
    private val table: T,
    private val insertStatement: InsertStatement<*>,
    private val onConflictDSL: PostgresqlOnConflictDSL<T>
) : PostgresqlOnConflictDSL<T> by onConflictDSL {

    fun values(body: T.(InsertStatement<*>) -> Unit) {
        body(table, insertStatement)
    }
}

/**
 * Postgres insert DSL with [returning] allowing to return inserted values
 */
class PostgresqlInsertReturningDSL<T : Table>(
    table: T,
    insertStatement: InsertStatement<*>,
    private val returningImpl: PostgresSqlReturningDSL,
    onConflictDSL: PostgresqlOnConflictDSL<T>
) : PostgresSqlReturningDSL by returningImpl, PostgresqlInsertDSL<T>(table, insertStatement, onConflictDSL)


internal class PostgresSqlPrepareInsertSQLCallbacks(
    private val onConflictRenderer: SQLRenderer,
    private val returningRender: SQLRenderer
) : RenderInsertSQLCallbacks {

    override fun onConflict(builder: QueryBuilder) {
        onConflictRenderer.render(builder)
    }

    override fun returning(builder: QueryBuilder) {
        returningRender.render(builder)
    }
}