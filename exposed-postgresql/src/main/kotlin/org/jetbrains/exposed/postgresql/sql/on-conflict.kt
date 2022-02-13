package org.jetbrains.exposed.postgresql.sql

import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.QueryBuilder
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.render.NoopSQLRenderer
import org.jetbrains.exposed.sql.render.SQLRenderer
import org.jetbrains.exposed.sql.statements.UpdateStatement

interface PostgresqlOnConflictDSL<T: Table> {
    /**
     * Specifies which conflicts ON CONFLICT takes the alternative action on by choosing arbiter indexes.
     * Either performs unique index inference, or names a constraint explicitly.
     * For ON CONFLICT DO NOTHING, it is optional to specify a conflict_target; when omitted, conflicts with all usable constraints (and unique indexes) are handled.
     *
     *  https://www.postgresql.org/docs/current/sql-insert.html#SQL-ON-CONFLICT
     */
    fun onConflictDoNothing(vararg conflictTarget: Column<*>)

    /**
     * See [onConflictDoNothing]
     */
    fun onConflictDoNothingConstraint(constraint: String)

    /**
     * Specifies which conflicts ON CONFLICT takes the alternative action on by choosing arbiter indexes.
     * For ON CONFLICT DO UPDATE, a conflict_target must be provided.
     *
     * https://www.postgresql.org/docs/current/sql-insert.html#SQL-ON-CONFLICT
     */
    fun onConflictDoUpdate(conflictTarget: List<Column<*>>, statement: PostgresqlUpdateWhereDSL<T>.() -> Unit)

    /**
     * Specifies which conflicts ON CONFLICT takes the alternative action on by choosing arbiter indexes.
     * For ON CONFLICT DO UPDATE, a conflict_target must be provided.
     *
     * https://www.postgresql.org/docs/current/sql-insert.html#SQL-ON-CONFLICT
     */
    fun onConflictDoUpdate(constraintName: String, statement: PostgresqlUpdateWhereDSL<T>.() -> Unit)
}

class PostgresSQLOnConflictDSLImpl<T: Table>(
    private val table: T,
    private val transaction: Transaction
) : PostgresqlOnConflictDSL<T> {

    private var onConflictSQLRenderer: SQLRenderer = NoopSQLRenderer
    private var onConflictAlreadyCalled = false
    internal val sqlRenderer
        get() = onConflictSQLRenderer

    override fun onConflictDoNothing(vararg conflictTarget: Column<*>) {
        checkOnConflictNotCalled()
        onConflictSQLRenderer = OnConflictDoNothingSQLRenderer(columns = conflictTarget, constraint = null)
    }

    override fun onConflictDoNothingConstraint(constraint: String) {
        checkOnConflictNotCalled()
        onConflictSQLRenderer = OnConflictDoNothingSQLRenderer(columns = null, constraint = constraint)
    }

    override fun onConflictDoUpdate(conflictTarget: List<Column<*>>, statement: PostgresqlUpdateWhereDSL<T>.() -> Unit) {
        checkOnConflictNotCalled()
        val updateStatement = UpdateStatement(table)
        PostgresqlUpdateWhereDSL(table, updateStatement).statement()

        onConflictSQLRenderer = OnConflictUpdateSqlRenderer(
            columns = conflictTarget,
            updateStatement = updateStatement,
            transaction = transaction
        )
    }

    override fun onConflictDoUpdate(constraint: String, statement: PostgresqlUpdateWhereDSL<T>.() -> Unit) {
        checkOnConflictNotCalled()

        val updateStatement = UpdateStatement(table)
        PostgresqlUpdateWhereDSL(table, updateStatement).statement()

        onConflictSQLRenderer = OnConflictUpdateSqlRenderer(
            constraint = constraint,
            updateStatement = updateStatement,
            transaction = transaction
        )
    }

    private fun checkOnConflictNotCalled() {
        if (onConflictAlreadyCalled) {
            throw IllegalStateException("Call ON CONFLICT function only once.")
        }
        onConflictAlreadyCalled = true
    }
}

class OnConflictDoNothingSQLRenderer(
    private val constraint: String? = null,
    columns: Array<out Column<*>>? = null
) : SQLRenderer {
    private val conflictTargetColumnSet = if (columns?.isEmpty() == true) null else columns

    init {
        check(constraint == null || constraint.isNotBlank()) {
            "conflictTarget can't be blank string"
        }
    }

    override fun render(builder: QueryBuilder) {
        builder {
            append(" ON CONFLICT")
            if (constraint != null) {
                append(" ON CONSTRAINT $constraint")
            }
            conflictTargetColumnSet?.appendTo(prefix = " (", postfix = ")") { column ->
                append(column.name)
            }
            append(" DO NOTHING")
        }
    }
}

class OnConflictUpdateSqlRenderer(
    private val constraint: String? = null,
    columns: List<Column<*>>? = null,
    private val updateStatement: UpdateStatement,
    private val transaction: Transaction
) : SQLRenderer {
    private val conflictTargetColumnSet = if (columns?.isEmpty() == true) null else columns

    init {
        check(constraint == null || constraint.isNotBlank()) {
            "conflictTarget can't be blank string"
        }
    }

    override fun render(builder: QueryBuilder) {
        builder {
            append(" ON CONFLICT")
            if (constraint != null) {
                append(" ON CONSTRAINT $constraint")
            }
            conflictTargetColumnSet?.appendTo(prefix = " (", postfix = ")") { column ->
                append(column.name)
            }
            append(" DO ")
            append(updateStatement.prepareSQL(transaction))
        }
    }
}
